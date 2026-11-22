package app.lumisite.core.sync

import app.lumisite.core.journal.LumiJson
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream

/**
 * Durable on-disk archive of a synchro capture (master or slave).
 * Layout, reused from files/synchro/<horodatage>/ :
 *   synchro_header.json — site, visit, master name, clock offset
 *   synchro.ndjson      — existing journal lines (t_mono_ns, utc_ms, appareil, quoi)
 *   capteurs.csv        — existing columns t_mono_ns,capteur,x,y,z (GPS, DEPTH, MESURE, IMU, …)
 *
 * Writes are flushed; [flush] also syncs to stable storage so a sleep or process kill
 * does not drop already-accepted samples. [open] resumes the same folder.
 */
@Serializable
data class SynchroHeader(
    val format: String = FORMAT,
    val role: String,
    val startedUtcMs: Long,
    val deviceName: String,
    val deviceId: String,
    val masterName: String? = null,
    val siteId: String? = null,
    val visitId: String? = null,
    val clockOffsetNs: Long? = null,
    val clockUncertaintyNs: Long? = null,
    val timeBaseId: String? = null,
) {
    companion object {
        const val FORMAT = "lumisite.synchro/1"
        const val ROLE_ESCLAVE = "esclave"
        const val ROLE_MAITRE = "maitre"
    }
}

class SynchroArchive private constructor(val dir: File, private var _header: SynchroHeader) : AutoCloseable {
    val header: SynchroHeader get() = _header

    private val journalFile = File(dir, JOURNAL)
    private val sensorsFile = File(dir, SENSORS)
    private val journalOut = FileOutputStream(journalFile, true)
    private val sensorsOut = FileOutputStream(sensorsFile, true)
    private val lock = Any()
    private var sensorWrites = 0

    companion object {
        const val HEADER = "synchro_header.json"
        const val JOURNAL = "synchro.ndjson"
        const val SENSORS = "capteurs.csv"
        const val SENSORS_COLUMNS = "t_mono_ns,capteur,x,y,z"
        const val STOPPED = "synchro arrêtée"
        const val DEFAULT_RESUME_MAX_AGE_MS = 12L * 60 * 60 * 1000

        /** Creates [dir] and writes the header plus an empty journal / sensors file. */
        fun create(dir: File, header: SynchroHeader): SynchroArchive {
            dir.mkdirs()
            writeHeader(dir, header)
            val sensors = File(dir, SENSORS)
            if (!sensors.exists() || sensors.length() == 0L) {
                FileOutputStream(sensors, false).use { it.write((SENSORS_COLUMNS + "\n").toByteArray()); it.fd.sync() }
            }
            File(dir, JOURNAL).apply { if (!exists()) createNewFile() }
            return SynchroArchive(dir, header)
        }

        /** Reopens an existing archive (after sleep, return, or process restart). Null if no header. */
        fun open(dir: File): SynchroArchive? {
            val header = readHeader(dir) ?: return null
            val sensors = File(dir, SENSORS)
            if (!sensors.exists() || sensors.length() == 0L) {
                FileOutputStream(sensors, false).use { it.write((SENSORS_COLUMNS + "\n").toByteArray()); it.fd.sync() }
            }
            File(dir, JOURNAL).apply { if (!exists()) createNewFile() }
            return SynchroArchive(dir, header)
        }

        fun readHeader(dir: File): SynchroHeader? {
            val f = File(dir, HEADER)
            if (!f.isFile) return null
            return runCatching { LumiJson.decodeFromString(SynchroHeader.serializer(), f.readText()) }.getOrNull()
        }

        /**
         * Synchro folders under [root] that still have a header, are not marked stopped,
         * and are younger than [maxAgeMs] — candidates to resume after a crash or kill.
         */
        fun resumableDirs(root: File, nowUtcMs: Long, maxAgeMs: Long = DEFAULT_RESUME_MAX_AGE_MS): List<File> {
            if (!root.isDirectory) return emptyList()
            return root.listFiles().orEmpty().filter { it.isDirectory }.filter { d ->
                val h = readHeader(d) ?: return@filter false
                if (nowUtcMs - h.startedUtcMs > maxAgeMs) return@filter false
                !isStopped(d)
            }.sortedByDescending { readHeader(it)?.startedUtcMs ?: 0L }
        }

        fun isStopped(dir: File): Boolean {
            val f = File(dir, JOURNAL)
            if (!f.isFile) return false
            return f.useLines { lines -> lines.any { it.contains("\"quoi\":\"$STOPPED\"") || it.contains("\"quoi\": \"$STOPPED\"") } }
        }

        internal fun writeHeader(dir: File, header: SynchroHeader) {
            dir.mkdirs()
            val target = File(dir, HEADER)
            val tmp = File(dir, "$HEADER.tmp")
            FileOutputStream(tmp).use { out ->
                out.write(LumiJson.encodeToString(SynchroHeader.serializer(), header).toByteArray())
                out.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "Impossible d'écrire ${target.path}" }
            }
        }

