package app.lumisite.core

import app.lumisite.core.export.ControlPointFeatures
import app.lumisite.core.export.ImuCsv
import app.lumisite.core.export.ResumableExport
import app.lumisite.core.export.SessionImporter
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.CalibrationEvent
import app.lumisite.core.model.CalibrationMethod
import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TimeBase
import app.lumisite.core.model.TimeBaseAdded
import app.lumisite.core.model.TrackingState
import app.lumisite.core.model.TrackingStateChanged
import app.lumisite.core.time.Stamp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v176f F2 (Session > Export): un seul ZIP, IMU en raw/imu.csv.gz, repères de calibrage dans le GeoJSON.
 * Fixture tests JVM: gzip round-trip, stable CSV columns/rounding, named control points with residual,
 * and the acceptance target (~30 min of capture < 20 Mo).
 */
class ExportZipTest {
    private val s = 1_000_000_000L

    // ------------------------------------------------------------------ ImuCsv (fonctions pures)

    private fun imu(monoNs: Long, type: Int = 1, values: List<Float> = listOf(0.1f, 9.8f, 0.0f), sensorNs: Long? = null, base: String = "tb-1") =
        ImuSample(Stamp(Fixtures.timeBase.monotonicToUtcMs(monoNs).toLong(), monoNs, base, sensorNs), type, "capteur", values, 3)

    @Test fun imuCsvColumnsRoundingAndTimeBasesAreStable() {
        val samples = listOf(
            imu(5 * s, 1, listOf(0.1f, 9.81f, -0.0f)),
            imu(5 * s + 10_000_000L, 4, listOf(0.123456789f, -0.0000004f, 1e-7f), sensorNs = 5 * s + 9_500_000L),
            imu(5 * s + 20_000_000L, 11, listOf(0f, 0f, 0f, 1f)),
            imu(5 * s + 30_000_000L, 6, listOf(1013.25f)),
            imu(5 * s + 40_000_000L, 2, listOf(1f, 2f, 3f), base = "tb-2"),
        )
        val csv = ImuCsv.render(samples)
        val lines = csv.lines()
        assertEquals("# ${ImuCsv.FORMAT_ID}", lines[0])
        assertEquals(ImuCsv.COLUMNS, lines[1])
        assertEquals("dt_us,sensor_dt_us,type,x,y,z,w", ImuCsv.COLUMNS)
        assertTrue(csv.contains("# time_base=tb-1 t_mono_ns_initial=${5 * s}"))
        assertTrue(csv.contains("# time_base=tb-2 t_mono_ns_initial=${5 * s + 40_000_000L}"))
        val rows = lines.filter(ImuCsv::isDataLine)
        assertEquals(5, rows.size)
        // dt relatifs à la base, capteur sur la même origine, type D9, arrondi 6 décimales stable.
        assertEquals("0,,imu.accel,0.1,9.81,0,", rows[0])
        assertEquals("10000,9500,imu.gyro,0.123457,0,0,", rows[1])
        assertEquals("20000,,imu.rotation,0,0,0,1", rows[2])
        assertEquals("30000,,imu.other.6,1013.25,,,", rows[3])
        // Nouvelle base de temps : dt_us repart de 0.
        assertEquals("0,,imu.mag,1,2,3,", rows[4])
    }

    @Test fun imuCsvWriterProducesTheSameBytesThroughGzip() {
        val samples = listOf(
            imu(5 * s), imu(5 * s + 10_000_000L, 4, listOf(0.2f, -0.3f, 0.4f), sensorNs = 5 * s + 9_000_000L),
            imu(6 * s, 15, listOf(0f, 0f, 0f, 1f), base = "tb-2"),
        )
        val bos = ByteArrayOutputStream()
        val w = ImuCsv.Writer(bos)
        samples.forEach(w::write)
        w.finish()
        // Le flux gzip se décompresse et le texte est identique au rendu direct.
        assertEquals(ImuCsv.render(samples), ImuCsv.ungzip(bos.toByteArray().inputStream()))
        // Session sans IMU : en-tête seul, gzip valide.
        val empty = ByteArrayOutputStream(); ImuCsv.Writer(empty).finish()
        assertEquals("# ${ImuCsv.FORMAT_ID}\n${ImuCsv.COLUMNS}\n", ImuCsv.ungzip(empty.toByteArray().inputStream()))
    }

