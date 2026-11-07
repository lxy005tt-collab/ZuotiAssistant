package `fun`.kirari.hanako.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/** Remove the retired gateway before enum decoding so other saved settings remain readable. */
internal fun Json.decodeCompatibleAppSettings(raw: String): AppSettings {
    val root = parseToJsonElement(raw).jsonObject.toMutableMap()
    root.remove("kirari")
    val removedProviderIds = mutableSetOf("__kirari_network__")
    (root["providers"] as? JsonArray)?.let { providers ->
        root["providers"] = JsonArray(providers.filterNot { entry ->
            val provider = entry as? JsonObject ?: return@filterNot false
            val id = (provider["id"] as? JsonPrimitive)?.contentOrNull
            val kind = (provider["kind"] as? JsonPrimitive)?.contentOrNull
            val removed = id == "__kirari_network__" || kind == "KIRARI_NETWORK"
            if (removed && id != null) removedProviderIds += id
            removed
        })
    }
    if ((root["selectedProviderId"] as? JsonPrimitive)?.contentOrNull in removedProviderIds) {
        root["selectedProviderId"] = JsonNull
    }
    for (key in listOf("textModelSelection", "visionModelSelection", "ocrModelSelection")) {
        val selection = root[key] as? JsonObject ?: continue
        if ((selection["providerId"] as? JsonPrimitive)?.contentOrNull in removedProviderIds) {
            // Gateway model identifiers must not be sent to an unrelated custom provider.
            root[key] = JsonObject(emptyMap())
        }
    }
    return decodeFromJsonElement<AppSettings>(JsonObject(root)).normalize()
}
