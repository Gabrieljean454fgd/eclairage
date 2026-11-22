package app.lumisite.core.sync

import java.util.Locale

/**
 * v176g § 1: what Accueil > Sync and Session > Synchro show about the Nearby synchro. Pure functions, shared by both
 * screens so they cannot disagree. Raw monotonic offsets (between unrelated boot clocks, often days) stay in
 * synchro.ndjson; the screen only shows how well the clocks are aligned.
 */
object SyncDisplay {
    const val MAX_NAME = 40
    /** Slave side: the master is shown green while its last ping is younger than this. */
    const val PING_FRESH_S = 10L

    enum class Tone { VERT, ORANGE, ROUGE }

    data class Banner(val text: String, val tone: Tone)

    /**
     * Readable phone name: the name typed in Réglages > Nom de ce téléphone, else the system device name, else
     * manufacturer + model (Xiaomi's Build.MODEL alone is a code such as « 23078PND5G »), then « (ABCD) » from the id.
     */
    fun deviceLabel(userName: String?, systemDeviceName: String?, manufacturer: String?, model: String?, idSuffix: String): String {
        fun clean(s: String?) = s?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val man = clean(manufacturer); val mod = clean(model)
        val hardware = when {
            man.isEmpty() -> mod
            mod.isEmpty() -> man
            mod.startsWith(man, ignoreCase = true) -> mod
            else -> "${man.replaceFirstChar { it.titlecase(Locale.ROOT) }} $mod"
        }
        val base = listOf(clean(userName), clean(systemDeviceName), hardware).firstOrNull { it.isNotEmpty() } ?: "Téléphone"
        val suffix = clean(idSuffix).takeLast(4).uppercase(Locale.ROOT)
        if (suffix.isEmpty()) return base.take(MAX_NAME).trimEnd()
        val room = MAX_NAME - suffix.length - 3
        return "${base.take(room).trimEnd()} ($suffix)"
    }

    /** « aligné à ± 33,9 ms »: the uncertainty only, never the raw offset between monotonic clocks. */
    fun alignmentText(offsetNs: Long?, uncertaintyNs: Long?): String =
        if (offsetNs == null || uncertaintyNs == null) "non aligné"
        else "aligné à ± " + String.format(Locale.FRANCE, "%.1f", uncertaintyNs / 1e6) + " ms"

    /** Master banner: green as soon as one slave is connected, orange while searching / connecting, red if all are lost. */
    fun masterBanner(peers: List<Peer>): Banner {
        val connected = peers.filter { it.link == Link.CONNECTED }
        if (connected.isNotEmpty()) {
            val names = connected.joinToString(", ") { it.name + if (it.synced) " · synchronisé" else " · alignement en cours" }
            return Banner("Connecté : $names", Tone.VERT)
        }
        val connecting = peers.filter { it.link == Link.CONNECTING }
        if (connecting.isNotEmpty()) return Banner("Connexion en cours : " + connecting.joinToString(", ") { it.name }, Tone.ORANGE)
        if (peers.isNotEmpty()) return Banner("Perdu : " + peers.joinToString(", ") { it.name } + " · reconnexion automatique attendue", Tone.ROUGE)
        return Banner("Recherche d'esclaves… (0 appareil)", Tone.ORANGE)
    }

    /** Slave banner: « Connecté à Pixel 7 (JSNK) · dernier ping il y a N s », green under [PING_FRESH_S], red beyond. */
    fun slaveBanner(masterName: String?, lastPingAgeS: Long?): Banner {
        if (masterName.isNullOrBlank()) return Banner("Recherche du maître…", Tone.ORANGE)
        if (lastPingAgeS == null) return Banner("Connecté à $masterName · aucun ping reçu", Tone.ORANGE)
        val age = lastPingAgeS.coerceAtLeast(0)
        return Banner("Connecté à $masterName · dernier ping il y a $age s", if (age < PING_FRESH_S) Tone.VERT else Tone.ROUGE)
    }

    /** Accueil chip while the master role runs. */
    fun homeChip(connected: Int): String = "Sync maître active · $connected esclave(s)"
}
