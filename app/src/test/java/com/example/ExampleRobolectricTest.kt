package com.example

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import com.example.network.NetworkConnectivityObserver
import com.example.viewmodel.CachePolicyMode
import com.example.viewmodel.ImagiflyViewModel
import com.example.viewmodel.WebErrorCategory
import com.example.webview.BundledAssetInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and target url from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Imagifly", context.getString(R.string.app_name))
        assertEquals("https://imagifly.net/", context.getString(R.string.target_url))
    }

    @Test
    fun `smart cache policy resolves LOAD_DEFAULT online and LOAD_CACHE_ELSE_NETWORK offline`() {
        assertEquals(
            WebSettings.LOAD_DEFAULT,
            CachePolicyMode.SMART_AUTO.resolveWebSettingsCacheMode(isOnline = true)
        )
        assertEquals(
            WebSettings.LOAD_CACHE_ELSE_NETWORK,
            CachePolicyMode.SMART_AUTO.resolveWebSettingsCacheMode(isOnline = false)
        )
        assertEquals(
            WebSettings.LOAD_CACHE_ELSE_NETWORK,
            CachePolicyMode.PREFER_CACHE.resolveWebSettingsCacheMode(isOnline = true)
        )
        assertEquals(
            WebSettings.LOAD_CACHE_ONLY,
            CachePolicyMode.OFFLINE_ONLY.resolveWebSettingsCacheMode(isOnline = true)
        )
    }

    @Test
    fun `viewModel records network error state and recovers cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = ImagiflyViewModel(context, NetworkConnectivityObserver(context))

        vm.onReceivedWebError(
            errorCode = WebViewClient.ERROR_HOST_LOOKUP,
            description = "ERR_NAME_NOT_RESOLVED",
            failingUrl = "https://imagifly.net/"
        )
        val errorState = vm.uiState.value.activeError
        assertNotNull(errorState)
        assertEquals(WebErrorCategory.OFFLINE_DISCONNECTED, errorState?.category)
        assertEquals("https://imagifly.net/", errorState?.failingUrl)

        vm.clearActiveError()
        assertNull(vm.uiState.value.activeError)
    }

    @Test
    fun `first launch startup state transitions immediately upon page finish`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("imagifly_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val firstVm = ImagiflyViewModel(context, NetworkConnectivityObserver(context))
        assertTrue(firstVm.uiState.value.isFirstLaunch)
        assertTrue(firstVm.uiState.value.isInitialLaunchLoading)

        firstVm.onTitleReceived("Imagifly")
        assertTrue(firstVm.uiState.value.isInitialLaunchLoading)

        firstVm.onPageFinished("https://imagifly.net/en", "Imagifly", false, false)
        assertFalse(firstVm.uiState.value.isInitialLaunchLoading)

        val secondVm = ImagiflyViewModel(context, NetworkConnectivityObserver(context))
        assertFalse(secondVm.uiState.value.isFirstLaunch)
        assertTrue(secondVm.uiState.value.isInitialLaunchLoading)
    }

    @Test
    fun `bundled hero mp4 and webp assets are intercepted locally`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mp4Url = "https://cache.imagifly.net/site/homepage/2026-11-11/wall-mobile-768-8adebf83cc86.mp4"
        val webpUrl = "https://cache.imagifly.net/site/homepage/2026-11-11/wall-mobile-768-fbda6530f22b.webp"

        val mp4Response = BundledAssetInterceptor.interceptIfBundled(
            context,
            fakeRequest(mp4Url, mapOf("Range" to "bytes=0-1023"))
        )
        assertNotNull(mp4Response)
        assertEquals("video/mp4", mp4Response?.mimeType)
        mp4Response?.data?.close()

        val webpResponse = BundledAssetInterceptor.interceptIfBundled(
            context,
            fakeRequest(webpUrl)
        )
        assertNotNull(webpResponse)
        assertEquals("image/webp", webpResponse?.mimeType)
        webpResponse?.data?.close()
    }

    private fun fakeRequest(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): WebResourceRequest = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame(): Boolean = false
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): Map<String, String> = headers
    }
}
