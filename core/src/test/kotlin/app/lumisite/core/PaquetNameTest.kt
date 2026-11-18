package app.lumisite.core

import app.lumisite.core.version.PaquetName
import kotlin.test.Test
import kotlin.test.assertEquals

/** Retour terrain build 199, point 0 : nom de paquet de l'Accueil, déduit du nom de branche de build. */
class PaquetNameTest {
    @Test fun etapeVersionKeepsShortName() {
        assertEquals("v176g", PaquetName.from("etape-3-v176g-menus-stables"))
        assertEquals("v176d", PaquetName.from("etape-3-v176d"))
    }

    @Test fun etapeLocalKeepsSuffix() {
        assertEquals("local-xxx", PaquetName.from("etape-3-local-xxx"))
        assertEquals("local-tornado5-bis", PaquetName.from("etape-3-local-tornado5-bis"))
    }

    @Test fun absentOrBlankGivesDev() {
        assertEquals("dev", PaquetName.from(null))
        assertEquals("dev", PaquetName.from(""))
        assertEquals("dev", PaquetName.from("   "))
    }

    @Test fun otherNamesPassThrough() {
        assertEquals("main", PaquetName.from("main"))
        assertEquals("petit/inclinaison", PaquetName.from("petit/inclinaison"))
        assertEquals("dev", PaquetName.from("dev"))
    }

    @Test fun idempotent() {
        listOf("etape-3-v176g-x", "etape-3-local-y", "main", "dev", null).forEach {
            assertEquals(PaquetName.from(it), PaquetName.from(PaquetName.from(it)))
        }
    }
}
