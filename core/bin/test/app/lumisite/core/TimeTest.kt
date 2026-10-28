package app.lumisite.core

import app.lumisite.core.time.Clock
import app.lumisite.core.time.Stamp
import app.lumisite.core.time.StampFactory
import app.lumisite.core.time.TimeMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeTest {
    @Test fun monotonicToUtcUsesReferencePair() {
        val tb = Fixtures.timeBase
        assertEquals(1_700_000_000_000.0, tb.monotonicToUtcMs(5_000_000_000))
        assertEquals(1_700_000_001_500.0, tb.monotonicToUtcMs(6_500_000_000))
    }

    @Test fun intervalUsesMonotonicNotUtc() {
        // UTC jumped backwards (NTP correction) but monotonic advanced by 2 s
        val a = Stamp(1_000_000, 10_000_000_000, "tb")
        val b = Stamp(999_000, 12_000_000_000, "tb")
        assertEquals(2_000_000_000, TimeMath.intervalNs(a, b))
    }

    @Test fun intervalAcrossTimeBasesIsRefused() {
        assertFailsWith<IllegalArgumentException> { TimeMath.intervalNs(Stamp(0, 0, "a"), Stamp(0, 1, "b")) }
    }

    @Test fun stampFactoryKeepsSensorTimestampUntouched() {
        val clock = object : Clock { override fun utcEpochMs() = 42L; override fun monotonicNs() = 7L }
        val s = StampFactory(clock, Fixtures.timeBase).now(sensorNs = 123_456_789_012)
        assertEquals(Stamp(42, 7, "tb-1", 123_456_789_012), s)
    }
}
