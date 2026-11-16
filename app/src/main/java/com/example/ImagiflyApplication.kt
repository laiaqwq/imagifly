package com.example

import android.app.Application
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ImagiflyApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        warmUpNetworkConnections()
        warmUpWebViewEngine()
    }

    private fun warmUpWebViewEngine() {
        // Trigger Chromium/WebView initialization cleanly during main-thread idle
        Looper.myQueue().addIdleHandler {
            try {
                WebSettings.getDefaultUserAgent(applicationContext)
                CookieManager.getInstance()
            } catch (_: Throwable) {
            }
            false
        }
    }

    private fun warmUpNetworkConnections() {
        appScope.launch {
            val hosts = listOf("imagifly.net", "cache.imagifly.net")
            hosts.forEach { host ->
                launch {
                    try {
                        InetAddress.getAllByName(host)
                    } catch (_: Throwable) {
                    }
                    try {
                        val conn = (URL("https://$host/").openConnection() as HttpURLConnection).apply {
                            requestMethod = "HEAD"
                            connectTimeout = 3000
                            readTimeout = 3000
                            instanceFollowRedirects = false
                        }
                        conn.connect()
                        conn.disconnect()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
