package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.TransportType
import com.example.ui.components.CacheSettingsSheet
import com.example.ui.components.NetworkErrorOverlay
import com.example.ui.components.StartupLoadingOverlay
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate950
import com.example.viewmodel.CachePolicyMode
import com.example.viewmodel.ImagiflyViewModel
import com.example.webview.BundledAssetInterceptor
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ImagiflyBrowserScreen(
    viewModel: ImagiflyViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showCacheSheet by remember { mutableStateOf(false) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var attemptedAutoCacheRetryForUrl by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val callback = fileUploadCallback
        fileUploadCallback = null
        if (uri != null) {
            callback?.onReceiveValue(arrayOf(uri))
        } else {
            callback?.onReceiveValue(null)
        }
    }

    // Show toast/snackbar messages from ViewModel
    LaunchedEffect(uiState.statusToastMessage) {
        val message = uiState.statusToastMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusToast()
        }
    }

    // Keep WebView cacheMode synchronized with connectivity and selected CachePolicyMode
    LaunchedEffect(uiState.cachePolicyMode, uiState.networkStatus.isOnline) {
        val resolvedMode = uiState.cachePolicyMode.resolveWebSettingsCacheMode(
            isOnline = uiState.networkStatus.isOnline
        )
        webViewRef?.settings?.cacheMode = resolvedMode
    }

    // Staged image loading on slower cellular networks: release images once HTML/CSS/JS reach 65%
    LaunchedEffect(uiState.loadProgress, uiState.isLoading) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (wv.settings.blockNetworkImage && (!uiState.isLoading || uiState.loadProgress >= 65)) {
            wv.settings.blockNetworkImage = false
        }
    }

    // Handle system Back press
    BackHandler(
        enabled = showCacheSheet || uiState.isSimulatingError || uiState.canGoBack
    ) {
        when {
            showCacheSheet -> showCacheSheet = false
            uiState.isSimulatingError -> viewModel.toggleSimulatedError(false)
            webViewRef?.canGoBack() == true -> {
                viewModel.clearActiveError()
                webViewRef?.goBack()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950),
        containerColor = Slate950,
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Slate800,
                    contentColor = Color.White,
                    actionColor = Cyan400
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate950),
            contentAlignment = Alignment.Center
        ) {
            // Pure Fullscreen System WebView Container
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    // Hide underlying WebView when startup splash or custom NetworkErrorOverlay is active
                    .alpha(if (uiState.activeError == null && !uiState.isInitialLaunchLoading) 1f else 0f)
                    .testTag("imagifly_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.parseColor("#090E1A"))

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            // On slower cellular connections, prioritize HTML/CSS/JS first before remote images
                            blockNetworkImage = uiState.networkStatus.transportType == TransportType.CELLULAR
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            javaScriptCanOpenWindowsAutomatically = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            allowFileAccess = true
                            allowContentAccess = true
                            cacheMode = uiState.cachePolicyMode.resolveWebSettingsCacheMode(
                                isOnline = uiState.networkStatus.isOnline
                            )
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                // Serve bundled hero MP4 video & mobile poster directly from APK assets
                                BundledAssetInterceptor.interceptIfBundled(ctx, request)?.let {
                                    return it
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase() ?: return false
                                return if (scheme == "http" || scheme == "https" || scheme == "file") {
                                    false
                                } else {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (_: Exception) {
                                        viewModel.postToastMessage("未找到可处理该链接的外部应用")
                                    }
                                    true
                                }
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                viewModel.onPageStarted(url)
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                super.onPageCommitVisible(view, url)
                                view?.settings?.blockNetworkImage = false
                                view?.evaluateJavascript(
                                    BundledAssetInterceptor.LAZY_LOAD_ENFORCEMENT_JS,
                                    null
                                )
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val wv = view ?: return
                                wv.settings.blockNetworkImage = false
                                wv.evaluateJavascript(
                                    BundledAssetInterceptor.LAZY_LOAD_ENFORCEMENT_JS,
                                    null
                                )
                                viewModel.onPageFinished(
                                    url = url,
                                    title = wv.title,
                                    canGoBack = wv.canGoBack(),
                                    canGoForward = wv.canGoForward()
                                )

                                // Automatically archive an offline .mht snapshot when online load succeeds
                                if (uiState.activeError == null &&
                                    uiState.networkStatus.isOnline &&
                                    url != null &&
                                    url.startsWith("https://")
                                ) {
                                    val archiveFile = File(
                                        viewModel.getOfflineArchiveDirectory(),
                                        "imagifly_auto_snapshot.mht"
                                    )
                                    wv.saveWebArchive(archiveFile.absolutePath, false) {
                                        viewModel.refreshCacheStatistics()
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    val failingUrl = request.url?.toString() ?: uiState.currentUrl
                                    val code = error?.errorCode ?: WebViewClient.ERROR_UNKNOWN
                                    val desc = error?.description?.toString()

                                    // Automatic first-chance fallback to WebView disk cache before showing error UI
                                    val wv = view
                                    if (wv != null &&
                                        attemptedAutoCacheRetryForUrl != failingUrl &&
                                        wv.settings.cacheMode != WebSettings.LOAD_CACHE_ELSE_NETWORK &&
                                        wv.settings.cacheMode != WebSettings.LOAD_CACHE_ONLY
                                    ) {
                                        attemptedAutoCacheRetryForUrl = failingUrl
                                        wv.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                                        wv.loadUrl(failingUrl)
                                        return
                                    }

                                    viewModel.onReceivedWebError(
                                        errorCode = code,
                                        description = desc,
                                        failingUrl = failingUrl
                                    )
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                if (request?.isForMainFrame == true) {
                                    val statusCode = errorResponse?.statusCode ?: return
                                    if (statusCode >= 500) {
                                        viewModel.onReceivedWebError(
                                            errorCode = statusCode,
                                            description = "HTTP $statusCode ${errorResponse.reasonPhrase.orEmpty()}",
                                            failingUrl = request.url?.toString()
                                        )
                                    }
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                // Strictly cancel invalid SSL connections per Play Store policy
                                handler?.cancel()
                                viewModel.onReceivedWebError(
                                    errorCode = WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
                                    description = "SSL 证书校验失败 (Primary Error: ${error?.primaryError})",
                                    failingUrl = error?.url ?: uiState.currentUrl
                                )
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                viewModel.onProgressChanged(
                                    progress = newProgress,
                                    canGoBack = view?.canGoBack() ?: false,
                                    canGoForward = view?.canGoForward() ?: false
                                )
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                viewModel.onTitleReceived(title)
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = filePathCallback
                                return try {
                                    filePickerLauncher.launch("*/*")
                                    true
                                } catch (_: Exception) {
                                    fileUploadCallback = null
                                    false
                                }
                            }
                        }

                        webViewRef = this
                        loadUrl(uiState.homeUrl)
                    }
                }
            )

            // Simple Startup Loading Screen (immediately dismissed once page finishes loading)
            StartupLoadingOverlay(
                visible = uiState.isInitialLaunchLoading && uiState.activeError == null,
                isFirstLaunch = uiState.isFirstLaunch,
                loadProgress = uiState.loadProgress
            )

            // Custom Network Anomaly & Offline Cache Recovery Page Overlay
            NetworkErrorOverlay(
                uiState = uiState,
                onRetryNetwork = {
                    attemptedAutoCacheRetryForUrl = null
                    val targetUrl = uiState.activeError?.failingUrl ?: uiState.homeUrl
                    viewModel.clearActiveError()
                    webViewRef?.let { wv ->
                        wv.settings.cacheMode = if (uiState.networkStatus.isOnline) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        wv.loadUrl(targetUrl)
                    }
                },
                onLoadFromCacheFallback = {
                    val targetUrl = uiState.activeError?.failingUrl ?: uiState.homeUrl
                    viewModel.clearActiveError()
                    viewModel.setCachePolicyMode(CachePolicyMode.PREFER_CACHE)
                    webViewRef?.let { wv ->
                        wv.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        val latestArchive = uiState.offlineArchives.firstOrNull()
                        if (!uiState.networkStatus.isOnline && latestArchive != null) {
                            wv.loadUrl("file://${latestArchive.filePath}")
                            viewModel.postToastMessage("已从本地离线快照恢复页面：${latestArchive.fileName}")
                        } else {
                            wv.loadUrl(targetUrl)
                            viewModel.postToastMessage("已切换至优先本地缓存模式 (LOAD_CACHE_ELSE_NETWORK)")
                        }
                    }
                },
                onOpenCacheSettings = {
                    viewModel.refreshCacheStatistics()
                    showCacheSheet = true
                },
                onOpenSystemNetworkSettings = {
                    openSystemWirelessSettings(context)
                }
            )
        }
    }

    if (showCacheSheet) {
        CacheSettingsSheet(
            uiState = uiState,
            onDismiss = { showCacheSheet = false },
            onSelectCacheMode = { mode ->
                viewModel.setCachePolicyMode(mode)
                webViewRef?.settings?.cacheMode = mode.resolveWebSettingsCacheMode(
                    isOnline = uiState.networkStatus.isOnline
                )
            },
            onSaveOfflineArchive = {
                val wv = webViewRef
                if (wv != null) {
                    val fileName = "imagifly_snapshot_${System.currentTimeMillis()}.mht"
                    val destFile = File(viewModel.getOfflineArchiveDirectory(), fileName)
                    wv.saveWebArchive(destFile.absolutePath, false) { savedPath ->
                        viewModel.refreshCacheStatistics()
                        if (savedPath != null) {
                            viewModel.postToastMessage("离线网页快照已保存 ($fileName)")
                        } else {
                            viewModel.postToastMessage("当前页面暂不支持生成离线快照，请等待页面加载完成")
                        }
                    }
                }
            },
            onLoadLatestOfflineArchive = {
                val latest = uiState.offlineArchives.firstOrNull()
                val wv = webViewRef
                if (latest != null && wv != null) {
                    viewModel.clearActiveError()
                    wv.settings.allowFileAccess = true
                    wv.loadUrl("file://${latest.filePath}")
                    viewModel.postToastMessage("正在加载本地离线快照：${latest.fileName}")
                } else if (wv != null) {
                    viewModel.clearActiveError()
                    wv.settings.cacheMode = WebSettings.LOAD_CACHE_ONLY
                    wv.loadUrl(uiState.homeUrl)
                    viewModel.postToastMessage("已使用纯离线 WebView 缓存模式加载")
                }
            },
            onClearWebCache = {
                webViewRef?.clearCache(true)
                viewModel.deleteOfflineArchives {
                    viewModel.postToastMessage("网页缓存与离线快照已全部清理")
                }
            },
            onClearCookiesAndStorage = {
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                }
                WebStorage.getInstance().deleteAllData()
                webViewRef?.clearFormData()
                webViewRef?.clearHistory()
                viewModel.refreshCacheStatistics()
                viewModel.postToastMessage("Cookies、DOM Storage 与历史记录已清理")
            },
            onToggleSimulateError = { enable ->
                viewModel.toggleSimulatedError(enable)
            }
        )
    }
}

private fun openSystemWirelessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
        }
    }
}
