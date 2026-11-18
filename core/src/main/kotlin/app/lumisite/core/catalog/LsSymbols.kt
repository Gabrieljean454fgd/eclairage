package app.lumisite.core.catalog

/**
 * Bibliothèque de symboles LS_* — dessin ORIGINAL dans l'esprit des symboles de plan NF EN 60617-11
 * (point lumineux = cercle avec croix, prise = demi-cercle, BAES = rectangle avec trait, etc.).
 *
 * Description pure : chaque symbole est une liste de primitives dans le carré unitaire [0,1]²
 * (x vers la droite, y vers le bas, origine en haut à gauche). Le rendu (Compose Canvas, bitmap
 * osmdroid) est fait dans app/ ; les couleurs viennent de la famille.
 */
sealed class LsPrim {
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : LsPrim()
    /** fill = disque plein, sinon cercle au trait. */
    data class Circle(val cx: Float, val cy: Float, val r: Float, val fill: Boolean = false) : LsPrim()
    /** Arc de cercle (degrés, sens horaire depuis 3 h, comme android.graphics). */
    data class Arc(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val sweepDeg: Float) : LsPrim()
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float, val fill: Boolean = false) : LsPrim()
    /** Polyligne / polygone : coordonnées aplaties x1,y1,x2,y2,… ; close+fill pour un remplissage. */
    data class Poly(val pts: List<Float>, val close: Boolean = false, val fill: Boolean = false) : LsPrim()
}

object LsSymbols {

    // ------------------------------------------------------------------ familles

    /** Couleur ARGB d'affichage par famille (sélecteur et carte). */
    fun familyColor(famille: String): Long = when (famille) {
        "ECLAIRAGE" -> 0xFFF9A825   // ambre
        "ELEC" -> 0xFF1976D2        // bleu
        "SECOURS" -> 0xFF2E7D32     // vert
        "SECURITE" -> 0xFF00897B    // turquoise (signalétique)
        "INCENDIE" -> 0xFFC62828    // rouge
        "CAMERA" -> 0xFF6A1B9A      // violet
        "SON" -> 0xFFEF6C00         // orange
        else -> 0xFF616161          // AUTRE et inconnues : gris
    }

    /** Symbole représentatif d'une famille (puce de la grille des familles). */
    fun familySymbol(famille: String): String = when (famille) {
        "ECLAIRAGE" -> "point_lumineux"
        "ELEC" -> "prise"
        "SECOURS" -> "baes"
        "SECURITE" -> "signal_sortie"
        "INCENDIE" -> "detecteur_fumee"
        "CAMERA" -> "camera"
        "SON" -> "haut_parleur"
        else -> "autre"
    }

    // ------------------------------------------------------------------ glyph helpers

    private fun l(x1: Float, y1: Float, x2: Float, y2: Float) = LsPrim.Line(x1, y1, x2, y2)
    private fun c(cx: Float, cy: Float, r: Float, fill: Boolean = false) = LsPrim.Circle(cx, cy, r, fill)
    private fun arc(cx: Float, cy: Float, r: Float, s: Float, sw: Float) = LsPrim.Arc(cx, cy, r, s, sw)
    private fun r(l: Float, t: Float, rr: Float, b: Float, fill: Boolean = false) = LsPrim.Rect(l, t, rr, b, fill)
    private fun p(vararg pts: Float, close: Boolean = false, fill: Boolean = false) = LsPrim.Poly(pts.toList(), close, fill)

    // ------------------------------------------------------------------ symboles

