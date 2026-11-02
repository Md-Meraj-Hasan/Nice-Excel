package com.meraj.niceexcel

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Offline HyperFormula adapter for the native grid.
 *
 * The hidden WebView hosts the same bundled JavaScript engine used by the web
 * spreadsheet. Formula work is submitted asynchronously; worker threads wait
 * for the renderer callback, while the Android UI thread is never blocked.
 */
internal object HyperFormulaRuntime {
    data class Calculation(
        val values: Map<String, String>,
        val numericKeys: Set<String>
    )

    private const val TAG = "NiceExcel-HyperFormula"
    private const val READY_TIMEOUT_SECONDS = 45L
    private const val CALC_TIMEOUT_SECONDS = 120L
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var webView: WebView? = null
    @Volatile private var ready = false
    @Volatile private var initFailure: String? = null
    @Volatile private var readyLatch = CountDownLatch(1)

    fun attach(activity: Activity) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "HyperFormula WebView must be attached on the Android main thread"
        }
        if (webView != null) return
        ready = false
        initFailure = null
        readyLatch = CountDownLatch(1)

        val view = WebView(activity)
        webView = view
        view.setBackgroundColor(Color.TRANSPARENT)
        view.alpha = 0f
        view.visibility = View.INVISIBLE
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = true
        view.settings.allowContentAccess = false
        view.settings.blockNetworkLoads = true
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    "!!(window.NiceExcelHF && typeof window.NiceExcelHF.calculate === 'function')"
                ) { result ->
                    if (result == "true") {
                        ready = true
                        Log.i(TAG, "Offline HyperFormula runtime ready")
                    } else {
                        initFailure = "HyperFormula bundle did not initialize. Rebuild the offline JS assets."
                        Log.e(TAG, initFailure!!)
                    }
                    readyLatch.countDown()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    initFailure = "Unable to load the local HyperFormula runtime: ${error?.description}"
                    readyLatch.countDown()
                    Log.e(TAG, initFailure!!)
                }
            }
        }
        activity.addContentView(view, ViewGroup.LayoutParams(1, 1))
        view.loadUrl("file:///android_asset/hyperformula-runtime.html")
    }

    fun recalculateSnapshot(snapshot: Map<String, Map<String, String>>): Calculation {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "HyperFormula calculation must run off the Android UI thread"
        }
        if (snapshot.values.none { cells -> cells.values.any { it.trimStart().startsWith("=") } }) {
            return Calculation(emptyMap(), emptySet())
        }
        check(readyLatch.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Timed out waiting for the offline HyperFormula runtime"
        }
        check(ready) { initFailure ?: "HyperFormula runtime is not ready" }

        val payload = JSONObject()
        snapshot.forEach { (sheetName, cells) ->
            val values = JSONObject()
            cells.forEach { (address, value) -> values.put(address, value) }
            payload.put(sheetName, values)
        }

        val completed = CountDownLatch(1)
        val result = AtomicReference<Calculation>()
        val failure = AtomicReference<Throwable?>()
        val payloadLiteral = JSONObject.quote(payload.toString())
        mainHandler.post {
            try {
                val view = webView ?: error("HyperFormula WebView has been destroyed")
                view.evaluateJavascript("window.NiceExcelHF.calculate($payloadLiteral)") { encoded ->
                    try {
                        check(!encoded.isNullOrBlank() && encoded != "null") {
                            "HyperFormula returned an empty calculation response"
                        }
                        val decoded = JSONArray("[$encoded]").getString(0)
                        val response = JSONObject(decoded)
                        if (response.has("error")) {
                            error(response.optString("error", "Unknown HyperFormula error"))
                        }
                        val values = linkedMapOf<String, String>()
                        val results = response.getJSONObject("results")
                        val keys = results.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            values[key] = results.optString(key, "")
                        }
                        val numericArray = response.optJSONArray("numericKeys") ?: JSONArray()
                        val numericKeys = buildSet {
                            for (index in 0 until numericArray.length()) {
                                numericArray.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                        result.set(Calculation(values, numericKeys))
                    } catch (error: Throwable) {
                        failure.set(error)
                    } finally {
                        completed.countDown()
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        check(completed.await(CALC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "HyperFormula calculation timed out"
        }
        failure.get()?.let { throw IllegalStateException("HyperFormula calculation failed", it) }
        return result.get() ?: Calculation(emptyMap(), emptySet())
    }

    fun close() {
        val closeAction = {
            val view = webView
            webView = null
            ready = false
            if (view != null) {
                runCatching {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.stopLoading()
                    view.destroy()
                }
            }
            readyLatch.countDown()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) closeAction()
        else mainHandler.post { closeAction() }
    }
}
