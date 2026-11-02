package app.lumisite.android.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import app.lumisite.android.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Stage 1.5: rotating text diagnostic journal. Each line: local time | active « Écran > Menu > Sous-menu » | category | message.
 * Written in the app files (diag/journal.txt, 512 KiB × 4 files), and copied to Documents/LumiSite/_journal/: each
 * rotated file once, the current file on [mirrorNow] (pause, share, end of session).
 */
object DiagLog {
    private const val MAX_BYTES = 512L * 1024
    private const val KEEP = 4
    private lateinit var app: Context
    private lateinit var dir: File
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "diaglog").apply { isDaemon = true } }
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT)
    @Volatile var screen: String = "Accueil"; private set
    val current: File get() = File(dir, "journal.txt")

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        dir = File(app.filesDir, "diag").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write("CRASH", "Thread ${t.name} : ${e.stackTraceToString().take(4_000)}") }
            previous?.uncaughtException(t, e)
        }
        i("APP", "Démarrage LumiSite ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE}), ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), boot_id ${BootInfo.bootId()}")
        memory("démarrage")
    }

    /** Records the screen now shown, with its real labels (« Écran > Menu > Sous-menu »). */
    fun screen(label: String) { if (label != screen) { screen = label; write("ÉCRAN", label) } }

    fun i(category: String, message: String) = write(category, message)
    fun e(category: String, message: String, t: Throwable? = null) =
        write("$category ERREUR", message + (t?.let { " : ${it::class.java.simpleName} ${it.message}" } ?: ""))

    fun memory(why: String) {
        if (!::app.isInitialized) return
        val rt = Runtime.getRuntime()
        val mi = ActivityManager.MemoryInfo().also { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val free = app.filesDir.usableSpace
        write("MÉMOIRE", "$why : tas Java ${(rt.totalMemory() - rt.freeMemory()) shr 20}/${rt.maxMemory() shr 20} Mo, système dispo ${mi.availMem shr 20} Mo" +
            (if (mi.lowMemory) " (mémoire basse)" else "") + ", stockage libre ${free shr 20} Mo")
    }

    @Synchronized
    private fun write(category: String, message: String) {
        if (!::app.isInitialized) return
        val line = "${fmt.format(Date())} | $screen | $category | ${message.replace('\n', ' ')}\n"
        runCatching {
            current.appendText(line)
            if (current.length() > MAX_BYTES) rotate()
        }
    }

    private fun rotate() {
        File(dir, "journal.$KEEP.txt").delete()
        for (k in KEEP - 1 downTo 1) File(dir, "journal.$k.txt").takeIf { it.exists() }?.renameTo(File(dir, "journal.${k + 1}.txt"))
        val rotated = File(dir, "journal.1.txt")
        current.renameTo(rotated)
        val name = "journal-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.txt"
        val copy = File(dir, name).also { rotated.copyTo(it, overwrite = true) }
        exec.execute { runCatching { PublicStore(app).copy(copy, listOf("_journal"), name) }; copy.delete() }
    }

    /** Copies the current file to Documents/LumiSite/_journal/journal-courant.txt (background). */
    fun mirrorNow() {
        if (!::app.isInitialized) return
        // One temporary file per snapshot: a later snapshot never overwrites one still queued.
        val snap = File(dir, "journal-courant-${System.nanoTime()}.tmp")
        synchronized(this) { if (current.exists()) current.copyTo(snap, overwrite = true) else return }
        exec.execute { runCatching { PublicStore(app).copy(snap, listOf("_journal"), "journal-courant.txt") }; snap.delete() }
    }

    /** One file with the kept history (oldest first) for « Réglages > Journal > Partager le journal ». */
    @Synchronized
    fun bundle(outDir: File): File {
        val out = File(outDir.apply { mkdirs() }, "lumisite-journal-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.txt")
        out.bufferedWriter().use { w ->
            for (k in KEEP downTo 1) File(dir, "journal.$k.txt").takeIf { it.exists() }?.let { f -> f.bufferedReader().use { it.copyTo(w) } }
            if (current.exists()) current.bufferedReader().use { it.copyTo(w) }
        }
        return out
    }

    val publicDir = PublicStore.displayPath(listOf("_journal"))
}
