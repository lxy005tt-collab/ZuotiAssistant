package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.data.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelIndependenceTest {
    @Test
    fun multimodalValidationWorksWithBothTextAndOcrLeftEmpty() {
        val pipeline = ProcessingPipeline()
        val settings = AppSettings(providers = listOf(ModelProviderConfig(id = "vision-only")),
            visionModelSelection = ModelSelection("vision-only", "chosen-vision")).normalize()
        val models = pipeline.resolveModels(settings)
        pipeline.validateVisionModels(models)
        assertEquals("chosen-vision", models.visionModel)
        assertNull(models.textProvider)
        assertNull(models.ocrProvider)
    }

    @Test(expected = IllegalStateException::class)
    fun ocrRouteCannotSilentlyUseTheVisionModelWhenTextIsEmpty() {
        val pipeline = ProcessingPipeline()
        val settings = AppSettings(providers = listOf(ModelProviderConfig(id = "vision-only")),
            visionModelSelection = ModelSelection("vision-only", "chosen-vision")).normalize()
        pipeline.validateOcrThenLlmModels(pipeline.resolveModels(settings))
    }
}
