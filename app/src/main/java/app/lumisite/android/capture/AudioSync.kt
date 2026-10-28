package app.lumisite.android.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import app.lumisite.core.model.AudioDetection
import app.lumisite.core.sync.Chirp
import app.lumisite.core.sync.ChirpDetector
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * EXPERIMENTAL acoustic sync marker. Every device (emitter included) records a window of microphone audio
 * and detects the chirp by cross-correlation. The detected frame index is mapped to the monotonic clock via
 * AudioRecord.getTimestamp(TIMEBASE_BOOTTIME) when available. Propagation delay (~2.9 ms/m) and
 * device-specific input latency are not compensated. Not validated on real hardware yet.
 */
object AudioSync {
    private const val RATE = Chirp.DEFAULT_SAMPLE_RATE

    data class Result(val eventMonotonicNs: Long, val detection: AudioDetection, val trusted: Boolean, val message: String)

    @SuppressLint("MissingPermission")
    fun listen(windowMs: Int, emitAfterMs: Int?, recordingFile: File?): Result {
        val n = RATE * windowMs / 1000
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.UNPROCESSED.takeIf { Build.VERSION.SDK_INT >= 24 } ?: MediaRecorder.AudioSource.MIC,
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE))
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialised (microphone unavailable?)" }
        val pcm = ShortArray(n)
        val template = Chirp.generate()
        var emitWriteNs: Long? = null
        var tsFrame: Long? = null; var tsNs: Long? = null; var tsBase: String? = null
        rec.startRecording()
        val startMono = SystemClock.elapsedRealtimeNanos()
        var emitter: Thread? = null
        if (emitAfterMs != null) {
            emitter = Thread {
                Thread.sleep(emitAfterMs.toLong())
                emitWriteNs = play(template)
            }.apply { start() }
        }
        var read = 0
        while (read < n) {
            val r = rec.read(pcm, read, minOf(4096, n - read))
            if (r <= 0) break
            read += r
            if (tsFrame == null && Build.VERSION.SDK_INT >= 24) {
                val ts = AudioTimestamp()
                if (rec.getTimestamp(ts, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS) {
                    tsFrame = ts.framePosition; tsNs = ts.nanoTime; tsBase = "TIMEBASE_BOOTTIME"
                }
            }
        }
        rec.stop(); rec.release()
        emitter?.join()
        val signal = FloatArray(read) { pcm[it] / 32768f }
        recordingFile?.let { writeWav(it, pcm, read) }
        val d = ChirpDetector.detect(signal, template, step = 1) ?: error("Recording too short")
        // frame -> monotonic: with timestamp pair if available, else from record start (less accurate).
        val eventNs = if (tsFrame != null && tsNs != null) tsNs!! + (d.index - tsFrame!!) * 1_000_000_000L / RATE
        else startMono + d.index * 1_000_000_000L / RATE
        val trusted = d.snr >= 8.0 && d.score >= 0.3
        val det = AudioDetection(
            RATE, Chirp.DEFAULT_F0, Chirp.DEFAULT_F1, Chirp.DEFAULT_DURATION_MS, d.index.toLong(), d.score, d.noiseFloor,
            tsFrame, tsNs, tsBase ?: "NONE (fallback: startRecording time)", emitWriteNs, recordingFile?.name,
        )
        val msg = "score=%.2f, pic/médiane=%.1f%s".format(d.score, d.snr, if (trusted) "" else " — détection peu fiable")
        return Result(eventNs, det, trusted, msg)
    }

    /** Plays the chirp; returns monotonic ns when write() returned (not the acoustic emission instant). */
    private fun play(template: FloatArray): Long {
        val pcm = ShortArray(template.size) { (template[it] * 0.9f * Short.MAX_VALUE).toInt().toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(pcm, 0, pcm.size)
        val t = SystemClock.elapsedRealtimeNanos()
        track.play()
        Thread.sleep((Chirp.DEFAULT_DURATION_MS + 200).toLong())
        track.release()
        return t
    }

    private fun writeWav(f: File, pcm: ShortArray, n: Int) {
        val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN).apply { for (i in 0 until n) putShort(pcm[i]) }.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        f.outputStream().use { it.write(h); it.write(data) }
    }
}
