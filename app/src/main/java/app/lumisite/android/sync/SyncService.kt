package app.lumisite.android.sync

import android.Manifest
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

/**
 * v176g 1f: foreground service kept while the Nearby synchro runs (master or slave), so that HyperOS / Android do not
 * cut Nearby and the microphone with the screen off. It does no work itself: [NearbyMesh] lives in the process.
 */
class SyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = runCatching { ServiceCompat.startForeground(this, NOTIF_ID, notification(this), types(this)) }
            .onFailure { DiagLog.e("SYNCHRO", "Service de premier plan refusé", it) }.isSuccess
        if (!ok) stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "synchro"
        private const val NOTIF_ID = 4207

        /** connectedDevice (Nearby) always; microphone only once RECORD_AUDIO is granted (Android 14 refuses it otherwise). */
        private fun types(ctx: Context): Int {
            if (Build.VERSION.SDK_INT < 29) return 0
            var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (Build.VERSION.SDK_INT >= 30 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            return t
        }

        private fun notification(ctx: Context): android.app.Notification {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Synchro", NotificationManager.IMPORTANCE_LOW))
            }
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Synchro LumiSite active")
                .setContentText("Nearby et micro gardés actifs, écran éteint compris. Arrêt : Accueil > Sync > Arrêter la synchro.")
                .setOngoing(true).setContentIntent(open).setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        /** Returns false when the system refuses to start it (journaled by the caller). */
        fun start(ctx: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, SyncService::class.java)); true
        }.getOrElse { DiagLog.e("SYNCHRO", "Démarrage du service refusé", it); false }

        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, SyncService::class.java)) } }
    }
}
