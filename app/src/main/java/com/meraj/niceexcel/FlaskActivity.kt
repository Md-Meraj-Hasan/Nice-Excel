package com.meraj.niceexcel

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import android.util.Base64
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Optional Flask-powered web spreadsheet. Existing native workbook UI remains available. */
class FlaskActivity : Activity() {
    companion object {
        const val EXTRA_OPEN_EXTERNAL_BROWSER = "com.meraj.niceexcel.OPEN_EXTERNAL_BROWSER"
    }
    private lateinit var webView: WebView
    private lateinit var loadingText: TextView
    private val localUrl = "http://127.0.0.1:8765/"
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 4107
    private val pdfSaveRequestCode = 4108
    private var pendingPdfBytes: ByteArray? = null
    private var pendingPdfName = "NiceExcel.pdf"
    @Volatile private var engineReady = false
    @Volatile private var engineFailed = false
    private var mainFrameErrorShown = false
    private val engineLogFile by lazy { File(filesDir, "niceexcel_engine_startup.log") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep Android's status/navigation bars in sync with the palette selected in Native Excel.
        val themePrefs = getSharedPreferences("niceexcel_theme", MODE_PRIVATE)
        val systemAccent = themePrefs.getInt("day_palette_primary", Color.rgb(17, 94, 57))
        val blackTheme = themePrefs.getBoolean("dark_mode", false)
        // Keep system bars black in Black Theme; preserve the selected accent in Day Theme.
        window.statusBarColor = if (blackTheme) Color.BLACK else systemAccent
        window.navigationBarColor = if (blackTheme) Color.BLACK else Color.rgb(20, 30, 40)
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(if (blackTheme) Color.rgb(11, 16, 32) else Color.WHITE) }
        loadingText = TextView(this).apply {
            text = "Starting offline Excel engine…"
            textSize = 14f
            setTextColor(Color.rgb(55, 67, 77))
            gravity = android.view.Gravity.CENTER
            setPadding(16, 18, 16, 18)
        }
        root.addView(loadingText, LinearLayout.LayoutParams(-1, -2))
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true && !mainFrameErrorShown) {
                        mainFrameErrorShown = true
                        loadingText.text = "Excel Web Engine failed to load. Go back and try Web again."
                        loadingText.visibility = android.view.View.VISIBLE
                        Toast.makeText(this@FlaskActivity, "Excel Web Engine connection failed. Go back and try Web again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    pendingFileChooser?.onReceiveValue(null)
                    pendingFileChooser = filePathCallback
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel.sheet.macroEnabled.12",
                            "text/csv",
                            "application/csv"
                        ))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    return try {
                        startActivityForResult(intent, fileChooserRequestCode)
                        true
                    } catch (e: Exception) {
                        pendingFileChooser?.onReceiveValue(null)
                        pendingFileChooser = null
                        Toast.makeText(this@FlaskActivity, "Unable to open device storage: ${e.message}", Toast.LENGTH_LONG).show()
                        false
                    }
                }
            }
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun goNative() {
                runOnUiThread { finish() }
            }

            // Save a PDF generated by the Web Page through Android's document picker.
            @JavascriptInterface
            fun savePdf(base64Data: String, fileName: String) {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    if (bytes.isEmpty()) throw IllegalArgumentException("PDF file is empty")
                    runOnUiThread {
                        pendingPdfBytes = bytes
                        pendingPdfName = fileName.substringAfterLast('/').ifBlank { "NiceExcel.pdf" }
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_TITLE, pendingPdfName)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                        try {
                            startActivityForResult(intent, pdfSaveRequestCode)
                        } catch (e: Exception) {
                            pendingPdfBytes = null
                            Toast.makeText(this@FlaskActivity, "Unable to open PDF save dialog: ${e.message}", Toast.LENGTH_LONG).show()
                            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('niceexcel-pdf-saved',{detail:{success:false,message:'Unable to open save dialog'}}))", null)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@FlaskActivity, "PDF preparation failed: ${e.message}", Toast.LENGTH_LONG).show()
                        webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('niceexcel-pdf-saved',{detail:{success:false,message:'PDF preparation failed'}}))", null)
                    }
                }
            }
        }, "NiceExcelNative")
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        try {
            copyUiAsset()
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))

            // If the local engine survived an activity reopen, reuse it rather than
            // trying to bind port 8765 a second time.
            Thread {
                if (waitForHealth(900)) {
                    engineReady = true
                    runOnUiThread { if (!isFinishing) loadWebUi() }
                    return@Thread
                }

                Thread {
                    try {
                        Python.getInstance().getModule("server").callAttr("start_server", filesDir.absolutePath)
                    } catch (e: Exception) {
                        engineFailed = true
                        val detail = Log.getStackTraceString(e)
                        try { engineLogFile.appendText("\n=== Python engine start failure ===\n$detail\n") } catch (_: Exception) { }
                        Log.e("NiceExcelWebEngine", "Python/Flask startup failed", e)
                        runOnUiThread {
                            if (!isFinishing) {
                                loadingText.visibility = android.view.View.VISIBLE
                                loadingText.text = "Offline Excel engine failed to start.\n\n" +
                                    detail.takeLast(5000) +
                                    "\n\nStartup log: files/niceexcel_engine_startup.log"
                                Toast.makeText(
                                    this,
                                    "Excel engine failed. Error details are shown on this screen.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }.apply { name = "NiceExcel-Flask"; isDaemon = true; start() }

                val ready = waitForHealth(25000)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    if (ready) {
                        engineReady = true
                        loadWebUi()
                    } else {
                        val detail = try {
                            if (engineLogFile.exists()) engineLogFile.readText().takeLast(5000)
                            else "No startup log was created. Expected local health endpoint: 127.0.0.1:8765/health"
                        } catch (_: Exception) { "Could not read engine startup log." }
                        val message = if (engineFailed) "Offline Excel engine crashed during startup." else "Offline Excel engine timed out before becoming ready."
                        Log.e("NiceExcelWebEngine", "$message\n$detail")
                        loadingText.visibility = android.view.View.VISIBLE
                        loadingText.text = message + "\n\n" + detail + "\n\nPlease send this error screen so the exact cause can be fixed."
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }.apply { name = "NiceExcel-EngineBootstrap"; isDaemon = true; start() }
        } catch (e: Exception) {
            val detail = Log.getStackTraceString(e)
            Log.e("NiceExcelWebEngine", "Unable to initialize Web Engine screen", e)
            try { engineLogFile.appendText("\n=== Web Engine initialization failure ===\n$detail\n") } catch (_: Exception) { }
            loadingText.visibility = android.view.View.VISIBLE
            loadingText.text = "Offline Excel engine could not initialize.\n\n" + detail.takeLast(5000) +
                "\n\nStartup log: files/niceexcel_engine_startup.log"
            Toast.makeText(this, "Python runtime could not start. Error details are shown on screen.", Toast.LENGTH_LONG).show()
        }
    }

    private fun waitForHealth(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !isFinishing) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("http://127.0.0.1:8765/health").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 500
                    readTimeout = 500
                    requestMethod = "GET"
                }
                if (connection.responseCode == 200) return true
            } catch (_: Exception) {
                // The server is not ready yet; keep polling until the deadline.
            } finally {
                connection?.disconnect()
            }
            try { Thread.sleep(250) } catch (_: InterruptedException) { return false }
        }
        return false
    }

    private fun loadWebUi() {
        if (isFinishing || !::webView.isInitialized) return
        mainFrameErrorShown = false
        if (::loadingText.isInitialized) loadingText.visibility = android.view.View.GONE
        if (intent.getBooleanExtra(EXTRA_OPEN_EXTERNAL_BROWSER, false)) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(localUrl)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                startActivity(browserIntent)
                Toast.makeText(this, "Opening $localUrl in browser", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "No browser available: ${e.message}", Toast.LENGTH_LONG).show()
                webView.loadUrl(localUrl)
            }
        } else {
            webView.loadUrl(localUrl)
        }
    }

    private fun copyUiAsset() {
        val bundledAssets = listOf(
            "niceexcel_index.html" to "niceexcel_asset_index.html",
            "hyperformula.bundle.js" to "niceexcel_hyperformula.bundle.js",
            "hyperformula_runtime.js" to "niceexcel_hyperformula_runtime.js",
            "niceexcel_pdf.bundle.js" to "niceexcel_pdf.bundle.js"
        )
        bundledAssets.forEach { (assetName, targetName) ->
            val target = File(filesDir, targetName)
            assets.open(assetName).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
    }


    @Deprecated("Deprecated in Android API; required for WebView file chooser callback compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pdfSaveRequestCode) {
            val bytes = pendingPdfBytes
            pendingPdfBytes = null
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (uri != null && bytes != null) {
                try {
                    val output = contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Could not open the selected save location")
                    output.use { it.write(bytes) }
                    Toast.makeText(this, "PDF saved successfully", Toast.LENGTH_LONG).show()
                    webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('niceexcel-pdf-saved',{detail:{success:true}}))", null)
                } catch (e: Exception) {
                    Toast.makeText(this, "PDF save failed: ${e.message}", Toast.LENGTH_LONG).show()
                    webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('niceexcel-pdf-saved',{detail:{success:false,message:'Save failed'}}))", null)
                }
            } else {
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('niceexcel-pdf-saved',{detail:{success:false,cancelled:true}}))", null)
            }
            return
        }
        if (requestCode != fileChooserRequestCode) return
        val callback = pendingFileChooser ?: return
        pendingFileChooser = null
        val uri = if (resultCode == RESULT_OK) data?.data else null
        callback.onReceiveValue(if (uri != null) arrayOf(uri) else null)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        pendingFileChooser?.onReceiveValue(null)
        pendingFileChooser = null
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
