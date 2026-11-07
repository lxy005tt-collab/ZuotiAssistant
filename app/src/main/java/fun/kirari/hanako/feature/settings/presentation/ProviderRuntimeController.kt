package `fun`.kirari.hanako.feature.settings.presentation

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.network.ProviderModelsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionTestStatus {
    IDLE, TESTING, SUCCESS, FAILED
}

data class ConnectionTestState(
    val status: ConnectionTestStatus = ConnectionTestStatus.IDLE,
    val latencyMs: Long = 0,
    val errorMessage: String = ""
)

internal class ProviderRuntimeController(
    private val scope: CoroutineScope,
    private val settings: StateFlow<AppSettings>,
    private val providerModelsApi: ProviderModelsApi
) {
    val connectionTestManager = ConnectionTestManager()
    private val connectionTestJobs = mutableMapOf<String, Job>()
    fun testProviderConnection(provider: ModelProviderConfig) {
        val providerId = provider.id
        connectionTestJobs[providerId]?.cancel()
        connectionTestManager.setState(providerId, ConnectionTestState(status = ConnectionTestStatus.TESTING))
        connectionTestJobs[providerId] = scope.launch {
            val trustAll = settings.value.trustAllHttpsCertificates
            val result = runCatching {
                providerModelsApi.testConnection(provider, trustAll)
            }
            if (!isActive) return@launch
            connectionTestManager.setState(
                providerId,
                result.fold(
                    onSuccess = { testResult ->
                        if (testResult.success) {
                            ConnectionTestState(
                                status = ConnectionTestStatus.SUCCESS,
                                latencyMs = testResult.latencyMs
                            )
                        } else {
                            ConnectionTestState(
                                status = ConnectionTestStatus.FAILED,
                                latencyMs = testResult.latencyMs,
                                errorMessage = testResult.errorMessage
                            )
                        }
                    },
                    onFailure = { error ->
                        val message = error.message ?: "连接测试失败"
                        ConnectionTestState(
                            status = ConnectionTestStatus.FAILED,
                            errorMessage = message
                        )
                    }
                )
            )
        }
    }

    fun resetConnectionTest(providerId: String) {
        connectionTestJobs[providerId]?.cancel()
        connectionTestJobs.remove(providerId)
        connectionTestManager.reset(providerId)
    }
}
