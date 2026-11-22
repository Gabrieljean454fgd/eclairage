package app.lumisite.core

import app.lumisite.core.export.ExportPackage
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.sync.SynchroArchive
import app.lumisite.core.sync.SynchroHeader
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests d'acceptation verrouillés — sauvegarde et export de la synchro sur l'esclave (build 247).
 * Ne pas modifier ce fichier : le verdict du relecteur s'appuie sur ces assertions.
 */
class SynchroArchiveTest {
    private fun tmp(): File = Files.createTempDirectory("lumisync").toFile()

    private fun esclave(dir: File, started: Long = 1_700_000_000_000) = SynchroArchive.create(
        dir,
        SynchroHeader(
            role = SynchroHeader.ROLE_ESCLAVE,
            startedUtcMs = started,
            deviceName = "Xiaomi 13T Pro",
            deviceId = "dev-xiaomi",
        ),
    )

    @Test fun headerLinksSiteVisitMasterAndClockOffset() {
        val dir = File(tmp(), "20261122-203000")
        esclave(dir).use { a ->
            a.setMasterName("Pixel 7")
            a.bind(Fixtures.site.id, "ses_visite_1")
            a.setClockOffset(offsetNs = 12_500_000, uncertaintyNs = 800_000, timeBaseId = "tb-pixel")
            a.flush()
        }
        val h = SynchroArchive.open(dir)!!.header
        assertEquals(SynchroHeader.ROLE_ESCLAVE, h.role)
        assertEquals("Xiaomi 13T Pro", h.deviceName)
        assertEquals("dev-xiaomi", h.deviceId)
        assertEquals("Pixel 7", h.masterName)
        assertEquals(Fixtures.site.id, h.siteId)
        assertEquals("ses_visite_1", h.visitId)
        assertEquals(12_500_000, h.clockOffsetNs)
        assertEquals(800_000, h.clockUncertaintyNs)
        assertEquals("tb-pixel", h.timeBaseId)
        assertEquals(SynchroHeader.FORMAT, h.format)
    }

    @Test fun journalAndSensorsSurviveReopenAsIfAfterSleep() {
        val dir = File(tmp(), "20261122-203100")
        esclave(dir, started = 1_700_000_100_000).use { a ->
            a.setMasterName("Pixel 7")
            a.bind(Fixtures.site.id, "ses_visite_1")
            a.appendJournal("local", "rôle : Captation-synchro (esclave)", 10, 1_700_000_100_000)
            a.appendJournal("ep1", "maître trouvé : Pixel 7", 20, 1_700_000_100_100)
            a.appendSensor(11, "ACCEL", 0.1, 0.2, 9.8)
            a.appendSensor(12, "GPS", 43.604, 1.444, 4.5)
            a.appendSensor(13, "DEPTH", 1.25, 0.0, 0.0)
            a.appendSensor(14, "MESURE", 2.04, 0.0, 0.0)
            a.flush()
        }
        // Processus tué / mise en veille : on rouvre le même dossier, sans perdre une ligne, et on peut continuer.
        SynchroArchive.open(dir)!!.use { a ->
            val journal = a.journalLines()
            assertTrue(journal.any { it.contains("Captation-synchro") })
            assertTrue(journal.any { it.contains("maître trouvé : Pixel 7") })
            val sensors = a.sensorLines()
            assertEquals("t_mono_ns,capteur,x,y,z", sensors.first())
            assertTrue(sensors.any { it.startsWith("12,GPS,43.604,1.444,4.5") })
            assertTrue(sensors.any { it.startsWith("13,DEPTH,1.25") })
            assertTrue(sensors.any { it.startsWith("14,MESURE,2.04") })
            assertEquals(Fixtures.site.id, a.header.siteId)
            assertEquals("Pixel 7", a.header.masterName)
            a.appendJournal("local", "reprise après veille", 30, 1_700_000_200_000)
            a.appendSensor(15, "GPS", 43.605, 1.445, 3.0)
            a.flush()
        }
        SynchroArchive.open(dir)!!.use { a ->
            assertEquals(3, a.journalLines().size)
            assertTrue(a.journalLines().last().contains("reprise après veille"))
            assertTrue(a.sensorLines().any { it.startsWith("15,GPS,43.605") })
        }
        val root = dir.parentFile
        val resumable = SynchroArchive.resumableDirs(root, nowUtcMs = 1_700_000_200_000)
        assertEquals(listOf(dir.canonicalFile), resumable.map { it.canonicalFile })
        SynchroArchive.open(dir)!!.use { it.markStopped(); it.flush() }
        assertTrue(SynchroArchive.isStopped(dir))
        assertTrue(SynchroArchive.resumableDirs(root, nowUtcMs = 1_700_000_200_000).isEmpty())
    }

    @Test fun flushMakesWritesDurableWithoutClose() {
        val dir = File(tmp(), "20261122-203200")
        val a = esclave(dir)
        a.appendJournal("local", "capteurs : GPS actif", 1, 1_700_000_000_001)
        a.appendSensor(2, "GPS", 48.8, 2.3, 8.0)
        a.flush()
        // Lecture externe, comme un autre processus, sans fermer l'archive encore ouverte.
        val journal = File(dir, SynchroArchive.JOURNAL).readText()
        val sensors = File(dir, SynchroArchive.SENSORS).readText()
        assertTrue(journal.contains("capteurs : GPS actif"))
        assertTrue(sensors.contains("2,GPS,48.8,2.3,8.0"))
        a.close()
    }

