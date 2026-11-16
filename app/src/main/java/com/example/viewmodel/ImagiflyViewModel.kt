package com.example.viewmodel

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.network.NetworkConnectivityObserver
import com.example.network.NetworkStatusSnapshot
import com.example.network.TransportType
import com.example.webview.BundledAssetInterceptor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CachePolicyMode(
    val titleRes: String,
    val subtitleRes: String,
    val badgeText: String
) {
    SMART_AUTO(
        titleRes = "智能自适应缓存 (推荐)",
        subtitleRes = "在线时使用标准 HTTP 缓存 (LOAD_DEFAULT)，断网或异常时自动无缝切换至本地缓存 (LOAD_CACHE_ELSE_NETWORK)",
        badgeText = "智能缓存"
    ),
    PREFER_CACHE(
        titleRes = "优先本地缓存 (省流极速)",
        subtitleRes = "只要本地有缓存副本即优先使用 (LOAD_CACHE_ELSE_NETWORK)，无缓存时才请求网络",
        badgeText = "优先缓存"
    ),
    OFFLINE_ONLY(
        titleRes = "纯离线只读模式",
        subtitleRes = "仅从本地缓存读取数据，完全不发起网络请求 (LOAD_CACHE_ONLY)",
        badgeText = "仅读缓存"
    ),
    NO_CACHE(
        titleRes = "强制实时拉取 (禁用缓存)",
        subtitleRes = "每次访问均直接请求远端服务器最新数据 (LOAD_NO_CACHE)",
        badgeText = "实时网络"
    );

    fun resolveWebSettingsCacheMode(isOnline: Boolean): Int {
        return when (this) {
            SMART_AUTO -> if (isOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
            PREFER_CACHE -> WebSettings.LOAD_CACHE_ELSE_NETWORK
            OFFLINE_ONLY -> WebSettings.LOAD_CACHE_ONLY
            NO_CACHE -> WebSettings.LOAD_NO_CACHE
        }
    }
}

enum class WebErrorCategory(val label: String) {
    OFFLINE_DISCONNECTED("网络连接中断"),
    CONNECTION_TIMEOUT("服务器响应超时"),
    SERVER_HTTP_ERROR("服务端响应异常"),
    SSL_SECURITY_ERROR("SSL 安全证书异常"),
    SIMULATED_DRILL("网络异常模拟演练")
}

data class WebPageError(
    val category: WebErrorCategory,
    val errorCode: Int,
    val description: String,
    val failingUrl: String,
    val timestamp: String
)

data class OfflineArchiveInfo(
    val filePath: String,
    val fileName: String,
    val savedAt: String,
    val sizeBytes: Long
)

data class ImagiflyUiState(
    val homeUrl: String = BundledAssetInterceptor.resolveDirectHomeUrl(),
    val currentUrl: String = BundledAssetInterceptor.resolveDirectHomeUrl(),
    val pageTitle: String = "Imagifly",
    val isFirstLaunch: Boolean = true,
    val isInitialLaunchLoading: Boolean = true,
    val isLoading: Boolean = true,
    val loadProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val networkStatus: NetworkStatusSnapshot = NetworkStatusSnapshot(
        isConnected = true,
        isValidated = true,
        transportType = TransportType.WIFI
    ),
    val wasOfflineBannerDismissed: Boolean = false,
    val showOnlineRestoredPrompt: Boolean = false,
    val cachePolicyMode: CachePolicyMode = CachePolicyMode.SMART_AUTO,
    val isLoadedFromCacheFallback: Boolean = false,
    val activeError: WebPageError? = null,
    val isSimulatingError: Boolean = false,
    val cacheSizeBytes: Long = 0L,
    val offlineArchives: List<OfflineArchiveInfo> = emptyList(),
    val statusToastMessage: String? = null
)

class ImagiflyViewModel(
    private val appContext: Context,
    private val connectivityObserver: NetworkConnectivityObserver
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isFirstLaunchFlag = !prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
    private val initialHomeUrl = BundledAssetInterceptor.resolveDirectHomeUrl()

    private val _uiState = MutableStateFlow(
        ImagiflyUiState(
            homeUrl = initialHomeUrl,
            currentUrl = initialHomeUrl,
            isFirstLaunch = isFirstLaunchFlag,
            isInitialLaunchLoading = true,
            networkStatus = connectivityObserver.getCurrentStatus()
        )
    )
    val uiState: StateFlow<ImagiflyUiState> = _uiState.asStateFlow()

    private var previousOnlineState: Boolean = _uiState.value.networkStatus.isOnline

    init {
        viewModelScope.launch {
            connectivityObserver.observe().collect { snapshot ->
                val justCameOnline = !previousOnlineState && snapshot.isOnline
                previousOnlineState = snapshot.isOnline
                _uiState.update { state ->
                    state.copy(
                        networkStatus = snapshot,
                        wasOfflineBannerDismissed = if (!snapshot.isOnline) false else state.wasOfflineBannerDismissed,
                        showOnlineRestoredPrompt = if (justCameOnline) true else if (!snapshot.isOnline) false else state.showOnlineRestoredPrompt
                    )
                }
            }
        }
        refreshCacheStatistics()
    }

    fun onPageStarted(url: String?) {
        val safeUrl = url ?: _uiState.value.currentUrl
        _uiState.update { state ->
            state.copy(
                currentUrl = if (safeUrl.startsWith("file://")) state.homeUrl else safeUrl,
                isLoading = true,
                loadProgress = 10,
                isLoadedFromCacheFallback = safeUrl.startsWith("file://") ||
                    !state.networkStatus.isOnline ||
                    state.cachePolicyMode == CachePolicyMode.OFFLINE_ONLY
            )
        }
    }

    fun onProgressChanged(progress: Int, canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update { state ->
            state.copy(
                loadProgress = progress,
                isLoading = progress < 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onTitleReceived(title: String?) {
        val cleanTitle = title?.takeIf {
            it.isNotBlank() &&
                !it.contains("Webpage not available", ignoreCase = true) &&
                !it.contains("找不到网页")
        } ?: return
        _uiState.update { it.copy(pageTitle = cleanTitle) }
    }

    fun onPageFinished(url: String?, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        val safeUrl = url ?: _uiState.value.currentUrl
        val cleanTitle = title?.takeIf {
            it.isNotBlank() &&
                !it.contains("Webpage not available", ignoreCase = true) &&
                !it.contains("找不到网页")
        } ?: "Imagifly"

        markFirstLaunchCompleted()

        _uiState.update { state ->
            state.copy(
                currentUrl = if (safeUrl.startsWith("file://")) state.homeUrl else safeUrl,
                pageTitle = cleanTitle,
                isLoading = false,
                isInitialLaunchLoading = false,
                loadProgress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
        refreshCacheStatistics()
    }

    fun onReceivedWebError(errorCode: Int, description: String?, failingUrl: String?) {
        val targetUrl = failingUrl ?: _uiState.value.currentUrl
        val category = when {
            !_uiState.value.networkStatus.isOnline -> WebErrorCategory.OFFLINE_DISCONNECTED
            errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                errorCode == WebViewClient.ERROR_CONNECT ||
                errorCode == WebViewClient.ERROR_IO -> WebErrorCategory.OFFLINE_DISCONNECTED
            errorCode == WebViewClient.ERROR_TIMEOUT -> WebErrorCategory.CONNECTION_TIMEOUT
            errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> WebErrorCategory.SSL_SECURITY_ERROR
            else -> WebErrorCategory.SERVER_HTTP_ERROR
        }

        val readableDesc = when (category) {
            WebErrorCategory.OFFLINE_DISCONNECTED ->
                "无法解析或连接主机 (${description ?: "ERR_INTERNET_DISCONNECTED"})。当网络不可用且本地未命中缓存时触发此保护页。"
            WebErrorCategory.CONNECTION_TIMEOUT ->
                "连接 ${targetUrl} 响应超时 (${description ?: "ERR_CONNECTION_TIMED_OUT"})，请检查当前网络质量。"
            WebErrorCategory.SSL_SECURITY_ERROR ->
                "目标站点的 SSL 数字证书校验未通过，为保护您的数据安全已拦截不安全连接。"
            WebErrorCategory.SERVER_HTTP_ERROR ->
                "网页加载异常 (${description ?: "HTTP / WebView Error $errorCode"})。"
            WebErrorCategory.SIMULATED_DRILL ->
                "当前处于网络异常模拟演练模式，用于直观展示断网与异常状态下的容灾提示页与离线缓存恢复体验。"
        }

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isInitialLaunchLoading = false,
                activeError = WebPageError(
                    category = category,
                    errorCode = errorCode,
                    description = readableDesc,
                    failingUrl = targetUrl,
                    timestamp = formatCurrentTime()
                )
            )
        }
    }

    fun toggleSimulatedError(enable: Boolean) {
        if (enable) {
            _uiState.update { state ->
                state.copy(
                    isSimulatingError = true,
                    isLoading = false,
                    isInitialLaunchLoading = false,
                    activeError = WebPageError(
                        category = WebErrorCategory.SIMULATED_DRILL,
                        errorCode = -106,
                        description = "演练模式：已模拟网络中断 (ERR_INTERNET_DISCONNECTED)。您可在此测试“优先读取本地缓存/快照”或“重新加载网页”。",
                        failingUrl = state.currentUrl,
                        timestamp = formatCurrentTime()
                    )
                )
            }
        } else {
            _uiState.update { state ->
                state.copy(
                    isSimulatingError = false,
                    activeError = null
                )
            }
        }
    }

    fun clearActiveError() {
        _uiState.update { state ->
            state.copy(
                activeError = null,
                isSimulatingError = false,
                showOnlineRestoredPrompt = false
            )
        }
    }

    fun setCachePolicyMode(mode: CachePolicyMode) {
        _uiState.update { state ->
            state.copy(
                cachePolicyMode = mode,
                statusToastMessage = "已切换缓存策略为：${mode.badgeText}"
            )
        }
    }

    fun dismissOfflineBanner() {
        _uiState.update { it.copy(wasOfflineBannerDismissed = true) }
    }

    fun dismissOnlineRestoredPrompt() {
        _uiState.update { it.copy(showOnlineRestoredPrompt = false) }
    }

    fun clearStatusToast() {
        _uiState.update { it.copy(statusToastMessage = null) }
    }

    fun postToastMessage(message: String) {
        _uiState.update { it.copy(statusToastMessage = message) }
    }

    fun getOfflineArchiveDirectory(): File {
        val dir = File(appContext.cacheDir, "offline_snapshots")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun refreshCacheStatistics() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDirSize = calculateDirectorySize(appContext.cacheDir)
            val webViewDir = File(appContext.applicationInfo.dataDir, "app_webview")
            val webViewDirSize = calculateDirectorySize(webViewDir)
            val totalBytes = cacheDirSize + webViewDirSize

            val snapshotDir = getOfflineArchiveDirectory()
            val archives = snapshotDir.listFiles()
                ?.filter { it.isFile && (it.name.endsWith(".mht") || it.name.endsWith(".mhtml")) }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    OfflineArchiveInfo(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        savedAt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(file.lastModified())),
                        sizeBytes = file.length()
                    )
                } ?: emptyList()

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        cacheSizeBytes = totalBytes,
                        offlineArchives = archives
                    )
                }
            }
        }
    }

    fun deleteOfflineArchives(onCompleted: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = getOfflineArchiveDirectory()
            dir.listFiles()?.forEach { it.delete() }
            val cacheSubDirs = appContext.cacheDir.listFiles()
            cacheSubDirs?.forEach { file ->
                if (file.name.contains("WebView", ignoreCase = true) ||
                    file.name.contains("org.chromium", ignoreCase = true)
                ) {
                    file.deleteRecursively()
                }
            }
            withContext(Dispatchers.Main) {
                refreshCacheStatistics()
                onCompleted()
            }
        }
    }

    private fun markFirstLaunchCompleted() {
        if (!prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH_COMPLETED, true).apply()
        }
    }

    private fun calculateDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var result = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            result += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return result
    }

    private fun formatCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            return ImagiflyViewModel(
                appContext = appContext,
                connectivityObserver = NetworkConnectivityObserver(appContext)
            ) as T
        }
    }

    companion object {
        private const val PREFS_NAME = "imagifly_prefs"
        private const val KEY_FIRST_LAUNCH_COMPLETED = "first_launch_completed"
    }
}
