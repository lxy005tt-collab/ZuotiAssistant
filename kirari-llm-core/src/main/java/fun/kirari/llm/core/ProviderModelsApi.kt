package `fun`.kirari.llm.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

data class RemoteModelOption(
    val id: String,
    val displayName: String = id,
)

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long = 0,
    val errorMessage: String = ""
)

data class ProviderCatalog(
    val models: List<RemoteModelOption>,
)

class ProviderModelsApi(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private companion object {
        const val TEST_TIMEOUT_MILLIS = 60_000L
        const val CATALOG_TIMEOUT_MILLIS = 60_000L
    }

    suspend fun getCatalog(
        provider: ProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ProviderCatalog = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(provider.modelsRequestUrl())
            .applyProviderHeaders(provider)
            .get()
            .build()

        clientProvider.clientWithTimeout(trustAllHttpsCertificates, CATALOG_TIMEOUT_MILLIS)
            .newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Failed to get models: ${response.code} ${response.body?.string()}")
            }

            val body = response.body?.string().orEmpty()
            return@withContext when (provider.kind) {
                ProviderKind.GOOGLE -> ProviderCatalog(models = parseGoogleModels(body))
                ProviderKind.OPENAI_COMPATIBLE,
                ProviderKind.OPENAI_RESPONSES,
                ProviderKind.ANTHROPIC -> ProviderCatalog(models = parseOpenAiLikeModels(body))
            }
        }
    }

    suspend fun listModels(
        provider: ProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): List<RemoteModelOption> = getCatalog(provider, trustAllHttpsCertificates).models

    suspend fun testConnection(
        provider: ProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        // 空 API Key 早返回，不发网络请求
        if (provider.apiKey.isBlank()) {
            return@withContext ConnectionTestResult(
                success = false,
                errorMessage = "请填写 API 密钥"
            )
        }
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(provider.connectionTestUrl())
                .applyProviderHeaders(provider)
                .get()
                .build()

            clientProvider.clientWithTimeout(trustAllHttpsCertificates, TEST_TIMEOUT_MILLIS)
                .newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val message = parseErrorMessage(body) ?: "HTTP ${response.code}"
                        return@withContext ConnectionTestResult(
                            success = false,
                            latencyMs = latency,
                            errorMessage = message
                        )
                    }
                    return@withContext ConnectionTestResult(success = true, latencyMs = latency)
                }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val message = when {
                e is java.net.UnknownHostException -> "无法解析主机名"
                e is java.net.ConnectException -> "连接被拒绝"
                e is java.net.SocketTimeoutException -> "连接超时"
                e is javax.net.ssl.SSLException -> "SSL 证书错误"
                else -> e.message ?: "未知错误"
            }
            return@withContext ConnectionTestResult(
                success = false,
                latencyMs = latency,
                errorMessage = message
            )
        }
    }

    private fun ProviderConfig.modelsRequestUrl(): String {
        val suffix = when (kind) {
            ProviderKind.OPENAI_COMPATIBLE -> "/models"
            ProviderKind.OPENAI_RESPONSES -> "/models"
            ProviderKind.ANTHROPIC -> "/models"
            ProviderKind.GOOGLE -> "/models?pageSize=100"
        }
        return "${baseUrl.trimEnd('/')}$suffix"
    }

    private fun ProviderConfig.connectionTestUrl(): String {
        val suffix = when (kind) {
            ProviderKind.GOOGLE -> "/models?pageSize=100"
            else -> "/models"
        }
        return "${baseUrl.trimEnd('/')}$suffix"
    }

    private fun parseOpenAiLikeModels(body: String): List<RemoteModelOption> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { modelJson ->
            val modelObject = modelJson.jsonObject
            val id = modelObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val displayName = modelObject["display_name"]?.jsonPrimitive?.contentOrNull
                ?: modelObject["name"]?.jsonPrimitive?.contentOrNull
                ?: id
            RemoteModelOption(id = id, displayName = displayName)
        }
    }

    private fun parseGoogleModels(body: String): List<RemoteModelOption> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["models"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { modelJson ->
            val modelObject = modelJson.jsonObject
            val id = modelObject["name"]?.jsonPrimitive?.contentOrNull?.substringAfterLast('/') ?: return@mapNotNull null
            val displayName = modelObject["displayName"]?.jsonPrimitive?.contentOrNull ?: id
            RemoteModelOption(id = id, displayName = displayName)
        }
    }

    private fun parseErrorMessage(body: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
}
