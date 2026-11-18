package app.lumisite.android.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.lumisite.android.MainActivity
import app.lumisite.android.data.DiagLog
import app.lumisite.core.sync.Link
import app.lumisite.core.sync.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Build 199, point 1f : service de premier plan qui garde la synchro Nearby (objet NearbyMesh, même processus)
 * vivante écran éteint — HyperOS/Xiaomi coupe sinon l'appli sans service. Types connectedDevice (radio Nearby)
 * + microphone (enregistrement esclave), permissions FOREGROUND_SERVICE_CONNECTED_DEVICE / _MICROPHONE.
 * Notification permanente « Synchro LumiSite active · N esclave(s) » avec action « Arrêter » (ACTION_STOP →
 * NearbyMesh.stop, le même arrêt que les boutons de l'écran). Suivi de NearbyMesh.state : le texte de la
 * notification suit le nombre d'esclaves connectés / le nom du maître. START_NOT_STICKY : si le processus meurt,
 * la synchro Nearby est de toute façon perdue (objet en mémoire), inutile de la recréer à moitié.
 */
class SynchroService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collect: Job? = null
    private var lastNotice: SyncService.Notice? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            NearbyMesh.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        goForeground()
        observe()
        return START_NOT_STICKY
    }

    /** startForeground dans le délai imposé (5 s) : l'état courant suffit, le collecteur affine ensuite. */
    private fun goForeground() {
        val types = SyncService.foregroundTypes(
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val mask = types.fold(0) { acc, t -> acc or when (t) {
            SyncService.ForegroundType.CONNECTED_DEVICE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            SyncService.ForegroundType.MICROPHONE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } }
        val st = NearbyMesh.state.value
        val n = st.role?.let { SyncService.notice(it, st.peers.count { p -> p.link == Link.CONNECTED }, st.slaveMasterName) }
            ?: SyncService.Notice("Synchro LumiSite active", "Démarrage…")
        lastNotice = n
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) ServiceCompat.startForeground(this, NOTIF_ID, build(n), mask)
            else @Suppress("DEPRECATION") startForeground(NOTIF_ID, build(n))
        }.onFailure { DiagLog.e("SYNCHRO", "service de premier plan refusé (la synchro continue tant que l'appli vit)", it) }
    }

    /** Republie la notification quand son texte change (connexion d'un esclave, perte du maître…). */
    private fun observe() {
        if (collect != null) return
        collect = scope.launch {
            NearbyMesh.state.collect { st ->
                val role = st.role ?: return@collect
                val n = SyncService.notice(role, st.peers.count { it.link == Link.CONNECTED }, st.slaveMasterName)
                if (n != lastNotice) {
                    lastNotice = n
                    runCatching { notify(n) }.onFailure { DiagLog.e("SYNCHRO", "notification non mise à jour", it) }
                }
            }
        }
    }

    private fun notify(n: SyncService.Notice) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, build(n))
    }

    private fun build(n: SyncService.Notice): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, SynchroService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(n.title).setContentText(n.text)
            .setContentIntent(open)
            .addAction(0, "Arrêter", stop)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Synchro entre téléphones", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        collect?.cancel(); collect = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "synchro"
        private const val NOTIF_ID = 4301
        const val ACTION_STOP = "app.lumisite.android.sync.SynchroService.STOP"

        /** Démarre le service de premier plan ; suit NearbyMesh.start (rôle actif). Échec journalisé, non bloquant. */
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, SynchroService::class.java)) }
                .onFailure { DiagLog.i("SYNCHRO", "service de premier plan non démarré : $it") }
        }

        /** Arrête le service ; suit NearbyMesh.stop. Idempotent. */
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, SynchroService::class.java)) }
        }
    }
}
