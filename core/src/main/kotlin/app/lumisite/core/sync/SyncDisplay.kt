package app.lumisite.core.sync

/**
 * Retour terrain build 199 (17/11), points 1 et 2 : ce que l'écran Sync (Accueil) ET l'onglet Session > Synchro affichent
 * à partir du même état Nearby. Fonctions pures (SyncDisplayTest).
 */
object SyncDisplay {
    /** Niveau d'un bandeau : vert, orange, rouge. */
    enum class Level { OK, WAIT, ERROR }
    data class Banner(val text: String, val level: Level)

    /**
     * Nom lisible du téléphone, annoncé par Nearby (endpointName) puis par Hello : le nom choisi par l'utilisateur
     * (Réglages > Nom de ce téléphone, sinon Settings.Global « device_name ») avant Build.MODEL, qui vaut un code sur
     * Xiaomi (« 23078PND5G » pour un 13T Pro). Suffixe = 4 derniers caractères de l'identifiant, pour distinguer deux
     * téléphones identiques. Longueur bornée (Nearby limite l'endpointName).
     */
    fun deviceLabel(userName: String?, systemDeviceName: String?, manufacturer: String, model: String, idSuffix: String): String {
        val base = userName?.trim()?.takeIf { it.isNotEmpty() }
            ?: systemDeviceName?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (model.startsWith(manufacturer, ignoreCase = true)) model else "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        return "${base.take(40)} (${idSuffix.takeLast(4)})"
    }

    /**
     * Bandeau de connexion du maître : vert dès qu'au moins un esclave est connecté (même si l'horloge n'est pas encore
     * alignée), avec les noms ; orange tant qu'on cherche ; rouge si tous sont perdus.
     */
    fun masterBanner(peers: List<Peer>): Banner {
        val connected = peers.filter { it.link == Link.CONNECTED }
        val lost = peers.count { it.link == Link.LOST }
        return when {
            connected.isNotEmpty() -> Banner("Connecté : " + connected.joinToString { it.name } +
                (if (connected.any { !it.synced }) " · alignement en cours" else " · synchronisé") + (if (lost > 0) " · $lost perdu(s)" else ""), Level.OK)
            peers.any { it.link == Link.CONNECTING } -> Banner("Connexion en cours : " + peers.filter { it.link == Link.CONNECTING }.joinToString { it.name }, Level.WAIT)
            lost > 0 -> Banner("Esclave perdu : " + peers.filter { it.link == Link.LOST }.joinToString { it.name } + " · reconnexion automatique", Level.ERROR)
            else -> Banner("Recherche d'esclaves…", Level.WAIT)
        }
    }

    /** Bandeau côté esclave : nom du maître et âge du dernier ping (vert < 10 s, orange au-delà, rouge sans maître). */
    fun slaveBanner(masterName: String?, lastPingAgeS: Long?): Banner = when {
        masterName == null -> Banner("Recherche du maître…", Level.WAIT)
        lastPingAgeS == null -> Banner("Connecté à $masterName · en attente du premier ping", Level.WAIT)
        lastPingAgeS < 10 -> Banner("Connecté à $masterName · dernier ping il y a $lastPingAgeS s", Level.OK)
        else -> Banner("Connecté à $masterName · aucun ping depuis $lastPingAgeS s", Level.ERROR)
    }

    /**
     * Le décalage entre horloges monotones (temps depuis le démarrage de chaque téléphone) vaut souvent des jours : le
     * journal du Pixel du 17/11 affiche « décalage 1001033936,0 ms ». À l'écran on montre l'incertitude, qui est la donnée
     * utile ; le décalage brut reste dans synchro.ndjson.
     */
    fun alignmentText(offsetNs: Long?, uncertaintyNs: Long?): String = when {
        offsetNs == null -> "non aligné"
        uncertaintyNs == null -> "aligné"
        else -> "aligné à ± %.1f ms".format(java.util.Locale.FRANCE, uncertaintyNs / 1e6)
    }
}
