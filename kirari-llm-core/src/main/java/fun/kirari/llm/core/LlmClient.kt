package `fun`.kirari.llm.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json

class LlmClient(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val logger: LlmLogger = NoopLlmLogger,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val tag = "KirariLlmClient"

    suspend fun stream(request: StreamRequest): Flow<LlmEvent> {
        logger.i(
            tag,
            "stream provider=${request.provider.kind} model=${request.model} imageCount=${request.imagesBase64.size} hasTools=${request.tools != null} trustAllHttps=${request.trustAllHttpsCertificates}"
        )
        val sseClient = SseStreamClient(clientProvider.client(request.trustAllHttpsCertificates), logger)
        val adapter = when (request.provider.kind) {
            ProviderKind.OPENAI_COMPATIBLE -> OpenAiChatAdapter(sseClient, json, logger)
            ProviderKind.OPENAI_RESPONSES -> OpenAiResponsesAdapter(sseClient, json, logger)
            ProviderKind.ANTHROPIC -> AnthropicAdapter(sseClient, json, logger)
            ProviderKind.GOOGLE -> GoogleAdapter(sseClient, json, logger)
        }
        // Payload construction/JSON encoding grows with every screenshot in a session.
        // Keep it off the UI collector while preserving the collector's own context.
        return adapter.stream(request).flowOn(Dispatchers.Default)
    }
}
