package `fun`.kirari.hanako.app

import android.content.Context
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.data.SettingsStore
import `fun`.kirari.hanako.platform.capture.ocr.LocalOcrManager
import `fun`.kirari.hanako.core.network.NetworkClientProvider
import `fun`.kirari.hanako.core.network.ProviderModelsApi
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.core.network.search.SearchClientImpl
import `fun`.kirari.hanako.core.network.search.SearchOrchestrator
import `fun`.kirari.hanako.core.network.search.TavilyUsageApi
import `fun`.kirari.hanako.core.network.update.AppUpdateApi
import `fun`.kirari.hanako.solve.runtime.WorkflowContainer

internal class AppContainer(appContext: Context) {
    val networkClientProvider = NetworkClientProvider()
    val settingsStore = SettingsStore(appContext)
    val providerModelsApi = ProviderModelsApi(clientProvider = networkClientProvider)
    val unifiedLLMClient = UnifiedLLMClient(clientProvider = networkClientProvider)
    val localOcrManager = LocalOcrManager(appContext)
    val settingsRepository = SettingsRepository(settingsStore)
    val searchOrchestrator = SearchOrchestrator(SearchClientImpl(networkClientProvider))
    val tavilyUsageApi = TavilyUsageApi(networkClientProvider)
    val appUpdateApi = AppUpdateApi(networkClientProvider)
    val workflow = WorkflowContainer(
        appContext = appContext,
        settingsRepository = settingsRepository,
        unifiedLLMClient = unifiedLLMClient,
        localOcrManager = localOcrManager,
        searchOrchestrator = searchOrchestrator
    )
}
