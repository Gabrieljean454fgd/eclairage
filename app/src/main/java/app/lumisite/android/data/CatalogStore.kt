package app.lumisite.android.data

import android.content.Context
import app.lumisite.core.catalog.CatalogEntry
import app.lumisite.core.catalog.EquipmentCatalog
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Equipment catalog shipped as data (assets/lumisite_catalog_2.json, from catalog/ at the repo root), plus the user's local
 * types LS_PERSO_n (« Ajouter un type… ») kept apart in files/catalog/types_perso.json: the shipped asset is never modified.
 */
object CatalogStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(CatalogEntry.serializer())
    @Volatile private var shipped: EquipmentCatalog? = null
    @Volatile private var merged: EquipmentCatalog? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "catalog/types_perso.json")

    fun shipped(ctx: Context): EquipmentCatalog = shipped ?: synchronized(this) {
        shipped ?: EquipmentCatalog.parse(ctx.assets.open(EquipmentCatalog.ASSET).bufferedReader().use { it.readText() }).also { shipped = it }
    }

    fun custom(ctx: Context): List<CatalogEntry> = file(ctx).takeIf { it.exists() }
        ?.let { f -> runCatching { json.decodeFromString(ser, f.readText()) }.getOrElse { emptyList() } }.orEmpty()

    fun get(ctx: Context): EquipmentCatalog = merged ?: synchronized(this) {
        merged ?: shipped(ctx).withCustom(custom(ctx)).also { merged = it }
    }

    /** Adds a local type to [famille]; throws IllegalArgumentException with a message to show if refused. */
    fun addCustom(ctx: Context, famille: String, label: String): CatalogEntry = synchronized(this) {
        val e = EquipmentCatalog.customType(get(ctx), famille, label)
        val f = file(ctx).apply { parentFile?.mkdirs() }
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(ser, custom(ctx) + e))
        check(tmp.renameTo(f)) { "Type non enregistré" }
        merged = null
        e
    }
}
