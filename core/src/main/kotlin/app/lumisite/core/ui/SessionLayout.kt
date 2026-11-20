package app.lumisite.core.ui

/**
 * Retour terrain build 199 (17/11), point 3 : la carte doit rester utilisable en Annoter et Export.
 * En portrait, la caméra se replie en bande (ou disparaît en « Carte plein écran ») et le panneau
 * se replie en bandeau, pour que la carte garde au moins la moitié de l'écran. Fonctions pures
 * (SessionLayoutTest) : l'écran applique le résultat, la GLSurfaceView reste toujours composée.
 */
object SessionLayout {

    /** Onglet de la barre d'actions de l'écran Session (même ordre que Mode dans SessionScreen). */
    enum class Tab { NONE, SYNC, CALIBRATE, SURVEY, DRAW, RELOCALIZE, EXPORT }

    /** FULL : cadre caméra normal · BAND : bande de ~96 dp (composée, recadrée) · HIDDEN : composée mais taille 0. */
    enum class Camera { FULL, BAND, HIDDEN }

    data class Split(
        val camera: Camera,
        /** Part de la hauteur restante donnée à la carte (poids relatif). */
        val map: Float,
        /** Part de la hauteur restante donnée au panneau ; 0 = pas de panneau ouvert. */
        val panel: Float,
        /** Panneau réduit à un bandeau cliquable ; la carte prend tout le reste. */
        val panelStrip: Boolean,
    )

    /** Hauteur de la bande caméra repliée (Annoter / Export), en dp. */
    const val CAMERA_BAND_DP = 96
    /** Hauteur du bandeau « panneau replié », en dp. */
    const val PANEL_STRIP_DP = 48
    /** Zone de toucher minimale d'un objet sur la carte, en dp. */
    const val MAP_TOUCH_DP = 48

    /**
     * Part carte/(carte+panneau) quand un onglet est ouvert sur caméra repliée : avec la bande de 96 dp,
     * la ligne d'état et la barre d'actions (~200 dp de chrome), 0.66 laisse la carte ≥ 50 % de l'écran
     * sur les appareils de terrain (~800–900 dp de haut).
     */
    const val MAP_SHARE_MAP_FIRST = 0.66f
    /** Part quand un onglet est ouvert avec la caméra visible (les contrôles AR restent utiles). */
    const val MAP_SHARE_PANEL_NORMAL = 0.45f
    /** Part sans onglet ouvert (le panneau n'affiche que l'état/journal). */
    const val MAP_SHARE_IDLE = 0.6f
    /** Largeur de la carte en paysage, normale et en « Carte plein écran ». */
    const val MAP_SHARE_LANDSCAPE = 0.55f
    const val MAP_SHARE_LANDSCAPE_FULL = 0.72f

    /** Caméra repliée en bande dans Annoter et Export ; masquée seulement en « Carte plein écran ». */
    fun cameraFor(tab: Tab, fullMap: Boolean): Camera = when {
        fullMap -> Camera.HIDDEN
        tab == Tab.DRAW || tab == Tab.EXPORT -> Camera.BAND
        else -> Camera.FULL
    }

    /**
     * Partage carte/panneau (parts valables en portrait ; le paysage n'en garde que camera et panelStrip).
     * « Carte plein écran » ou panneau replié : la carte prend toute la hauteur restante et le panneau
     * ouvert est réduit à un bandeau (replié, rouvrable au toucher).
     */
    fun resolve(tab: Tab, fullMap: Boolean, panelCollapsed: Boolean): Split {
        val camera = cameraFor(tab, fullMap)
        if (fullMap || panelCollapsed) return Split(camera, 1f, 0f, panelStrip = tab != Tab.NONE)
        val map = when {
            tab == Tab.NONE -> MAP_SHARE_IDLE
            camera == Camera.BAND -> MAP_SHARE_MAP_FIRST
            else -> MAP_SHARE_PANEL_NORMAL
        }
        return Split(camera, map, 1f - map, panelStrip = false)
    }

    /** Largeur de la carte en paysage : élargie en « Carte plein écran ». */
    fun landscapeMapWeight(fullMap: Boolean): Float = if (fullMap) MAP_SHARE_LANDSCAPE_FULL else MAP_SHARE_LANDSCAPE
}
