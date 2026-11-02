package app.lumisite.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.lumisite.android.data.DiagLog
import app.lumisite.android.data.Repository
import app.lumisite.core.export.ResumableExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * D14: session export as unique WorkManager work (one per session), foreground of type dataSync with a percentage
 * notification. Interruptible: a stop (system, user, process death) leaves the resumable manifest consistent and the
 * next run continues after the last verified entry. No continuous execution is promised: the system may defer it.
 */
class ExportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private fun info(pct: Int, text: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Export des sessions", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Export LumiSite").setContentText("$text · $pct %")
            .setProgress(100, pct, false).setOngoing(true).setOnlyAlertOnce(true).build()
        val id = NOTIF_BASE + (inputData.getString(KEY_SESSION)?.hashCode() ?: 0 and 0xffff)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(0, "Préparation…")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION) ?: return@withContext Result.failure()
        val repo = Repository(applicationContext)
        val site = inputData.getString(KEY_SITE)?.let(repo::site) ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Site introuvable"))
        live[sessionId] = 0
        runCatching { setForeground(info(0, "Préparation…")) }.onFailure { DiagLog.i("EXPORT", "Premier plan refusé (l'export continue tant que l'appli est active) : $it") }
        var last = 0L
        try {
            val r = repo.export(site, sessionId) { p ->
                if (isStopped) throw CancellationException("export interrompu")
                val pct = (p.fraction * 100).toInt()
                live[sessionId] = pct
                val now = SystemClock.elapsedRealtime()
                if (now - last > 400 || p.step == ResumableExport.Step.TERMINE) {
                    last = now
                    setProgressAsync(workDataOf(KEY_STEP to p.step.label, KEY_DETAIL to p.detail, KEY_PCT to pct))
                    runCatching { setForegroundAsync(info(pct, p.step.label)) }
                }
            }
            DiagLog.i("EXPORT", "Export ${r.zip.name} terminé (${r.zip.length()} octets), copie : ${r.publicPath ?: "non faite"}")
            Result.success(workDataOf(KEY_ZIP to r.zip.path, KEY_PUBLIC to r.publicPath, KEY_URI to r.publicUri?.toString(),
                KEY_PROBLEMS to r.problems.joinToString("\n"), KEY_FILES to r.manifest.files.size))
        } catch (e: CancellationException) {
            DiagLog.i("EXPORT", "Export $sessionId interrompu ; il reprendra au prochain lancement")
            throw e
        } catch (e: Exception) {
            DiagLog.e("EXPORT", "Export $sessionId", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.toString())))
        } finally { live.remove(sessionId) }
    }

    companion object {
        const val CHANNEL = "export"
        private const val NOTIF_BASE = 4200
        const val KEY_SESSION = "session"; const val KEY_SITE = "site"; const val KEY_STEP = "step"; const val KEY_DETAIL = "detail"
        const val KEY_PCT = "pct"; const val KEY_ZIP = "zip"; const val KEY_PUBLIC = "public"; const val KEY_URI = "uri"
        const val KEY_PROBLEMS = "problems"; const val KEY_FILES = "files"; const val KEY_ERROR = "error"

        /** Percentage of the exports running in this process (cards of Accueil / Site, Terminer message). */
        val live = ConcurrentHashMap<String, Int>()

        fun workName(sessionId: String) = "export-$sessionId"

        /** A second press while an export runs keeps the running one (KEEP): no duplicate export. */
        fun enqueue(ctx: Context, siteId: String, sessionId: String) {
            live.putIfAbsent(sessionId, 0)
            val req = OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(workDataOf(KEY_SESSION to sessionId, KEY_SITE to siteId))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("export").build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(workName(sessionId), ExistingWorkPolicy.KEEP, req)
        }

        fun latest(ctx: Context, sessionId: String): WorkInfo? =
            runCatching { WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(workName(sessionId)).get() }.getOrNull()?.lastOrNull()
    }
}