    @Test fun imuCsvParseRestoresTimestampsTypesAndValues() {
        val tb2 = TimeBase("tb-2", "dev-1", TimeBase.ANDROID_ELAPSED_REALTIME, 1_700_000_100_000, 1_000_000_000)
        val samples = listOf(
            imu(5 * s, 1, listOf(0.1f, 9.8f, 0.0f)),
            imu(5 * s + 10_000_000L, 4, listOf(0.2f, -0.3f, 0.4f), sensorNs = 5 * s + 9_500_000L),
            imu(5 * s + 20_000_000L, 11, listOf(0f, 0f, 0f, 1f)),
            imu(5 * s + 30_000_000L, 6, listOf(1013.25f)),
            imu(5 * s + 40_000_000L, 2, listOf(1f, 2f, 3f), base = "tb-2"),
        )
        val back = ImuCsv.parse(ImuCsv.render(samples), mapOf("tb-1" to Fixtures.timeBase, "tb-2" to tb2))
        assertEquals(5, back.size)
        assertEquals(5 * s + 10_000_000L, back[1].t.monotonicNs)
        assertEquals(5 * s + 9_500_000L, back[1].t.sensorNs)
        assertEquals("tb-2", back[4].t.timeBaseId)
        assertEquals(listOf(1, 4, 11, 6, 2), back.map { it.sensorType })
        assertEquals(listOf(0.1f, 9.8f, 0.0f), back[0].values)
        assertEquals(listOf(0f, 0f, 0f, 1f), back[2].values)
        assertEquals(listOf(1013.25f), back[3].values)
    }

    // ------------------------------------------------------------------ repères → GeoJSON

    private fun repereEvents(): List<SessionEvent> {
        val events = ArrayList<SessionEvent>()
        events += SessionStarted("e0", Fixtures.stamp(5 * s))
        events += TrackingStateChanged("e1", Fixtures.stamp(5 * s + 1), TrackingState.TRACKING)
        // Calibrage de référence pour les relevés (transform identité : plan local = site).
        val p0 = Fixtures.pose(5 * s + 100_000_000L, 0.0, 0.0)
        events += CalibrationEvent("cal", p0.t, CalibrationMethod.MULTI_POINT,
            listOf(LocalObservation(p0, ReferencePoint("A", Enu(0.0, 0.0), source = "MAP_TAP"))),
            transform = Similarity2D.IDENTITY, scaleFixed = true, residualM = 0.05)
        // 4 repères de calibrage aux coins d'un carré 10 m : local == cible → résidu ~0.
        listOf(Enu(0.0, 0.0), Enu(10.0, 0.0), Enu(10.0, 10.0), Enu(0.0, 10.0)).forEachIndexed { i, t ->
            val p = Fixtures.pose(6 * s + i * 100_000_000L, t.e, t.n)
            events += ControlPointEvent("cp$i", p.t, "CALIBRATION",
                LocalObservation(p, ReferencePoint("Calibrage ${i + 1}", t, source = "MAP_TAP")))
        }
        // 5e point : contrôle indépendant (hors calcul).
        val p5 = Fixtures.pose(6 * s + 500_000_000L, 5.0, 5.0)
        events += ControlPointEvent("cp4", p5.t, "CALIBRATION",
            LocalObservation(p5, ReferencePoint("Contrôle", Enu(5.0, 5.0), source = "MAP_TAP")), independentControl = true)
        // Un luminaire posé au même endroit qu'un repère.
        val ps = Fixtures.pose(6 * s + 600_000_000L, 2.0, 3.0)
        events += SurveyEvent("s1", ps.t, "LUMINAIRE", "allée nord", ps, TrackingState.TRACKING, null, "AR_POSE")
        return events
    }

    private fun releves(events: List<SessionEvent>): List<JsonObject> =
        (app.lumisite.core.export.Releves.featureCollection(events, LocalTangentPlane(Fixtures.origin), "dev-1", "sess-1")["features"] as JsonArray)
            .map { it as JsonObject }

