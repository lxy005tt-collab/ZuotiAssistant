package `fun`.kirari.hanako.feature.overlay.window

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.feature.overlay.state.BubbleMenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleMenuRegistryTest {

    @Test
    fun entries_keepExpectedStableOrderAndItems() {
        assertEquals(
            listOf(
                BubbleMenuItem.ShowAnswer,
                BubbleMenuItem.SelectRegion,
                BubbleMenuItem.ToggleSearch,
                BubbleMenuItem.Settings
            ),
            BubbleMenuRegistry.entries.map { it.item }
        )
    }

    @Test
    fun regionEntry_isAnActionInBothProcessingModes() {
        val entry = BubbleMenuRegistry.entries.first { it.item == BubbleMenuItem.SelectRegion }
        for (route in ProcessingRoute.entries) {
            val settings = AppSettings(processingRoute = route)
            assertTrue(entry.isEnabled(settings))
            assertFalse(entry.isChecked(settings))
        }
    }

    @Test
    fun toggleSearchEntry_reflectsWebSearchState() {
        val entry = BubbleMenuRegistry.entries.first { it.item == BubbleMenuItem.ToggleSearch }

        assertFalse(entry.isChecked(AppSettings(webSearch = WebSearchSettings(enabled = false))))
        assertTrue(entry.isChecked(AppSettings(webSearch = WebSearchSettings(enabled = true))))
    }

    @Test
    fun settingsEntry_isAlwaysEnabledAndUnchecked() {
        val entry = BubbleMenuRegistry.entries.first { it.item == BubbleMenuItem.Settings }
        val settings = AppSettings()

        assertTrue(entry.isEnabled(settings))
        assertFalse(entry.isChecked(settings))
    }
}
