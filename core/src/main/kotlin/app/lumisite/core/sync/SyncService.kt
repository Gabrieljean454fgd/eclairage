package app.lumisite.core.sync

/**
 * Retour build 199, point 1f : la synchro Nearby (objet NearbyMesh) tournait déjà au retour arrière ; ce fichier
 * porte la logique pure du service de premier plan qui l'empêche d'être tuée écran éteint (HyperOS/Xiaomi) :
 * quand il doit tourner, quels types de premier plan il demande, le texte de sa notification permanente.
 * Côté app : SynchroService (app/.../sync/SynchroService.kt). Test JVM : SyncServiceTest.
 */
object SyncService {
    /** Types de premier plan demandés à startForeground (miroir des FOREGROUND_SERVICE_TYPE_* de ServiceInfo). */
    enum class ForegroundType { CONNECTED_DEVICE, MICROPHONE }

    /**
     * Le service suit la synchro : il tourne dès qu'un rôle est actif (démarré par NearbyMesh.start, arrêté par
     * NearbyMesh.stop via ACTION_STOP / stopService — le service n'a pas à décider de la fin de vie lui-même).
     */
    fun shouldRun(running: Boolean, role: SyncRole2?): Boolean = running && role != null

    /**
     * Types passés à startForeground : connectedDevice toujours (radio Nearby), microphone en plus quand
     * RECORD_AUDIO est accordé — Android 14+ exige le type microphone pour garder l'enregistrement audio de
     * l'esclave avec l'écran éteint. Le manifeste déclare les deux types ; le masque runtime reste un sous-ensemble.
     */
    fun foregroundTypes(recordAudioGranted: Boolean): Set<ForegroundType> =
        if (recordAudioGranted) setOf(ForegroundType.CONNECTED_DEVICE, ForegroundType.MICROPHONE)
        else setOf(ForegroundType.CONNECTED_DEVICE)

    /** Contenu de la notification permanente. */
    data class Notice(val title: String, val text: String)

    /**
     * « Synchro LumiSite active » + l'état : « N esclave(s) » (connectés) côté maître, nom du maître ou
     * « Recherche du maître… » côté esclave.
     */
    fun notice(role: SyncRole2, connectedSlaves: Int, masterName: String?): Notice = when (role) {
        SyncRole2.SAISIE_MAITRE -> Notice("Synchro LumiSite active", "$connectedSlaves esclave(s)")
        SyncRole2.CAPTATION_ESCLAVE -> Notice("Synchro LumiSite active",
            masterName?.let { "Connecté à $it" } ?: "Recherche du maître…")
    }
}