    /** id de symbole → primitives. Tous les ids utilisés dans lumisite_catalog_2.json sont couverts. */
    val glyphs: Map<String, List<LsPrim>> = mapOf(

        // ---- Éclairage
        "point_lumineux" to listOf(
            c(0.5f, 0.5f, 0.34f),
            l(0.26f, 0.26f, 0.74f, 0.74f), l(0.74f, 0.26f, 0.26f, 0.74f),
        ),
        "spot_surface" to listOf(
            c(0.5f, 0.5f, 0.34f),
            l(0.26f, 0.26f, 0.74f, 0.74f), l(0.74f, 0.26f, 0.26f, 0.74f),
            c(0.5f, 0.5f, 0.10f, fill = true),
        ),
        "spot_sol" to listOf(
            r(0.16f, 0.16f, 0.84f, 0.84f),
            l(0.16f, 0.16f, 0.84f, 0.84f), l(0.84f, 0.16f, 0.16f, 0.84f),
            l(0.5f, 0.84f, 0.5f, 0.62f),
        ),
        "reglette" to listOf(
            r(0.06f, 0.36f, 0.94f, 0.64f),
            l(0.06f, 0.36f, 0.94f, 0.64f), l(0.94f, 0.36f, 0.06f, 0.64f),
        ),
        "lineaire" to listOf(
            r(0.04f, 0.42f, 0.96f, 0.58f),
        ),
        "projecteur" to listOf(
            arc(0.5f, 0.55f, 0.30f, 90f, 180f),
            l(0.5f, 0.25f, 0.5f, 0.85f),
            p(0.5f, 0.55f, 0.86f, 0.34f, 0.86f, 0.76f, close = true, fill = true),
        ),
        "vitrail" to listOf(
            c(0.34f, 0.5f, 0.24f),
            l(0.34f, 0.26f, 0.34f, 0.74f),
            l(0.58f, 0.5f, 0.86f, 0.5f), p(0.86f, 0.5f, 0.74f, 0.40f, 0.74f, 0.60f, close = true, fill = true),
        ),
        "lustre" to listOf(
            c(0.5f, 0.5f, 0.22f),
            l(0.5f, 0.10f, 0.5f, 0.28f), l(0.5f, 0.72f, 0.5f, 0.90f),
            l(0.10f, 0.5f, 0.28f, 0.5f), l(0.72f, 0.5f, 0.90f, 0.5f),
            l(0.22f, 0.22f, 0.34f, 0.34f), l(0.78f, 0.22f, 0.66f, 0.34f),
            l(0.22f, 0.78f, 0.34f, 0.66f), l(0.78f, 0.78f, 0.66f, 0.66f),
        ),
        "lanterne" to listOf(
            c(0.5f, 0.56f, 0.24f),
            l(0.5f, 0.32f, 0.5f, 0.12f), c(0.5f, 0.10f, 0.04f, fill = true),
            l(0.38f, 0.62f, 0.5f, 0.46f), l(0.62f, 0.62f, 0.5f, 0.46f),
        ),
        "suspension" to listOf(
            l(0.5f, 0.06f, 0.5f, 0.38f), l(0.30f, 0.06f, 0.70f, 0.06f),
            c(0.5f, 0.62f, 0.24f),
            l(0.33f, 0.69f, 0.67f, 0.69f),
        ),
        "applique" to listOf(
            l(0.3f, 0.06f, 0.3f, 0.94f),
            arc(0.3f, 0.5f, 0.28f, -90f, 180f),
        ),
        "hublot" to listOf(
            c(0.5f, 0.5f, 0.36f),
            arc(0.5f, 0.5f, 0.22f, -90f, 180f),
            l(0.5f, 0.28f, 0.5f, 0.72f),
        ),
        "balise" to listOf(
            r(0.30f, 0.55f, 0.70f, 0.80f),
            l(0.5f, 0.55f, 0.5f, 0.30f), c(0.5f, 0.26f, 0.05f, fill = true),
        ),
        "herse" to listOf(
            l(0.08f, 0.2f, 0.92f, 0.2f),
            c(0.25f, 0.5f, 0.12f), c(0.5f, 0.5f, 0.12f), c(0.75f, 0.5f, 0.12f),
            l(0.25f, 0.38f, 0.25f, 0.2f), l(0.5f, 0.38f, 0.5f, 0.2f), l(0.75f, 0.38f, 0.75f, 0.2f),
        ),
        "lampe_temp" to listOf(
            p(0.5f, 0.14f, 0.90f, 0.82f, 0.10f, 0.82f, close = true),
            c(0.5f, 0.58f, 0.14f),
            l(0.41f, 0.49f, 0.59f, 0.67f), l(0.59f, 0.49f, 0.41f, 0.67f),
        ),
        "bougeoir" to listOf(
            l(0.5f, 0.42f, 0.5f, 0.80f), l(0.34f, 0.80f, 0.66f, 0.80f),
            c(0.5f, 0.30f, 0.09f),
            l(0.5f, 0.18f, 0.5f, 0.12f),
        ),

        // ---- Électricité
        "prise" to listOf(
            arc(0.5f, 0.55f, 0.32f, -180f, 180f),
            l(0.18f, 0.55f, 0.82f, 0.55f),
            l(0.5f, 0.55f, 0.5f, 0.85f),
        ),
        "rj45" to listOf(
            r(0.26f, 0.22f, 0.74f, 0.78f),
            l(0.26f, 0.40f, 0.74f, 0.40f),
            l(0.38f, 0.22f, 0.38f, 0.40f), l(0.62f, 0.22f, 0.62f, 0.40f),
        ),
        "interrupteur" to listOf(
            c(0.42f, 0.62f, 0.16f),
            l(0.42f, 0.62f, 0.74f, 0.30f),
            c(0.74f, 0.30f, 0.05f, fill = true),
        ),
        "va_et_vient" to listOf(
            c(0.30f, 0.62f, 0.14f), c(0.70f, 0.62f, 0.14f),
            l(0.30f, 0.62f, 0.56f, 0.30f), l(0.70f, 0.62f, 0.88f, 0.30f),
        ),
        "variateur" to listOf(
            c(0.42f, 0.62f, 0.16f),
            l(0.42f, 0.62f, 0.74f, 0.30f),
            p(0.68f, 0.78f, 0.76f, 0.70f, 0.84f, 0.78f, 0.92f, 0.70f),
        ),
        "tableau" to listOf(
            r(0.28f, 0.12f, 0.72f, 0.88f),
            l(0.28f, 0.30f, 0.72f, 0.30f),
            l(0.5f, 0.30f, 0.5f, 0.88f),
        ),
        "tableau_div" to listOf(
            r(0.28f, 0.12f, 0.72f, 0.88f),
            l(0.28f, 0.32f, 0.72f, 0.32f),
            l(0.28f, 0.52f, 0.72f, 0.52f), l(0.28f, 0.72f, 0.72f, 0.72f),
        ),
        "coffret" to listOf(
            r(0.24f, 0.24f, 0.76f, 0.76f),
            l(0.24f, 0.24f, 0.76f, 0.76f),
            c(0.5f, 0.5f, 0.07f, fill = true),
        ),
        "differentiel" to listOf(
            r(0.24f, 0.18f, 0.76f, 0.82f),
            p(0.32f, 0.6f, 0.44f, 0.4f, 0.56f, 0.6f, 0.68f, 0.4f),
        ),
        "derivation" to listOf(
            c(0.5f, 0.5f, 0.16f),
            l(0.5f, 0.34f, 0.5f, 0.12f), l(0.5f, 0.66f, 0.5f, 0.88f),
            l(0.34f, 0.5f, 0.12f, 0.5f), l(0.66f, 0.5f, 0.88f, 0.5f),
        ),
        "chemin_cables" to listOf(
            l(0.08f, 0.36f, 0.92f, 0.36f), l(0.08f, 0.64f, 0.92f, 0.64f),
            l(0.32f, 0.36f, 0.32f, 0.64f), l(0.68f, 0.36f, 0.68f, 0.64f),
        ),
        "detecteur_mouvement" to listOf(
            arc(0.5f, 0.62f, 0.12f, -180f, 180f),
            arc(0.5f, 0.62f, 0.25f, -180f, 180f),
            arc(0.5f, 0.62f, 0.37f, -180f, 180f),
            c(0.5f, 0.62f, 0.05f, fill = true),
        ),
        "pdl" to listOf(
            p(0.5f, 0.12f, 0.88f, 0.5f, 0.5f, 0.88f, 0.12f, 0.5f, close = true),
            l(0.5f, 0.12f, 0.5f, 0.88f),
        ),

        // ---- Secours
        "baes" to listOf(
            r(0.14f, 0.28f, 0.86f, 0.72f),
            l(0.14f, 0.28f, 0.86f, 0.72f),
        ),
        "baes_adresse" to listOf(
            r(0.14f, 0.28f, 0.86f, 0.72f),
            l(0.14f, 0.28f, 0.86f, 0.72f),
            c(0.5f, 0.5f, 0.06f, fill = true),
        ),
        "baeh" to listOf(
            r(0.14f, 0.28f, 0.86f, 0.72f),
            l(0.14f, 0.28f, 0.86f, 0.72f),
            l(0.14f, 0.50f, 0.86f, 0.50f),
        ),
        "bloc_np" to listOf(
            r(0.14f, 0.28f, 0.86f, 0.72f),
            l(0.14f, 0.28f, 0.86f, 0.72f),
            c(0.74f, 0.40f, 0.07f),
        ),
        "lsc" to listOf(
            r(0.14f, 0.28f, 0.86f, 0.72f),
            l(0.24f, 0.5f, 0.66f, 0.5f), p(0.66f, 0.5f, 0.54f, 0.40f, 0.54f, 0.60f, close = true, fill = true),
        ),
        "lse" to listOf(
            r(0.14f, 0.28f, 0.86f, 0.72f),
            r(0.38f, 0.38f, 0.56f, 0.72f),
            l(0.56f, 0.55f, 0.72f, 0.55f),
        ),
        "cat" to listOf(
            r(0.20f, 0.24f, 0.80f, 0.76f),
            c(0.5f, 0.5f, 0.14f),
            c(0.5f, 0.5f, 0.05f, fill = true),
        ),
        "asi" to listOf(
            r(0.18f, 0.24f, 0.82f, 0.76f),
            p(0.24f, 0.55f, 0.36f, 0.40f, 0.50f, 0.62f, 0.64f, 0.38f, 0.76f, 0.55f),
        ),
        "groupe_electrogene" to listOf(
            r(0.14f, 0.30f, 0.86f, 0.74f),
            p(0.56f, 0.34f, 0.40f, 0.56f, 0.54f, 0.56f, 0.44f, 0.72f),
        ),
        "inverseur" to listOf(
            r(0.18f, 0.24f, 0.82f, 0.76f),
            l(0.28f, 0.42f, 0.66f, 0.42f), p(0.66f, 0.42f, 0.56f, 0.34f, 0.56f, 0.50f, close = true, fill = true),
            l(0.72f, 0.60f, 0.34f, 0.60f), p(0.34f, 0.60f, 0.44f, 0.52f, 0.44f, 0.68f, close = true, fill = true),
        ),
        "coffret_relais" to listOf(
            r(0.26f, 0.26f, 0.74f, 0.74f),
            arc(0.5f, 0.5f, 0.16f, 0f, 180f),
            l(0.5f, 0.26f, 0.5f, 0.34f), l(0.5f, 0.66f, 0.5f, 0.74f),
        ),
        "luminaire_secouru" to listOf(
            c(0.5f, 0.5f, 0.30f),
            l(0.29f, 0.29f, 0.71f, 0.71f), l(0.71f, 0.29f, 0.29f, 0.71f),
            r(0.70f, 0.06f, 0.94f, 0.24f),
        ),

        // ---- Sécurité / signalétique
        "signal_sortie" to listOf(
            r(0.12f, 0.26f, 0.88f, 0.74f),
            l(0.24f, 0.5f, 0.66f, 0.5f), p(0.66f, 0.5f, 0.52f, 0.38f, 0.52f, 0.62f, close = true, fill = true),
            l(0.76f, 0.38f, 0.76f, 0.62f),
        ),
        "antipanique" to listOf(
            r(0.18f, 0.24f, 0.82f, 0.76f),
            p(0.5f, 0.34f, 0.58f, 0.44f, 0.42f, 0.44f, close = true, fill = true),
            p(0.5f, 0.66f, 0.58f, 0.56f, 0.42f, 0.56f, close = true, fill = true),
        ),
        "ambiance" to listOf(
            r(0.18f, 0.24f, 0.82f, 0.76f),
            c(0.5f, 0.5f, 0.12f),
        ),
        "remplacement" to listOf(
            c(0.5f, 0.5f, 0.30f),
            arc(0.5f, 0.5f, 0.18f, -60f, 240f),
            p(0.62f, 0.33f, 0.72f, 0.30f, 0.68f, 0.42f, close = true, fill = true),
        ),
        "fleche_direction" to listOf(
            l(0.16f, 0.5f, 0.70f, 0.5f),
            p(0.84f, 0.5f, 0.58f, 0.28f, 0.58f, 0.72f, close = true, fill = true),
        ),
        "signal_issue" to listOf(
            r(0.22f, 0.18f, 0.62f, 0.82f),
            l(0.42f, 0.5f, 0.78f, 0.5f), p(0.78f, 0.5f, 0.66f, 0.40f, 0.66f, 0.60f, close = true, fill = true),
        ),
        "balisage" to listOf(
            r(0.12f, 0.62f, 0.30f, 0.80f, fill = true),
            r(0.41f, 0.62f, 0.59f, 0.80f, fill = true),
            r(0.70f, 0.62f, 0.88f, 0.80f, fill = true),
        ),
        "rassemblement" to listOf(
            c(0.5f, 0.5f, 0.08f, fill = true),
            l(0.5f, 0.14f, 0.5f, 0.38f), l(0.5f, 0.86f, 0.5f, 0.62f),
            l(0.14f, 0.5f, 0.38f, 0.5f), l(0.86f, 0.5f, 0.62f, 0.5f),
        ),
        "premiers_secours" to listOf(
            r(0.38f, 0.14f, 0.62f, 0.86f, fill = true),
            r(0.14f, 0.38f, 0.86f, 0.62f, fill = true),
        ),
        "picto_extincteur" to listOf(
            r(0.38f, 0.34f, 0.62f, 0.82f),
            c(0.5f, 0.28f, 0.07f),
            l(0.5f, 0.28f, 0.34f, 0.16f),
        ),

        // ---- Incendie
        "detecteur_fumee" to listOf(
            c(0.5f, 0.5f, 0.32f),
            c(0.5f, 0.5f, 0.10f, fill = true),
        ),
        "detecteur_thermique" to listOf(
            c(0.5f, 0.5f, 0.32f),
            l(0.5f, 0.32f, 0.5f, 0.62f), c(0.5f, 0.62f, 0.07f, fill = true),
        ),
        "detecteur_flamme" to listOf(
            p(0.5f, 0.16f, 0.84f, 0.76f, 0.16f, 0.76f, close = true),
            c(0.5f, 0.56f, 0.10f, fill = true),
        ),
        "detecteur_lineaire" to listOf(
            l(0.10f, 0.5f, 0.90f, 0.5f),
            c(0.24f, 0.5f, 0.10f), c(0.76f, 0.5f, 0.10f),
        ),
        "centrale_incendie" to listOf(
            r(0.20f, 0.20f, 0.80f, 0.80f),
            l(0.5f, 0.34f, 0.5f, 0.66f), l(0.34f, 0.5f, 0.66f, 0.5f),
        ),
        "cmsi" to listOf(
            r(0.20f, 0.20f, 0.80f, 0.80f),
            l(0.20f, 0.34f, 0.80f, 0.34f), l(0.20f, 0.66f, 0.80f, 0.66f),
        ),
        "declencheur" to listOf(
            r(0.26f, 0.26f, 0.74f, 0.74f),
            c(0.5f, 0.5f, 0.10f, fill = true),
        ),
        "alarme" to listOf(
            c(0.42f, 0.5f, 0.18f),
            arc(0.42f, 0.5f, 0.30f, -55f, 110f),
            arc(0.42f, 0.5f, 0.42f, -55f, 110f),
        ),
        "flash" to listOf(
            c(0.5f, 0.56f, 0.20f),
            l(0.5f, 0.36f, 0.5f, 0.20f), l(0.32f, 0.46f, 0.20f, 0.36f), l(0.68f, 0.46f, 0.80f, 0.36f),
            p(0.5f, 0.14f, 0.44f, 0.22f, 0.52f, 0.22f, 0.46f, 0.30f),
        ),
        "repeteur" to listOf(
            r(0.18f, 0.26f, 0.82f, 0.74f),
            r(0.34f, 0.40f, 0.66f, 0.60f),
        ),
        "sirene" to listOf(
            arc(0.5f, 0.66f, 0.24f, -180f, 180f),
            l(0.26f, 0.66f, 0.74f, 0.66f),
            arc(0.5f, 0.66f, 0.34f, -180f, 180f),
        ),
        "module_relais" to listOf(
            r(0.28f, 0.28f, 0.72f, 0.72f),
            l(0.28f, 0.72f, 0.72f, 0.28f),
            l(0.28f, 0.5f, 0.12f, 0.5f), l(0.72f, 0.5f, 0.88f, 0.5f),
        ),
        "extincteur" to listOf(
            r(0.38f, 0.32f, 0.62f, 0.84f),
            l(0.5f, 0.32f, 0.5f, 0.22f), c(0.5f, 0.20f, 0.05f),
            l(0.62f, 0.30f, 0.74f, 0.18f), l(0.74f, 0.18f, 0.74f, 0.30f),
            l(0.44f, 0.48f, 0.56f, 0.48f),
        ),
        "ria" to listOf(
            c(0.5f, 0.5f, 0.32f),
            c(0.5f, 0.5f, 0.18f),
            l(0.5f, 0.5f, 0.68f, 0.32f),
        ),
        "baas" to listOf(
            r(0.14f, 0.30f, 0.86f, 0.70f),
            l(0.14f, 0.30f, 0.86f, 0.70f),
            arc(0.5f, 0.78f, 0.10f, -180f, 180f),
        ),

        // ---- Caméra / sûreté
        "camera" to listOf(
            c(0.36f, 0.5f, 0.20f),
            p(0.56f, 0.5f, 0.90f, 0.30f, 0.90f, 0.70f, close = true, fill = true),
        ),
        "camera_dome" to listOf(
            arc(0.5f, 0.62f, 0.28f, -180f, 180f),
            l(0.22f, 0.62f, 0.78f, 0.62f),
            c(0.5f, 0.52f, 0.08f, fill = true),
        ),
        "camera_bullet" to listOf(
            r(0.14f, 0.38f, 0.66f, 0.62f),
            p(0.66f, 0.5f, 0.90f, 0.34f, 0.90f, 0.66f, close = true, fill = true),
        ),
        "camera_ptz" to listOf(
            c(0.5f, 0.5f, 0.28f),
            p(0.5f, 0.5f, 0.86f, 0.30f, 0.86f, 0.70f, close = true, fill = true),
            arc(0.5f, 0.5f, 0.42f, -50f, 280f),
        ),
        "camera_thermique" to listOf(
            c(0.36f, 0.5f, 0.18f),
            p(0.54f, 0.5f, 0.86f, 0.32f, 0.86f, 0.68f, close = true, fill = true),
            p(0.18f, 0.78f, 0.26f, 0.72f, 0.34f, 0.78f, 0.42f, 0.72f),
        ),
        "camera_fisheye" to listOf(
            c(0.5f, 0.5f, 0.32f),
            c(0.5f, 0.5f, 0.16f),
            c(0.5f, 0.5f, 0.06f, fill = true),
        ),
        "enregistreur" to listOf(
            r(0.14f, 0.30f, 0.86f, 0.70f),
            c(0.30f, 0.5f, 0.08f, fill = true),
            l(0.48f, 0.42f, 0.76f, 0.42f), l(0.48f, 0.58f, 0.76f, 0.58f),
        ),
        "serveur" to listOf(
            r(0.20f, 0.16f, 0.80f, 0.84f),
            l(0.20f, 0.39f, 0.80f, 0.39f), l(0.20f, 0.61f, 0.80f, 0.61f),
            c(0.30f, 0.28f, 0.04f, fill = true), c(0.30f, 0.50f, 0.04f, fill = true), c(0.30f, 0.72f, 0.04f, fill = true),
        ),
        "badge" to listOf(
            r(0.28f, 0.14f, 0.72f, 0.86f),
            c(0.5f, 0.36f, 0.09f),
            arc(0.5f, 0.66f, 0.16f, -180f, 180f),
        ),
        "interphone" to listOf(
            r(0.30f, 0.14f, 0.70f, 0.86f),
            c(0.5f, 0.34f, 0.08f),
            r(0.42f, 0.52f, 0.58f, 0.72f),
        ),
        "centrale_intrusion" to listOf(
            r(0.22f, 0.22f, 0.78f, 0.78f),
            p(0.5f, 0.30f, 0.58f, 0.44f, 0.72f, 0.46f, 0.62f, 0.60f, 0.66f, 0.74f, 0.5f, 0.66f, 0.34f, 0.74f, 0.38f, 0.60f, 0.28f, 0.46f, 0.42f, 0.44f, close = true),
        ),
        "detecteur_ir" to listOf(
            c(0.5f, 0.62f, 0.10f, fill = true),
            arc(0.5f, 0.62f, 0.24f, -180f, 180f),
            arc(0.5f, 0.62f, 0.38f, -180f, 180f),
        ),

        // ---- Son
        "amplificateur" to listOf(
            r(0.14f, 0.30f, 0.86f, 0.70f),
            p(0.30f, 0.60f, 0.55f, 0.40f, 0.55f, 0.60f, close = true),
            l(0.55f, 0.50f, 0.74f, 0.50f),
        ),
        "enceinte" to listOf(
            r(0.26f, 0.14f, 0.74f, 0.86f),
            c(0.5f, 0.60f, 0.14f),
            c(0.5f, 0.30f, 0.07f),
        ),
        "haut_parleur" to listOf(
            p(0.22f, 0.40f, 0.40f, 0.40f, 0.62f, 0.24f, 0.62f, 0.76f, 0.40f, 0.60f, 0.22f, 0.60f, close = true),
            arc(0.62f, 0.5f, 0.16f, -55f, 110f),
            arc(0.62f, 0.5f, 0.28f, -55f, 110f),
        ),
        "micro" to listOf(
            c(0.5f, 0.28f, 0.12f),
            l(0.5f, 0.40f, 0.5f, 0.74f), l(0.34f, 0.80f, 0.66f, 0.80f),
        ),
        "xlr" to listOf(
            c(0.5f, 0.5f, 0.30f),
            c(0.40f, 0.40f, 0.05f, fill = true), c(0.60f, 0.40f, 0.05f, fill = true), c(0.5f, 0.62f, 0.05f, fill = true),
        ),
        "diffuseur" to listOf(
            c(0.42f, 0.5f, 0.18f),
            arc(0.42f, 0.5f, 0.30f, -55f, 110f),
            l(0.42f, 0.32f, 0.42f, 0.12f), c(0.42f, 0.10f, 0.04f, fill = true),
        ),

        // ---- Autre
        "autre" to listOf(
            r(0.18f, 0.18f, 0.82f, 0.82f),
            c(0.5f, 0.5f, 0.08f, fill = true),
        ),
    )

    /** Primitives du symbole demandé ; inconnu → « autre ». */
    fun glyph(id: String?): List<LsPrim> = glyphs[id] ?: glyphs.getValue("autre")

    /** Symbole d'un item du catalogue (son champ symbole, sinon « autre »). */
    fun symbolOf(item: LsItem?): String = item?.symbole?.takeIf { it in glyphs } ?: "autre"
}
