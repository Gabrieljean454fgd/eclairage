package app.lumisite.core

/** Version text shown on Accueil, in the pre-release APK name and in the reports: « Build 210 · v176g ». */
object BuildLabel {
    fun format(versionCode: Int, paquet: String?): String {
        val p = paquet?.trim().orEmpty()
        return if (p.isEmpty()) "Build $versionCode" else "Build $versionCode · $p"
    }

    /** Package name from a branch: etape-3-v176g-retours-build199 → v176g, etape-3-v176a-bis-x → v176a-bis, else "". */
    fun paquetFromBranch(branch: String): String =
        Regex("^etape-?[0-9]+-(v[0-9]+[a-z]*(-bis)?)-.*").find(branch.trim())?.groupValues?.get(1) ?: ""
}
