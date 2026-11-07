package `fun`.kirari.hanako.core.network

import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.llm.core.ProviderConfig

/** Projects saved provider settings into the shared model transport. */
internal class ProviderConfigResolver {
    fun resolve(provider: ModelProviderConfig): ProviderConfig = ProviderConfig(
        kind = provider.kind,
        baseUrl = provider.baseUrl,
        apiKey = provider.apiKey
    )
}
