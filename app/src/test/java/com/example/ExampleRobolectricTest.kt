package com.example

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import com.example.network.NetworkConnectivityObserver
import com.example.viewmodel.CachePolicyMode
import com.example.viewmodel.ImagiflyViewModel
import com.example.viewmodel.WebErrorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
