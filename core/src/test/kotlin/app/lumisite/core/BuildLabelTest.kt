package app.lumisite.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildLabelTest {
    @Test fun formatsBuildAndPackage() {
        assertEquals("Build 210 · v176g", BuildLabel.format(210, "v176g"))
        assertEquals("Build 210", BuildLabel.format(210, ""))
        assertEquals("Build 210", BuildLabel.format(210, null))
    }

    @Test fun packageComesFromTheBranchName() {
        assertEquals("v176g", BuildLabel.paquetFromBranch("etape-3-v176g-retours-build199"))
        assertEquals("v176e", BuildLabel.paquetFromBranch("etape-3-v176e-menu-bloc"))
        assertEquals("v176a-bis", BuildLabel.paquetFromBranch("etape-3-v176a-bis-rectangles"))
        assertEquals("", BuildLabel.paquetFromBranch("main"))
        assertEquals("", BuildLabel.paquetFromBranch("etape-3-paquet2"))
    }
}
