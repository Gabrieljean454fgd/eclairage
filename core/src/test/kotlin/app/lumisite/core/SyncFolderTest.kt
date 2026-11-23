package app.lumisite.core

import app.lumisite.core.export.ExportPackage
import app.lumisite.core.sync.MeshMsg
import app.lumisite.core.sync.SyncFolder
import app.lumisite.core.sync.SyncFolderMeta
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v176h: the slave's synchro run is tied to the site / visit, exportable alone and included in the site export. */
class SyncFolderTest {
    private fun tmp(): File = Files.createTempDirectory("lumisync").toFile()
    private fun run(root: File, name: String, siteId: String?): File = File(root, name).apply {
        SyncFolder.write(this, SyncFolderMeta(role = "esclave", device = "Xiaomi 13T Pro (6GES)", deviceId = "dev-x", startedUtcMs = 1,
            timeBaseId = "tb-1", masterName = "Pixel 7 (JSNK)", siteId = siteId, siteName = "Bureau", sessionId = "ses-1"))
        File(this, SyncFolder.JOURNAL).writeText("{\"t_mono_ns\":1,\"quoi\":\"rôle : esclave\"}\n")
        File(this, SyncFolder.GPS).writeText(SyncFolder.GPS_HEADER + "5,6,gps,43.6,1.44,4.0,,\n")
    }

    @Test fun runSpanningTwoVisitsStaysInBothSiteExportsAndClosingKeepsHistory() {
        val root = tmp()
        val dir = File(root, "20261123-130000")
        var m = SyncFolderMeta(role = "esclave", device = "X", deviceId = "d", startedUtcMs = 1)
            .linkedTo("site-a", "A", "ses-a", 10).linkedTo("site-a", "A", "ses-a", 11).linkedTo("site-b", "B", "ses-b", 20)
        assertEquals(listOf("ses-a", "ses-b"), m.sites.map { it.sessionId })
        m = m.linkedTo(null, null, null, 30)
        assertNull(m.siteId); assertEquals(2, m.sites.size)
        SyncFolder.write(dir, m)
        assertEquals(listOf(dir.name), SyncFolder.forSite(root, "site-a").map { it.name })
        assertEquals(listOf(dir.name), SyncFolder.forSite(root, "site-b").map { it.name })
        assertTrue(SyncFolder.forSite(root, "site-c").isEmpty())
        assertEquals("LumiSite_site-b", SyncFolder.buildPackage(listOf(dir), tmp(), 40, "t").name)
        // A finished visit reopened during the same run gets a new dated link.
        assertEquals(listOf(10L, 20L, 50L), m.linkedTo("site-b", "B", "ses-b", 50).sites.map { it.fromUtcMs })
    }

    @Test fun metaRoundTripAndSiteFilter() {
        val root = tmp()
        run(root, "20261123-100000", Fixtures.site.id); run(root, "20261123-110000", "autre"); run(root, "20261123-120000", null)
        assertEquals("Pixel 7 (JSNK)", SyncFolder.read(File(root, "20261123-100000"))!!.masterName)
        assertEquals(listOf("20261123-100000"), SyncFolder.forSite(root, Fixtures.site.id).map { it.name })
        assertEquals("20261123-120000", SyncFolder.runs(root).first().name)
        assertNull(SyncFolder.read(tmp()))
        assertTrue(SyncFolder.summary(File(root, "20261123-100000")).contains("gps.csv"))
    }

    @Test fun slavePackageHasManifestAndRunFiles() {
        val root = tmp(); val r = run(root, "20261123-100000", Fixtures.site.id)
        val pkg = SyncFolder.buildPackage(listOf(r), tmp(), 0, "test")
        assertEquals(emptyList(), ExportPackage.verify(pkg))
        val files = ExportPackage.readManifest(pkg).files.map { it.path }
        assertTrue("synchro/20261123-100000/gps.csv" in files && "synchro/20261123-100000/synchro.json" in files, files.toString())
    }

    @Test fun siteExportIncludesSynchroRuns() {
        val r = run(tmp(), "20261123-100000", Fixtures.site.id)
        val pkg = ExportPackage.build(Fixtures.site, emptyList(), null, null, tmp(), 0, "test", listOf(r))
        assertEquals(emptyList(), ExportPackage.verify(pkg))
        assertTrue(File(pkg, "synchro/20261123-100000/synchro.ndjson").exists())
    }

    @Test fun newMessagesRoundTrip() {
        val m = MeshMsg.SiteContext("Pixel 7 (JSNK)", Fixtures.site.id, "Bureau", "ses-1")
        assertEquals(m, MeshMsg.decode(MeshMsg.encode(m)))
        val c = MeshMsg.ClockInfo("top", 12_000_000, null, "r1")
        assertEquals(c, MeshMsg.decode(MeshMsg.encode(c)))
    }
}