    @Test fun slaveExportZipUsesSitePackageLayoutAndSeparatesVisits() {
        val root = tmp()
        val visit1 = File(root, "synchro/20261122-100000").apply { mkdirs() }
        val visit2 = File(root, "synchro/20261122-150000").apply { mkdirs() }
        esclave(visit1, started = 1_700_000_000_000).use {
            it.bind(Fixtures.site.id, "ses_matin"); it.setMasterName("Pixel 7")
            it.appendJournal("local", "visite du matin", 1, 1_700_000_000_000)
            it.appendSensor(2, "GPS", 43.6, 1.44, 5.0)
            it.flush()
        }
        esclave(visit2, started = 1_700_000_500_000).use {
            it.bind(Fixtures.site.id, "ses_apresmidi"); it.setMasterName("Pixel 7")
            it.appendJournal("local", "visite de l'après-midi", 1, 1_700_000_500_000)
            it.flush()
        }
        val out = File(root, "export")
        val pkg = ExportPackage.build(
            Fixtures.site, emptyList(), null, null, out, 1_700_000_900_000, "test",
            synchroArchives = listOf(visit1, visit2),
        )
        assertTrue(ExportPackage.verify(pkg).isEmpty(), ExportPackage.verify(pkg).toString())
        val v1 = File(pkg, "synchro/dev-xiaomi/20261122-100000/${SynchroArchive.HEADER}")
        val v2 = File(pkg, "synchro/dev-xiaomi/20261122-150000/${SynchroArchive.JOURNAL}")
        assertTrue(v1.isFile, "visite 1 absente du paquet : ${pkg.walkTopDown().filter { it.isFile }.map { it.relativeTo(pkg) }.toList()}")
        assertTrue(v2.isFile)
        assertTrue(File(pkg, "synchro/dev-xiaomi/20261122-100000/${SynchroArchive.SENSORS}").readText().contains("GPS"))
        assertTrue(File(pkg, "synchro/dev-xiaomi/20261122-150000/${SynchroArchive.JOURNAL}").readText().contains("après-midi"))
        val zip = File(root, "LumiSite_${Fixtures.site.id}_slave.zip")
        ExportPackage.zipStored(pkg, zip)
        ZipFile(zip).use { z ->
            val names = z.entries().toList().map { it.name }
            assertTrue(names.any { it.endsWith("manifest.json") }, names.toString())
            assertTrue(names.any { it.contains("LumiSite_${Fixtures.site.id}/") }, names.toString())
            assertTrue(names.any { it.contains("synchro/dev-xiaomi/20261122-100000/${SynchroArchive.HEADER}") }, names.toString())
            assertTrue(names.any { it.contains("synchro/dev-xiaomi/20261122-150000/${SynchroArchive.JOURNAL}") }, names.toString())
        }
    }

    @Test fun siteExportIncludesEveryBoundSlaveArchive() {
        val root = tmp()
        val sessionDir = File(root, "sessions/ses_visite_1").apply { mkdirs() }
        SessionJournal(sessionDir, Fixtures.header).use { it.event(SessionStarted("st", Fixtures.stamp(500))) }
        // Archive déjà stagée dans la session (export de session / stageSynchroInto).
        val staged = File(sessionDir, "synchro/20261122-100000").apply { mkdirs() }
        esclave(staged).use {
            it.bind(Fixtures.site.id, "ses_visite_1"); it.setMasterName("Pixel 7")
            it.appendJournal("local", "stagée dans la session", 1, 1_700_000_000_000)
            it.flush()
        }
        // Archive encore seulement sous files/synchro, liée au même site (export global du lieu).
        val loose = File(root, "files/synchro/20261122-180000").apply { mkdirs() }
        esclave(loose, started = 1_700_000_800_000).use {
            it.bind(Fixtures.site.id, "ses_visite_1"); it.setMasterName("Pixel 7")
            it.appendJournal("local", "archive libre liée au site", 1, 1_700_000_800_000)
            it.flush()
        }
        // Archive d'un autre site : ne doit pas apparaître si on ne la passe pas.
        val other = File(root, "files/synchro/other").apply { mkdirs() }
        esclave(other, started = 1_700_000_000_000).use {
            it.bind("site_autre", "ses_x"); it.flush()
        }
        val pkg = ExportPackage.build(
            Fixtures.site,
            listOf(ExportPackage.SessionInput(sessionDir)),
            null, null, File(root, "pkg"), 1_700_000_900_000, "test",
            synchroArchives = listOf(loose),
        )
        val files = pkg.walkTopDown().filter { it.isFile }.map { it.relativeTo(pkg).invariantSeparatorsPath }.toList()
        assertTrue(files.any { it.startsWith("synchro/dev-xiaomi/20261122-100000/") }, files.toString())
        assertTrue(files.any { it.startsWith("synchro/dev-xiaomi/20261122-180000/") }, files.toString())
        assertFalse(files.any { it.contains("/other/") }, files.toString())
        assertTrue(ExportPackage.verify(pkg).isEmpty(), ExportPackage.verify(pkg).toString())
    }

    @Test fun unboundArchiveIsStillExportable() {
        val dir = File(tmp(), "20261122-204000")
        esclave(dir).use {
            it.setMasterName("Pixel 7")
            it.appendJournal("local", "pas encore de site", 1, 1_700_000_000_000)
            it.flush()
            assertNull(it.header.siteId)
        }
        assertNotNull(SynchroArchive.open(dir))
        assertEquals("Pixel 7", SynchroArchive.open(dir)!!.header.masterName)
    }
}