    @Test fun geoJsonContainsLuminaireAndNamedControlPoints() {
        val features = releves(repereEvents())
        // Le luminaire avec ses propriétés plates.
        val equip = features.single { (it["properties"] as JsonObject)["lumisite:kind"]?.jsonPrimitive?.contentOrNull == "equipment" }
        val ep = equip["properties"] as JsonObject
        assertEquals("allée nord", ep["name"]?.jsonPrimitive?.contentOrNull) // la note nomme le luminaire
        assertEquals("equipment", ep["lumisite:kind"]?.jsonPrimitive?.contentOrNull)
        assertTrue("lumisite:code" in ep)
        // Les 5 repères nommés.
        val cps = features.filter { (it["properties"] as JsonObject)["lumisite:kind"]?.jsonPrimitive?.contentOrNull == "control_point" }
        assertEquals(5, cps.size)
        val cp1 = cps.single { it["id"]!!.jsonPrimitive.content == "cp-cp0" }
        val p = cp1["properties"] as JsonObject
        assertEquals("Calibrage 1", p["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("CALIBRATION", p["lumisite:role"]?.jsonPrimitive?.contentOrNull)
        assertEquals("retenu", p["lumisite:statut"]?.jsonPrimitive?.contentOrNull)
        assertEquals("MAP_TAP", p["lumisite:methode"]?.jsonPrimitive?.contentOrNull)
        val residu = p["lumisite:residu_m"]!!.jsonPrimitive.content.toDouble()
        assertTrue(residu < 0.01, "repères exacts : résidu $residu m")
        assertEquals("bon", p["lumisite:qualite"]?.jsonPrimitive?.contentOrNull)
        // sigma_xy_m = résidu, accuracy.method = control_point.
        val acc = p["accuracy"] as JsonObject
        assertEquals(residu, acc["sigma_xy_m"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals("control_point", acc["method"]!!.jsonPrimitive.content)
        // Le contrôle indépendant : hors calcul, résidu propre.
        val ctl = cps.single { it["id"]!!.jsonPrimitive.content == "cp-cp4" }
        val cp = ctl["properties"] as JsonObject
        assertEquals("Contrôle", cp["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("contrôle", cp["lumisite:statut"]?.jsonPrimitive?.contentOrNull)
        assertTrue((cp["lumisite:residu_m"]?.jsonPrimitive?.contentOrNull?.toDouble() ?: 1.0) < 0.01)
        // JOSM/QGIS : point WGS84 + style de repère.
        val g = cp1["geometry"] as JsonObject
        assertEquals("Point", g["type"]!!.jsonPrimitive.content)
        val c = g["coordinates"] as JsonArray
        assertEquals(2, c.size)
        assertTrue(c[0].jsonPrimitive.content.toDouble() in 2.0..3.0 && c[1].jsonPrimitive.content.toDouble() in 48.0..49.0)
        assertEquals("flag", p["marker-symbol"]?.jsonPrimitive?.contentOrNull)
    }

    @Test fun repèresOfPreviousArSessionHaveNoResidual() {
        // Reprise : epoch 0 = 3 repères, puis TimeBaseAdded, epoch 1 = 3 repères.
        val tb2 = TimeBase("tb-2", "dev-1", TimeBase.ANDROID_ELAPSED_REALTIME, 1_700_000_100_000, 1_000_000_000)
        fun st2(monoNs: Long) = Stamp(tb2.monotonicToUtcMs(monoNs).toLong(), monoNs, tb2.id)
        val events = ArrayList<SessionEvent>()
        events += SessionStarted("e0", Fixtures.stamp(5 * s))
        listOf(Enu(0.0, 0.0), Enu(10.0, 0.0), Enu(10.0, 10.0)).forEachIndexed { i, t ->
            val p = Fixtures.pose(6 * s + i * 100_000_000L, t.e, t.n)
            events += ControlPointEvent("old$i", p.t, "CALIBRATION", LocalObservation(p, ReferencePoint("Ancien ${i + 1}", t, source = "MAP_TAP")))
        }
        events += TimeBaseAdded("tba", st2(100_000_000L), tb2, "REBOOT")
        listOf(Enu(0.0, 0.0), Enu(10.0, 0.0), Enu(10.0, 10.0)).forEachIndexed { i, t ->
            val p = Fixtures.pose(0, t.e, t.n).copy(t = st2(200_000_000L + i * 100_000_000L))
            events += ControlPointEvent("new$i", p.t, "CALIBRATION", LocalObservation(p, ReferencePoint("Nouveau ${i + 1}", t, source = "MAP_TAP")))
        }
        val cps = releves(events).filter { (it["properties"] as JsonObject)["lumisite:kind"]?.jsonPrimitive?.contentOrNull == "control_point" }
        assertEquals(6, cps.size)
        val old = cps.filter { it["id"]!!.jsonPrimitive.content.startsWith("cp-old") }
        assertEquals(3, old.size)
        old.forEach {
            val p = it["properties"] as JsonObject
            assertEquals("session précédente", p["lumisite:statut"]?.jsonPrimitive?.contentOrNull)
            assertEquals("inconnu", p["lumisite:qualite"]?.jsonPrimitive?.contentOrNull)
            assertFalse("lumisite:residu_m" in p, "pas de résidu pour une session AR précédente")
            assertTrue((p["accuracy"] as JsonObject)["sigma_xy_m"] is JsonNull)
        }
        val neu = cps.filter { it["id"]!!.jsonPrimitive.content.startsWith("cp-new") }
        assertTrue(neu.all { (it["properties"] as JsonObject)["lumisite:statut"]?.jsonPrimitive?.contentOrNull == "retenu" })
        assertTrue(neu.all { ((it["properties"] as JsonObject)["lumisite:residu_m"]?.jsonPrimitive?.contentOrNull?.toDouble() ?: 1.0) < 0.01 })
    }

    @Test fun qualityOfFollowsResidualThresholds() {
        assertEquals("inconnu", ControlPointFeatures.qualityOf(null))
        assertEquals("bon", ControlPointFeatures.qualityOf(0.0))
        assertEquals("bon", ControlPointFeatures.qualityOf(0.5))
        assertEquals("moyen", ControlPointFeatures.qualityOf(0.5001))
        assertEquals("moyen", ControlPointFeatures.qualityOf(1.0))
        assertEquals("insuffisant", ControlPointFeatures.qualityOf(1.0001))
    }

    // ------------------------------------------------------------------ export ZIP (intégration)

    private fun journalSession(imuSamples: Int = 300): java.io.File {
        val dir = Files.createTempDirectory("lumi").toFile()
        SessionJournal(dir, Fixtures.header).use { j ->
            j.event(SessionStarted("e0", Fixtures.stamp(5 * s)))
            j.event(TrackingStateChanged("e1", Fixtures.stamp(5 * s + 1), TrackingState.TRACKING))
            val poses = (0..20).map { Fixtures.pose(5 * s + 100_000_000L + it * 100_000_000L, it * 0.2, it * 0.5) }
            poses.forEach(j::pose)
            for (k in 0 until imuSamples) {
                val t = 5 * s + k * 10_000_000L
                j.imu(ImuSample(Fixtures.stamp(t), 1, "accel", listOf(0.1f, 9.8f, 0.0f), 3))
                if (k % 2 == 0) j.imu(ImuSample(Fixtures.stamp(t + 5_000_000L).copy(sensorNs = t + 4_000_000L), 4, "gyro", listOf(0.001f, -0.002f, 0.0005f), 3))
                if (k % 10 == 0) j.imu(ImuSample(Fixtures.stamp(t + 7_000_000L), 11, "rot", listOf(0f, 0f, 0f, 1f), 3))
                if (k % 50 == 0) j.imu(ImuSample(Fixtures.stamp(t + 8_000_000L), 6, "pressure", listOf(1013.25f), 3))
            }
            j.event(CalibrationEvent("cal", poses[0].t, CalibrationMethod.MULTI_POINT,
                listOf(LocalObservation(poses[0], ReferencePoint("A", Enu(0.0, 0.0), source = "MAP_TAP"))),
                transform = Similarity2D.IDENTITY, scaleFixed = true, residualM = 0.05))
            repereEvents().filterIsInstance<ControlPointEvent>().forEach { j.event(it) }
            j.event(SurveyEvent("s1", poses[10].t, "LUMINAIRE", "allée nord", poses[10], TrackingState.TRACKING, null, "AR_POSE"))
        }
        return dir
    }

    @Test fun sessionExportIsASingleZipWithGzippedImuCsv() {
        val dir = journalSession()
        val r = ResumableExport.run(Fixtures.site, dir, Files.createTempDirectory("exp").toFile(), "test", 1_700_000_000_000)
        // Un seul ZIP, vérifié de bout en bout.
        assertTrue(r.zip.isFile && r.zip.name.endsWith(".zip"))
        assertEquals(emptyList(), SessionImporter.verify(r.zip), "verify() accepte raw/imu.csv.gz")
        ZipFile(r.zip).use { zf ->
            val names = zf.entries().asSequence().map { it.name }.toList()
            assertTrue("raw/imu.csv.gz" in names)
            assertFalse("raw/imu.ndjson" in names, "plus de IMU en NDJSON dans l'export session")
            val fe = r.manifest.entries.single { it.name == "raw/imu.csv.gz" }
            assertEquals("ImuSample", fe.recordType)
            assertEquals("raw", fe.category)
            // Le gzip se décompresse : en-tête + une ligne CSV par échantillon enregistré.
            val text = ImuCsv.ungzip(zf.getInputStream(zf.getEntry("raw/imu.csv.gz")))
            assertTrue(text.startsWith("# ${ImuCsv.FORMAT_ID}\n${ImuCsv.COLUMNS}\n"))
            val rows = text.lines().filter(ImuCsv::isDataLine)
            // 300 accel + 150 gyro + 30 rotation + 6 pression.
            assertEquals(486, fe.records)
            assertEquals(486, rows.size)
            assertTrue(text.contains("imu.accel") && text.contains("imu.gyro") && text.contains("imu.rotation") && text.contains("imu.other.6"))
        }
        // Relecture : les IMU reviennent en ImuSample (capteur, valeurs, sensorNs à la précision µs).
        val loaded = SessionImporter.load(r.zip)
        assertEquals(emptyList(), loaded.problems, loaded.problems.toString())
        assertEquals(486, loaded.imu.size)
        assertEquals(5 * s + 5_000_000L, loaded.imu.first { it.sensorType == 4 }.t.monotonicNs)
        assertEquals(5 * s + 4_000_000L, loaded.imu.first { it.sensorType == 4 }.t.sensorNs)
        assertEquals(listOf(0.001f, -0.002f, 0.0005f), loaded.imu.first { it.sensorType == 4 }.values)
        assertEquals(6, loaded.imu.count { it.sensorType == 6 })
    }

    @Test fun exportOfThirtyMinutesStaysUnder20MB() {
        // ~30 min de capture terrain : accéléro + gyroscope 100 Hz, magnétomètre 10 Hz, rotation 5 Hz, poses 10 Hz.
        val dir = Files.createTempDirectory("lumi").toFile()
        SessionJournal(dir, Fixtures.header).use { j ->
            j.event(SessionStarted("e0", Fixtures.stamp(0)))
            j.event(TrackingStateChanged("e1", Fixtures.stamp(1_000_000), TrackingState.TRACKING))
            var i = 0L
            for (ms in 0L until 30L * 60_000 step 10L) {
                val t = ms * 1_000_000L
                j.imu(ImuSample(Fixtures.stamp(t), 1, "accel", listOf(0.1f, 9.8f + (i % 7) * 0.001f, 0.02f), 3))
                j.imu(ImuSample(Fixtures.stamp(t + 500_000L).copy(sensorNs = t + 480_000L), 4, "gyro", listOf(0.001f, -0.002f, 0.0005f), 3))
                if (i % 10 == 0L) j.imu(ImuSample(Fixtures.stamp(t + 700_000L), 2, "mag", listOf(19.5f, -42.1f, 36.8f), 3))
                if (i % 20 == 0L) j.imu(ImuSample(Fixtures.stamp(t + 800_000L), 11, "rot", listOf(0.01f, 0.02f, -0.01f, 0.9998f), 3))
                if (i % 100 == 0L) j.pose(Fixtures.pose(t + 900_000L, (i * 0.001) % 6.0, (i * 0.0007) % 4.0))
                i++
            }
            // Calibrage + repères + un luminaire : le GeoJSON doit contenir les deux.
            val p0 = Fixtures.pose(29L * 60_000_000_000L, 0.0, 0.0)
            j.event(CalibrationEvent("cal", p0.t, CalibrationMethod.MULTI_POINT,
                listOf(LocalObservation(p0, ReferencePoint("A", Enu(0.0, 0.0), source = "MAP_TAP"))),
                transform = Similarity2D.IDENTITY, scaleFixed = true, residualM = 0.05))
            repereEvents().filterIsInstance<ControlPointEvent>().forEach { j.event(it) }
            j.event(SurveyEvent("s1", p0.t, "LUMINAIRE", null, p0, TrackingState.TRACKING, null, "AR_POSE"))
        }
        val r = ResumableExport.run(Fixtures.site, dir, Files.createTempDirectory("exp").toFile(), "test", 1_700_000_000_000)
        assertTrue(r.zip.length() < 20L * 1024 * 1024, "export 30 min = ${r.zip.length() / 1_048_576} Mo ≥ 20 Mo")
        ZipFile(r.zip).use { zf ->
            val fc = LumiJson.decodeFromString(JsonObject.serializer(), zf.getInputStream(zf.getEntry("releves.geojson")).readBytes().decodeToString())
            val kinds = (fc["features"] as JsonArray).map { ((it as JsonObject)["properties"] as JsonObject)["lumisite:kind"]!!.jsonPrimitive.content }
            assertTrue("equipment" in kinds && "control_point" in kinds, "GeoJSON : luminaire + repères nommés — $kinds")
        }
        assertEquals(emptyList(), SessionImporter.verify(r.zip))
    }
}
