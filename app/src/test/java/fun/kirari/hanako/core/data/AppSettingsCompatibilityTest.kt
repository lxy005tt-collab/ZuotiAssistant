package `fun`.kirari.hanako.core.data

import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.llm.core.ReasoningEffort
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun removingGatewayPreservesCustomCredentialsChoicesAndHistory() {
        val settings = json.decodeCompatibleAppSettings("""
            {
              "providers": [
                {"id":"retired","kind":"KIRARI_NETWORK","apiKey":"retired-token"},
                {"id":"custom","kind":"OPENAI_COMPATIBLE","name":"Custom API",
                 "baseUrl":"https://example.test/v1","apiKey":"fixture-custom-key",
                 "favoriteModels":["custom-text","custom-vision"]}
              ],
              "selectedProviderId":"custom",
              "textModelSelection":{"providerId":"custom","model":"custom-text"},
              "visionModelSelection":{"providerId":"custom","model":"custom-vision"},
              "ocrModelSelection":{"providerId":"__local_mlkit__","model":"mlkit_chinese_ocr"},
              "processingRoute":"OCR_THEN_LLM",
              "reasoningEffort":"HIGH",
              "screenCaptureMethod":"SHIZUKU_ADB",
              "floatingWindow":{"sizePercent":75,"backgroundOpacityPercent":80},
              "kirari":{"auth":{"accessToken":"retired-token","refreshToken":"retired-refresh"}},
              "history":[{"id":"saved-answer","assistantName":"题目解答助手",
                "route":"MULTIMODAL_DIRECT","answer":"保留这条答案"}]
            }
        """.trimIndent())

        val provider = settings.providers.single()
        assertEquals("custom", provider.id)
        assertEquals("https://example.test/v1", provider.baseUrl)
        assertEquals("fixture-custom-key", provider.apiKey)
        assertEquals(listOf("custom-text", "custom-vision"), provider.favoriteModels)
        assertEquals("custom", settings.selectedProviderId)
        assertEquals(ModelSelection("custom", "custom-text"), settings.textModelSelection)
        assertEquals(ModelSelection("custom", "custom-vision"), settings.visionModelSelection)
        assertEquals(LOCAL_OCR_PROVIDER_ID, settings.ocrModelSelection.providerId)
        assertEquals(ProcessingRoute.OCR_THEN_LLM, settings.processingRoute)
        assertEquals(ReasoningEffort.HIGH, settings.reasoningEffort)
        assertEquals(ScreenCaptureMethod.SHIZUKU_ADB, settings.screenCaptureMethod)
        assertEquals(80, settings.floatingWindow.backgroundOpacityPercent)
        assertEquals("保留这条答案", settings.history.single().answer)
        val persisted = json.encodeToString(AppSettings.serializer(), settings)
        assertFalse(persisted.contains("\"kirari\""))
        assertFalse(persisted.contains("KIRARI_NETWORK"))
        assertFalse(persisted.contains("retired-token"))
        assertEquals(settings, json.decodeCompatibleAppSettings(persisted))
    }

    @Test
    fun retiredBuiltinSelectionsStayEmptyInsteadOfChoosingAnotherProvider() {
        val settings = json.decodeCompatibleAppSettings("""
            {
              "providers":[{"id":"custom","chatModel":"chat-new","visionModel":"vision-new"}],
              "selectedProviderId":"__kirari_network__",
              "textModelSelection":{"providerId":"__kirari_network__","model":"gateway-text"},
              "visionModelSelection":{"providerId":"__kirari_network__","model":"gateway-vision"},
              "ocrModelSelection":{"providerId":"__local_mlkit__","model":"mlkit_chinese_ocr"}
            }
        """.trimIndent())

        assertEquals("custom", settings.selectedProviderId)
        assertEquals(ModelSelection(), settings.textModelSelection)
        assertEquals(ModelSelection(), settings.visionModelSelection)
        assertEquals(LOCAL_OCR_PROVIDER_ID, settings.ocrModelSelection.providerId)
    }

    @Test
    fun gatewayOnlyConfigurationClearsStaleSelectionsWithoutInventingCredentials() {
        val settings = json.decodeCompatibleAppSettings("""
            {
              "providers":[{"id":"retired","kind":"KIRARI_NETWORK","apiKey":"retired-token"}],
              "selectedProviderId":"retired",
              "textModelSelection":{"providerId":"retired","model":"gateway-text"},
              "visionModelSelection":{"providerId":"retired","model":"gateway-vision"},
              "ocrModelSelection":{"providerId":"retired","model":"gateway-ocr"}
            }
        """.trimIndent())

        assertTrue(settings.availableProviders().isEmpty())
        assertNull(settings.selectedProviderId)
        assertEquals(ModelSelection(), settings.textModelSelection)
        assertEquals(ModelSelection(), settings.visionModelSelection)
        assertEquals(ModelSelection(), settings.ocrModelSelection)
    }

    @Test
    fun clearingOneModelSurvivesSavingReloadingAndDoesNotAffectOtherModels() {
        val original = AppSettings(
            providers = listOf(ModelProviderConfig(id = "custom", chatModel = "legacy-default")),
            textModelSelection = ModelSelection("custom", "text-chosen"),
            visionModelSelection = ModelSelection("custom", "vision-chosen"),
            ocrModelSelection = ModelSelection(LOCAL_OCR_PROVIDER_ID, LOCAL_OCR_MODEL_ID)
        ).normalize()
        val cleared = original.copy(textModelSelection = ModelSelection()).normalize()
        val loaded = json.decodeCompatibleAppSettings(json.encodeToString(AppSettings.serializer(), cleared))
        assertEquals(ModelSelection(), loaded.textModelSelection)
        assertEquals(original.visionModelSelection, loaded.visionModelSelection)
        assertEquals(original.ocrModelSelection, loaded.ocrModelSelection)
    }

    @Test
    fun newInstallDoesNotPreselectModelsAndRemovedProvidersDoNotRedirectRequests() {
        val fresh = json.decodeCompatibleAppSettings("{}")
        assertEquals(ModelSelection(), fresh.textModelSelection)
        assertEquals(ModelSelection(), fresh.visionModelSelection)
        assertEquals(ModelSelection(), fresh.ocrModelSelection)
        val removed = AppSettings(providers = listOf(ModelProviderConfig(id = "remaining")),
            visionModelSelection = ModelSelection("removed", "private-model")).normalize()
        assertEquals(ModelSelection(), removed.visionModelSelection)
    }
}
