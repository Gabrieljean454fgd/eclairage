package app.lumisite.android.data

import android.content.Context
import app.lumisite.core.catalog.EquipmentCatalog
import app.lumisite.core.catalog.LsCatalog
import app.lumisite.core.catalog.LsItem
import kotlinx.serialization.json.Json
import java.io.File

/** Equipment catalog shipped as data (assets/equipment_catalog.json, from catalog/ at the repo root). */
object CatalogStore {
    @Volatile private var cached: EquipmentCatalog? = null
    @Volatile private var cachedLs: LsCatalog? = null

    fun get(ctx: Context): EquipmentCatalog = cached ?: synchronized(this) {
        cached ?: EquipmentCatalog.parse(ctx.assets.open("equipment_catalog.json").bufferedReader().use { it.readText() }).also { cached = it }
    }

    /** Catalogue 2.0 (assets/lumisite_catalog_2.json) + les types LS_PERSO_* de filesDir/catalogue_perso.json. */
    fun getLs(ctx: Context): LsCatalog = cachedLs ?: synchronized(this) {
        cachedLs ?: load(ctx).also { cachedLs = it }
    }

    private fun personalFile(ctx: Context) = File(ctx.filesDir, "catalogue_perso.json")

    private fun load(ctx: Context): LsCatalog {
        val base = LsCatalog.parse(ctx.assets.open("lumisite_catalog_2.json").bufferedReader().use { it.readText() })
        val perso = personalFile(ctx).takeIf { it.isFile }?.let {
            runCatching { LsCatalog.parse(it.readText()).items }.onFailure { e -> DiagLog.e("CATALOGUE", "catalogue_perso.json illisible : ${e.message}", e) }.getOrDefault(emptyList())
        }.orEmpty()
        return base.copy(items = base.items + perso).validated()
    }

    /** Renvoie le catalogue complet après ajout (appelé après [appendPersonal]). */
    fun reload(ctx: Context): LsCatalog = synchronized(this) { load(ctx).also { cachedLs = it } }

    /** « Ajouter un type… » : persiste le LS_PERSO_* dans filesDir/catalogue_perso.json (append). */
    fun appendPersonal(ctx: Context, item: LsItem) {
        val f = personalFile(ctx)
        val existing = f.takeIf { it.isFile }?.let { runCatching { LsCatalog.parse(it.readText()).items }.getOrDefault(emptyList()) }.orEmpty()
        val cat = LsCatalog("perso", existing + item).validated()
        f.writeText(Json { prettyPrint = true }.encodeToString(LsCatalog.serializer(), cat))
        reload(ctx)
    }
}
