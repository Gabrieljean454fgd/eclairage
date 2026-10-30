package app.lumisite.core

import app.lumisite.core.compute.Calibrator
import app.lumisite.core.compute.Relocalizer
import app.lumisite.core.compute.SurveyPositions
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Enu
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SurveyPositionsTest {
    private val s = 1_000_000_000L

    private fun survey(id: String, mono: Long, x: Double, y: Double, manual: Enu? = null, supersedes: String? = null, deleted: Boolean? = null) = SurveyEvent(
        id, Fixtures.stamp(mono), "spot", pose = if (manual == null) Fixtures.pose(mono, x, y) else null, tracking = TrackingState.TRACKING,
        positionSource = if (manual != null) "MANUAL_MAP" else "AR_POSE", manualSite = manual, supersedes = supersedes, deleted = deleted,
    )

    private fun calibrated(): MutableList<SessionEvent> {
        val ref = ReferencePoint("P1", Enu(10.0, 20.0, 0.0), null, "MAP_TAP")
        return mutableListOf(Calibrator.onePointHeading("cal", Fixtures.stamp(1 * s), LocalObservation(Fixtures.pose(1 * s, 0.0, 0.0), ref), 0.0, "MANUAL", null))
    }

    @Test fun withoutCalibrationThereIsNoPosition() {
        val r = SurveyPositions.resolveAll(listOf(survey("a", 2 * s, 1.0, 1.0)))
        assertNull(r.single().site)
    }

    @Test fun withCalibrationThePoseIsTransformed() {
        val ev = calibrated() + survey("a", 2 * s, 0.0, 5.0)
        val p = assertNotNull(SurveyPositions.resolveAll(ev).single().site)
        assertEquals(10.0, p.e, 1e-6); assertEquals(25.0, p.n, 1e-6)
    }

    @Test fun editAfterRelocalizationKeepsThePosition() {
        val ev = calibrated()
        val before = survey("before", 2 * s, 0.0, 5.0)
        ev += before
        val seg = TrajectoryCorrector.activeAt(TrajectoryCorrector.timeline(ev), 3 * s)!!
        ev += Relocalizer.relocalize("rel", Fixtures.stamp(3 * s), Fixtures.pose(3 * s, 0.0, 10.0), ReferencePoint("R", Enu(12.0, 30.0, 0.0), null, "MAP_TAP"), seg.transform, null, null)
        ev += before.copy(id = "edit", t = Fixtures.stamp(5 * s), poseT = before.t, supersedes = "before", note = "commentaire")
        val p = SurveyPositions.resolveAll(ev).single()
        assertEquals("edit", p.event.id)
        assertEquals(10.0, p.site!!.e, 1e-6); assertEquals(25.0, p.site!!.n, 1e-6)
        assertEquals("cal", p.transformEventId)
    }

    @Test fun aimedPointIsUsedNotThePhonePosition() {
        val aimed = survey("a", 2 * s, 0.0, 5.0).copy(aimSession = listOf(2.0, -1.4, -3.0), positionSource = "AR_PLANE")
        val ev = calibrated() + aimed
        val seg = TrajectoryCorrector.activeAt(TrajectoryCorrector.timeline(ev), 2 * s)!!
        val want = app.lumisite.core.compute.Aim.toSite(app.lumisite.core.compute.V3(2.0, -1.4, -3.0), seg.transform)
        val p = assertNotNull(SurveyPositions.resolveAll(ev).single().site)
        assertEquals(want.e, p.e, 1e-9); assertEquals(want.n, p.n, 1e-9); assertEquals(-1.4, p.u, 1e-9)
        kotlin.test.assertTrue(kotlin.math.hypot(p.e - 10.0, p.n - 25.0) > 1.0)
        // Same point in the exported Feature (local metres of the site frame).
        val f = app.lumisite.core.export.GeoJsonFeatures.lines(ev, app.lumisite.core.geo.LocalTangentPlane(Fixtures.site.origin), "dev-1", "sess-1").single()
        val local = f["properties"]!!.let { it as kotlinx.serialization.json.JsonObject }["local"] as kotlinx.serialization.json.JsonObject
        assertEquals(want.e, (local["x"] as kotlinx.serialization.json.JsonPrimitive).content.toDouble(), 1e-3)
        assertEquals(want.n, (local["y"] as kotlinx.serialization.json.JsonPrimitive).content.toDouble(), 1e-3)
    }

    @Test fun relocalizationOnlyMovesLaterRecords() {
        val ev = calibrated()
        ev += survey("before", 2 * s, 0.0, 5.0)
        val seg = TrajectoryCorrector.activeAt(TrajectoryCorrector.timeline(ev), 3 * s)!!
        ev += Relocalizer.relocalize("rel", Fixtures.stamp(3 * s), Fixtures.pose(3 * s, 0.0, 10.0), ReferencePoint("R", Enu(12.0, 30.0, 0.0), null, "MAP_TAP"), seg.transform, null, null)
        ev += survey("after", 4 * s, 0.0, 10.0)
        val r = SurveyPositions.resolveAll(ev).associateBy { it.event.id }
        assertEquals(10.0, r.getValue("before").site!!.e, 1e-6)
        assertEquals(25.0, r.getValue("before").site!!.n, 1e-6)
        assertEquals(12.0, r.getValue("after").site!!.e, 1e-6)
        assertEquals(30.0, r.getValue("after").site!!.n, 1e-6)
        assertEquals("rel", r.getValue("after").transformEventId)
    }

    @Test fun editsAndDeletesAreAppendOnly() {
        val ev = calibrated()
        ev += survey("a", 2 * s, 0.0, 5.0)
        ev += survey("b", 3 * s, 0.0, 6.0)
        ev += survey("a2", 4 * s, 0.0, 0.0, manual = Enu(1.0, 2.0, 0.0), supersedes = "a")
        ev += survey("b-del", 5 * s, 0.0, 0.0, manual = Enu(0.0, 0.0, 0.0), supersedes = "b", deleted = true)
        val r = SurveyPositions.resolveAll(ev)
        assertEquals(listOf("a2"), r.map { it.event.id })
        assertEquals(Enu(1.0, 2.0, 0.0), r.single().site)
        assertEquals("a", SurveyPositions.rootId(ev, "a2"))
    }

    @Test fun legacyV01SurveyWithoutOptionalFieldsIsReadable() {
        val line = """{"type":"survey","id":"srv-1","t":{"utcEpochMs":1,"monotonicNs":2,"timeBaseId":"tb"},"kind":"LUMINAIRE","tracking":"TRACKING",""" +
            """"estimatedSite":{"e":1.5,"n":2.5,"u":0.0},"positionSource":"AR_POSE","dxf":{"layer":"LUMI_LUMINAIRE","blockName":"LUMI_LUMINAIRE_GENERIC"}}"""
        val e = LumiJson.decodeFromString(SessionEvent.serializer(), line) as SurveyEvent
        assertNull(e.supersedes); assertNull(e.deleted)
        assertEquals(Enu(1.5, 2.5, 0.0), SurveyPositions.resolveAll(listOf(e)).single().site)
    }
}