        private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        internal fun journalLine(appareil: String, quoi: String, tMonoNs: Long, utcMs: Long): String =
            "{\"t_mono_ns\":$tMonoNs,\"utc_ms\":$utcMs,\"appareil\":${q(appareil)},\"quoi\":${q(quoi)}}\n"
    }

    fun bind(siteId: String, visitId: String) = update { it.copy(siteId = siteId, visitId = visitId) }

    fun setMasterName(name: String) = update { it.copy(masterName = name) }

    fun setClockOffset(offsetNs: Long, uncertaintyNs: Long, timeBaseId: String? = null) = update {
        it.copy(clockOffsetNs = offsetNs, clockUncertaintyNs = uncertaintyNs, timeBaseId = timeBaseId ?: it.timeBaseId)
    }

    fun appendJournal(appareil: String, quoi: String, tMonoNs: Long, utcMs: Long) = synchronized(lock) {
        val bytes = journalLine(appareil, quoi, tMonoNs, utcMs).toByteArray()
        journalOut.write(bytes)
        journalOut.flush()
    }

    fun appendSensor(tMonoNs: Long, capteur: String, x: Double, y: Double = 0.0, z: Double = 0.0) = synchronized(lock) {
        val kind = capteur.replace(Regex("[,\\n\\r]"), "_")
        sensorsOut.write("$tMonoNs,$kind,$x,$y,$z\n".toByteArray())
        sensorWrites++
        // IMU arrives at SENSOR_DELAY_GAME: flush often, but fsync only on [flush] or every 25 samples.
        if (kind in setOf("GPS", "DEPTH", "MESURE") || sensorWrites % 25 == 0) sensorsOut.flush()
    }

    fun markStopped() {
        val h = header
        appendJournal("local", STOPPED, h.startedUtcMs, h.startedUtcMs)
        flush()
    }

    fun isStopped(): Boolean = companionIsStopped()
    private fun companionIsStopped() = Companion.isStopped(dir)

    fun journalLines(): List<String> = synchronized(lock) {
        journalOut.flush()
        journalFile.takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() }
    }

    fun sensorLines(): List<String> = synchronized(lock) {
        sensorsOut.flush()
        sensorsFile.takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() }
    }

    /** Flush and fsync so a subsequent open() or a process kill still sees the writes. */
    fun flush() = synchronized(lock) {
        journalOut.flush(); journalOut.fd.sync()
        sensorsOut.flush(); sensorsOut.fd.sync()
    }

    override fun close() {
        synchronized(lock) {
            runCatching { flush() }
            runCatching { journalOut.close() }
            runCatching { sensorsOut.close() }
        }
    }

    private fun update(fn: (SynchroHeader) -> SynchroHeader) = synchronized(lock) {
        _header = fn(_header)
        writeHeader(dir, _header)
    }
}

