package app.lumisite.core

import app.lumisite.core.ui.SessionLayout
import app.lumisite.core.ui.SessionLayout.Camera
import app.lumisite.core.ui.SessionLayout.Tab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Retour terrain build 199, point 3 : carte utilisable en Annoter et Export (caméra repliée, panneau repliable). */
class SessionLayoutTest {

    @Test fun cameraCollapsedInAnnoterAndExportOnly() {
        assertEquals(Camera.BAND, SessionLayout.cameraFor(Tab.DRAW, fullMap = false))
        assertEquals(Camera.BAND, SessionLayout.cameraFor(Tab.EXPORT, fullMap = false))
        listOf(Tab.NONE, Tab.SYNC, Tab.CALIBRATE, Tab.SURVEY, Tab.RELOCALIZE).forEach {
            assertEquals(Camera.FULL, SessionLayout.cameraFor(it, fullMap = false), it.name)
        }
    }

    @Test fun fullMapHidesCameraEverywhere() {
        Tab.entries.forEach { assertEquals(Camera.HIDDEN, SessionLayout.cameraFor(it, fullMap = true), it.name) }
    }

    @Test fun annoterOpenPanelKeepsMapMajority() {
        val s = SessionLayout.resolve(Tab.DRAW, fullMap = false, panelCollapsed = false)
        assertEquals(Camera.BAND, s.camera)
        assertFalse(s.panelStrip)
        assertTrue(s.map / (s.map + s.panel) >= 0.6f, "carte = ${s.map}")
        assertTrue(s.panel > 0f)
    }

    @Test fun otherOpenPanelsKeepNormalCamera() {
        val s = SessionLayout.resolve(Tab.SURVEY, fullMap = false, panelCollapsed = false)
        assertEquals(Camera.FULL, s.camera)
        assertEquals(SessionLayout.MAP_SHARE_PANEL_NORMAL, s.map)
        assertEquals(1f - SessionLayout.MAP_SHARE_PANEL_NORMAL, s.panel)
    }

    @Test fun fullMapCollapsesPanelToStrip() {
        val s = SessionLayout.resolve(Tab.DRAW, fullMap = true, panelCollapsed = false)
        assertEquals(Camera.HIDDEN, s.camera)
        assertTrue(s.panelStrip)
        assertEquals(1f, s.map)
        assertEquals(0f, s.panel)
    }

    @Test fun fullMapWithoutOpenTabHidesTheStatusPanelToo() {
        val s = SessionLayout.resolve(Tab.NONE, fullMap = true, panelCollapsed = false)
        assertEquals(Camera.HIDDEN, s.camera)
        assertFalse(s.panelStrip)
        assertEquals(0f, s.panel)
    }

    @Test fun collapsedPanelGivesAllHeightToTheMap() {
        val s = SessionLayout.resolve(Tab.EXPORT, fullMap = false, panelCollapsed = true)
        assertTrue(s.panelStrip)
        assertEquals(1f, s.map)
        assertEquals(Camera.BAND, s.camera)
    }

    @Test fun collapsedPanelWithoutTabDisappears() {
        val s = SessionLayout.resolve(Tab.NONE, fullMap = false, panelCollapsed = true)
        assertFalse(s.panelStrip)
        assertEquals(0f, s.panel)
    }

    @Test fun landscapeMapWidensInFullMap() {
        assertTrue(SessionLayout.landscapeMapWeight(true) > SessionLayout.landscapeMapWeight(false))
    }

    @Test fun touchZoneAndBandsAreFieldSized() {
        assertTrue(SessionLayout.MAP_TOUCH_DP >= 48)
        assertTrue(SessionLayout.CAMERA_BAND_DP in 80..128)
        assertTrue(SessionLayout.PANEL_STRIP_DP >= 48)
    }
}
