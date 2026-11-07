package `fun`.kirari.hanako.core.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FloatingWindowSettingsPersistenceTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun olderSettingsLoadNewDefaultsWithoutChangingCaptureOrLegacyPreferences() {
        val settings = json.decodeFromString<AppSettings>(
            """{"screenCaptureMethod":"SHIZUKU_ADB","automation":{"bubbleMenuEnabled":false},"trustAllHttpsCertificates":true}"""
        ).normalize()
        assertEquals(FloatingWindowSettings(), settings.floatingWindow)
        assertEquals(ScreenCaptureMethod.SHIZUKU_ADB, settings.screenCaptureMethod)
        assertFalse(settings.automation.bubbleMenuEnabled)
        assertEquals(true, settings.trustAllHttpsCertificates)
    }

    @Test
    fun editedDisplaySettingsSurvivePersistenceAlongsideCaptureChoice() {
        val display = FloatingWindowSettings(85, 70, 20, typewriterEnabled = false, closeOnOutsideTap = false)
        val settings = AppSettings(floatingWindow = display, screenCaptureMethod = ScreenCaptureMethod.SHIZUKU_ADB)
        val restored = json.decodeFromString<AppSettings>(json.encodeToString(settings)).normalize()
        assertEquals(display, restored.floatingWindow)
        assertEquals(ScreenCaptureMethod.SHIZUKU_ADB, restored.screenCaptureMethod)
    }

    @Test
    fun importedOutOfRangeDisplaySettingsCannotHideOrOversizeTheWindow() {
        val restored = json.decodeFromString<AppSettings>(
            """{"floatingWindow":{"sizePercent":0,"backgroundOpacityPercent":-1,"answerTextSizeSp":1000}}"""
        ).normalize()
        assertEquals(60, restored.floatingWindow.sizePercent)
        assertEquals(60, restored.floatingWindow.backgroundOpacityPercent)
        assertEquals(22, restored.floatingWindow.answerTextSizeSp)
    }
}
