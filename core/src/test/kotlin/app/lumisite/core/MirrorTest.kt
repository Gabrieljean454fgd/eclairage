package app.lumisite.core

import app.lumisite.core.journal.Mirror
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.journal.Streams
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MirrorTest {
    @Test fun planAppendsNewBytesOnlyAndNeverTruncates() {
        val src = listOf(Mirror.Entry("s/events.ndjson", 300), Mirror.Entry("s/session_header.json", 50), Mirror.Entry("s/photo.jpg", 10))
        val dst = listOf(Mirror.Entry("s/events.ndjson", 120), Mirror.Entry("s/session_header.json", 40), Mirror.Entry("x/raw_poses.ndjson", 9))
        assertEquals(listOf(Mirror.Append("s/events.ndjson", 120), Mirror.Copy("s/session_header.json"), Mirror.Copy("s/photo.jpg")), Mirror.plan(src, dst))
        assertEquals(emptyList(), Mirror.plan(listOf(Mirror.Entry("a.ndjson", 5)), listOf(Mirror.Entry("a.ndjson", 9))))
    }

    /** Old private sessions are copied to the visible folder, then read back after a reinstall (private dir wiped). */
    @Test fun sessionSurvivesMirrorAndRestore() {
        val priv = Files.createTempDirectory("priv").toFile(); val visible = Files.createTempDirectory("docs").toFile()
        val sess = java.io.File(priv, "sessions/ses-1")
        SessionJournal(sess, Fixtures.header).use { j -> j.pose(Fixtures.pose(6_000_000_000, 1.0, 2.0)) }
        assertTrue(Mirror.sync(priv, visible) > 0)
        SessionJournal(sess).use { j -> j.pose(Fixtures.pose(7_000_000_000, 2.0, 2.0)) }
        Mirror.sync(priv, visible)
        assertEquals(java.io.File(sess, Streams.POSES).readText(), java.io.File(visible, "sessions/ses-1/${Streams.POSES}").readText())
        assertTrue(java.io.File(sess, Streams.HEADER).exists())
        val reinstalled = Files.createTempDirectory("priv2").toFile()
        Mirror.sync(visible, reinstalled)
        val back = SessionReader.read(java.io.File(reinstalled, "sessions/ses-1"))
        assertEquals(Fixtures.header.id, back.header.id); assertEquals(2, back.poses.size)
    }
}
