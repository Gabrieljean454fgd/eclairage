package app.lumisite.core

import app.lumisite.core.sync.Clock
import app.lumisite.core.sync.MasterMesh
import app.lumisite.core.sync.MeshLog
import app.lumisite.core.sync.MeshMsg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Retour terrain build 199, point 1c : le journal à l'écran montre l'incertitude (« aligné à ± 33,9 ms »),
 * jamais le décalage brut entre horloges monotones ; le brut reste dans `what` (synchro.ndjson).
 */
class SyncMeshScreenTest {
    private fun feedPongs(m: MasterMesh, id: String, n: Int): List<MeshLog> {
        val logs = mutableListOf<MeshLog>()
        repeat(n) { i ->
            logs += m.onMessage(id, MeshMsg.Pong(i.toLong(), i * 1000L, i * 1000L + 10, i * 1000L + 20), i * 1000L + 30).first
        }
        return logs
    }

    @Test fun alignedTransitionKeepsRawOffsetForFileAndReadableTextForScreen() {
        val m = MasterMesh("maitre")
        m.onConnecting("e1", "E1", 0); m.onConnected("e1", 0)
        val logs = feedPongs(m, "e1", 3)
        assertEquals(Clock.ALIGNED, m.peers.getValue("e1").clock)
        val align = logs.last { it.what.startsWith("alignement") }
        // synchro.ndjson : la valeur brute est conservée pour l'analyse.
        assertTrue(align.what.contains("décalage"), align.what)
        // Écran : incertitude seulement, pas de « décalage ».
        val screen = assertNotNull(align.screen)
        assertTrue(screen.startsWith("alignement : aligné à ±"), screen)
        assertTrue(!screen.contains("décalage"), screen)
    }

    @Test fun nonAlignedTransitionsHaveNoScreenOverride() {
        val m = MasterMesh("maitre")
        m.onConnecting("e1", "E1", 0); m.onConnected("e1", 0)
        val logs = feedPongs(m, "e1", 1)
        assertEquals(Clock.ALIGNING, m.peers.getValue("e1").clock)
        val align = logs.last { it.what.startsWith("alignement") }
        assertNull(align.screen)
    }
}
