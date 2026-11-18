package app.lumisite.core.version

/**
 * Retour terrain build 199 (17/11), point 0 : l'écran Accueil affiche « Build N · <paquet> », où <paquet> identifie
 * le paquet de travail compilé (la branche de build), pas une version marketing.
 *
 * La valeur brute est choisie à la compilation (app/build.gradle.kts, champ BuildConfig.PAQUET) :
 * variable d'environnement LUMISITE_PAQUET, sinon GITHUB_HEAD_REF, sinon GITHUB_REF_NAME, sinon la branche git
 * courante. [from] la transforme : « etape-3-v176g-xxx » → « v176g », « etape-3-local-xxx » → « local-xxx »,
 * nom absent ou vide → « dev », tout autre nom gardé tel quel. Idempotente : from(from(x)) == from(x), donc
 * appeler [from] côté appli sur BuildConfig.PAQUET reste correct même si la transformation a déjà eu lieu au build.
 */
object PaquetName {
    private val ETAPE_VERSION = Regex("^etape-\\d+-(v\\d+[a-z]?)(\\b.*)?$")
    private val ETAPE_LOCAL = Regex("^etape-\\d+-(local-.+)$")

    fun from(raw: String?): String {
        val s = raw?.trim() ?: return "dev"
        if (s.isEmpty()) return "dev"
        ETAPE_VERSION.matchEntire(s)?.let { return it.groupValues[1] }
        ETAPE_LOCAL.matchEntire(s)?.let { return it.groupValues[1] }
        return s
    }
}
