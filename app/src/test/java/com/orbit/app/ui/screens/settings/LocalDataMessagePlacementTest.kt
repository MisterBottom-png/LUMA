package com.orbit.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Each message shows next to the button it is about. */
class LocalDataMessagePlacementTest {
    @Test
    fun restoreErrorsShowByRestoreAndExportErrorsByExport() {
        assertEquals(LocalDataArea.Restore, LocalDataToolsMessage.RestoreFileInvalid.area())
        assertEquals(LocalDataArea.Restore, LocalDataToolsMessage.RestoreFailed.area())
        assertEquals(LocalDataArea.Restore, LocalDataToolsMessage.ReminderSetupStillFailing.area())
        assertEquals(LocalDataArea.Export, LocalDataToolsMessage.ExportFailed.area())
        assertEquals(LocalDataArea.Export, LocalDataToolsMessage.ExportUnverified.area())
        assertEquals(LocalDataArea.Reset, LocalDataToolsMessage.ResetFailed.area())
    }

    @Test
    fun theExportFileCarriesTheAppName() {
        assertEquals("tallele-export.json", ExportFileName)
    }
}
