package com.example.webview

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Locale

object BundledAssetInterceptor {

    private data class BundledAssetSpec(
        val assetName: String,
        val mimeType: String
    )

    private val bundledUrlMap: Map<String, BundledAssetSpec> = mapOf(
        "https://cache.imagifly.net/site/homepage/2026-11-11/wall-mobile-768-8adebf83cc86.mp4" to
            BundledAssetSpec("wall-mobile-768-8adebf83cc86.mp4", "video/mp4"),
        "https://cache.imagifly.net/site/homepage/2026-11-11/wall-mobile-768-fbda6530f22b.webp" to
            BundledAssetSpec("wall-mobile-768-fbda6530f22b.webp", "image/webp")
    )

    /**
     * Resolve direct localized URL to skip the initial HTTP 308 redirect from https://imagifly.net/.
     */
    fun resolveDirectHomeUrl(): String {
        val lang = Locale.getDefault().language.lowercase(Locale.US)
        return when {
            lang.startsWith("zh") -> "https://imagifly.net/zh"
            lang.startsWith("en") -> "https://imagifly.net/en"
            else -> "https://imagifly.net/"
        }
    }

    /**
     * Intercepts bundled hero media requests and serves them instantaneously from APK assets,
     * including full HTTP 206 Partial Content Range support required by HTML5 <video> players.
     */
    fun interceptIfBundled(
        context: Context,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString()?.substringBefore('#') ?: return null
        val normalizedUrl = url.substringBefore('?')
        val spec = bundledUrlMap[url] ?: bundledUrlMap[normalizedUrl] ?: return null

        val originHeader = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Origin", ignoreCase = true) }
            ?.value ?: "*"

        val rangeHeader = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Range", ignoreCase = true) }
            ?.value

        return try {
            val afd = context.assets.openFd(spec.assetName)
            val totalLength = afd.length

            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
                val rangeSpec = rangeHeader.substringAfter("bytes=").trim()
                val parts = rangeSpec.split("-", limit = 2)
                val start = parts.getOrNull(0)?.toLongOrNull()?.coerceIn(0L, (totalLength - 1).coerceAtLeast(0L)) ?: 0L
                val requestedEnd = parts.getOrNull(1)?.toLongOrNull()
                val end = (requestedEnd ?: (totalLength - 1)).coerceIn(start, (totalLength - 1).coerceAtLeast(start))
                val contentLength = (end - start + 1).coerceAtLeast(0L)

                val stream = FileInputStream(afd.fileDescriptor).apply {
                    channel.position(afd.startOffset + start)
                }
                val boundedStream = BoundedAssetInputStream(stream, contentLength, afd)

                val headers = mapOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes $start-$end/$totalLength",
                    "Content-Length" to contentLength.toString(),
                    "Cache-Control" to "public, max-age=31536000, immutable",
                    "Access-Control-Allow-Origin" to originHeader
                )

                WebResourceResponse(
                    spec.mimeType,
                    null,
                    206,
                    "Partial Content",
                    headers,
                    boundedStream
                )
            } else {
                val stream = FileInputStream(afd.fileDescriptor).apply {
                    channel.position(afd.startOffset)
                }
                val boundedStream = BoundedAssetInputStream(stream, totalLength, afd)

                val headers = mapOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Length" to totalLength.toString(),
                    "Cache-Control" to "public, max-age=31536000, immutable",
                    "Access-Control-Allow-Origin" to originHeader
                )

                WebResourceResponse(
                    spec.mimeType,
                    null,
                    200,
                    "OK",
                    headers,
                    boundedStream
                )
            }
        } catch (_: Exception) {
            // Fallback for compressed assets or test environments
            try {
                val stream = context.assets.open(spec.assetName)
                val headers = mapOf(
                    "Cache-Control" to "public, max-age=31536000, immutable",
                    "Access-Control-Allow-Origin" to originHeader
                )
                WebResourceResponse(spec.mimeType, null, 200, "OK", headers, stream)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * JS snippet injected into the page to enforce lazy loading on below-the-fold images and non-hero videos,
     * including dynamically inserted DOM nodes via MutationObserver.
     */
    const val LAZY_LOAD_ENFORCEMENT_JS: String = """
        (function() {
          if (window.__imagiflyLazyApplied) return;
          window.__imagiflyLazyApplied = true;
          function applyLazy(root) {
            var vh = window.innerHeight || 800;
            var imgs = (root || document).querySelectorAll('img');
            for (var i = 0; i < imgs.length; i++) {
              var img = imgs[i];
              if (!img.classList.contains('home-wall-poster')) {
                var rect = img.getBoundingClientRect();
                if (rect.top > vh * 1.15) {
                  if (img.getAttribute('loading') !== 'lazy') {
                    img.setAttribute('loading', 'lazy');
                  }
                  if (!img.getAttribute('decoding')) {
                    img.setAttribute('decoding', 'async');
                  }
                  img.setAttribute('fetchpriority', 'low');
                }
              }
            }
            var videos = (root || document).querySelectorAll('video');
            for (var j = 0; j < videos.length; j++) {
              var vid = videos[j];
              if (!vid.classList.contains('home-wall-media')) {
                var vRect = vid.getBoundingClientRect();
                if (vRect.top > vh * 1.2 && vid.getAttribute('preload') !== 'none') {
                  vid.setAttribute('preload', 'none');
                }
              }
            }
          }
          applyLazy(document);
          if (typeof MutationObserver !== 'undefined' && document.body) {
            var observer = new MutationObserver(function(mutations) {
              for (var k = 0; k < mutations.length; k++) {
                var added = mutations[k].addedNodes;
                for (var m = 0; m < added.length; m++) {
                  if (added[m].nodeType === 1) {
                    applyLazy(added[m]);
                  }
                }
              }
            });
            observer.observe(document.body, { childList: true, subtree: true });
          }
        })();
    """

    private class BoundedAssetInputStream(
        delegate: InputStream,
        private var remainingBytes: Long,
        private val closeableFd: android.content.res.AssetFileDescriptor
    ) : FilterInputStream(delegate) {

        override fun read(): Int {
            if (remainingBytes <= 0L) return -1
            val byteRead = super.read()
            if (byteRead != -1) remainingBytes--
            return byteRead
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remainingBytes <= 0L) return -1
            val toRead = minOf(len.toLong(), remainingBytes).toInt()
            val count = super.read(b, off, toRead)
            if (count > 0) remainingBytes -= count
            return count
        }

        override fun close() {
            try {
                super.close()
            } finally {
                try {
                    closeableFd.close()
                } catch (_: Exception) {
                }
            }
        }
    }
}
