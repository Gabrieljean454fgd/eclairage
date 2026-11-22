package app.lumisite.core.ids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UuidV7Test {
    private val fixedEntropy = Entropy { 0x5A5A_5A5A_5A5A_5A5AL }
    private val t0 = 1_700_000_000_000L

    @Test fun generateSetsVersion7AndRfc4122Variant() {
        val u = UuidV7.generate(t0, fixedEntropy)
        assertEquals(36, u.length)
        assertEquals(u, u.lowercase())
        assertEquals('7', u[14]) // version nibble, first char of the third group
        assertTrue(u[19] in "89ab") // variant nibble, first char of the fourth group
        assertTrue(UuidV7.isUuidV7(u))
    }

    @Test fun timestampFollowsClockAndLexicographicOrderFollowsTime() {
        val a = UuidV7.generate(t0, fixedEntropy)
        val b = UuidV7.generate(t0 + 1, fixedEntropy)
        assertEquals(t0, UuidV7.timestampMsOrNull(a))
        assertEquals(t0 + 1, UuidV7.timestampMsOrNull(b))
        assertTrue(a < b)
    }

    @Test fun sameMillisecondIdsStayDistinct() {
        var draw = 0L
        val entropy = Entropy { draw++ }
        val a = UuidV7.generate(t0, entropy)
        val b = UuidV7.generate(t0, entropy)
        assertNotEquals(a, b)
        assertEquals(UuidV7.timestampMsOrNull(a), UuidV7.timestampMsOrNull(b))
    }

    @Test fun parseRejectsNonV7Strings() {
        val v7 = UuidV7.generate(t0, fixedEntropy)
        assertEquals(v7, UuidV7.parseOrNull(v7))
        // Version 4 and version 1 UUIDs are rejected.
        assertNull(UuidV7.parseOrNull("9f2c3d4e-5a1b-4c2d-8e3f-0a1b2c3d4e5f"))
        assertNull(UuidV7.parseOrNull("c3d4e5f6-5a1b-1c2d-8e3f-0a1b2c3d4e5f"))
        // Wrong variant (RFC 4122 requires 8/9/a/b).
        assertNull(UuidV7.parseOrNull("9f2c3d4e-5a1b-7c2d-0e3f-0a1b2c3d4e5f"))
        // Malformed: not an UUID at all, truncated, uppercase.
        assertNull(UuidV7.parseOrNull("pas-un-uuid"))
        assertNull(UuidV7.parseOrNull("9f2c3d4e5a1b2"))
        assertNull(UuidV7.parseOrNull(v7.uppercase()))
        assertFalse(UuidV7.isUuidV7(""))
    }

    @Test fun idsNewKeepsPrefixAndFullUuidV7() {
        val id = Ids.new("evt", t0, fixedEntropy)
        assertTrue(id.startsWith("evt-"))
        val uuid = id.removePrefix("evt-")
        assertTrue(UuidV7.isUuidV7(uuid))
        assertEquals(t0, UuidV7.timestampMsOrNull(uuid))
    }
}
