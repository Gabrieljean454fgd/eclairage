package app.lumisite.android.data

import android.content.Context
import app.lumisite.core.catalog.EquipmentCatalog

/** Equipment catalog shipped as data (assets/equipment_catalog.json, from catalog/ at the repo root). */
object CatalogStore {
    @Volatile private var cached: EquipmentCatalog? = null

    fun get(ctx: Context): EquipmentCatalog = cached ?: synchronized(this) {
        cached ?: EquipmentCatalog.parse(ctx.assets.open("equipment_catalog.json").bufferedReader().use { it.readText() }).also { cached = it }
    }
}
