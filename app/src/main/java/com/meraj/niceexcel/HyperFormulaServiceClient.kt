package com.meraj.niceexcel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.File

/**
 * Synchronous worker-thread adapter for the isolated HyperFormula WebView process.
 * Call only from NiceExcel's bounded backgroundExecutor, never from the UI thread.
 */
internal data class HyperFormulaCalculationResult(
    val formulaResults: Map<String, String>,
    val spillResults: Map<String, String>
)

internal object HyperFormulaServiceClient {
    private const val TAG = "NiceExcel-HyperFormula"
    private const val TIMEOUT_SECONDS = 60L

    fun recalculateSnapshot(
        context: Context,
        allSheets: Map<String, Map<String, String>>,
        formulas: List<Triple<String, String, String>>,
        visibleRowsBySheet: Map<String, Set<Int>> = emptyMap()
    ): HyperFormulaCalculationResult {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "HyperFormula service calls must run off the Android UI thread"
        }
        if (formulas.isEmpty()) return HyperFormulaCalculationResult(emptyMap(), emptyMap())

        val payload = JSONObject()
        val sheetsJson = JSONObject()
        allSheets.forEach { (sheetName, cells) ->
            val cellsJson = JSONObject()
            cells.forEach { (address, value) -> cellsJson.put(address, value) }
            sheetsJson.put(sheetName, cellsJson)
        }
        val formulasJson = JSONArray()
        formulas.forEach { (sheet, address, _) ->
            formulasJson.put(JSONObject().put("sheet", sheet).put("address", address))
        }
        payload.put("sheets", sheetsJson)
        payload.put("formulas", formulasJson)
        val visibleRowsJson = JSONObject()
        visibleRowsBySheet.forEach { (name, rows) ->
            visibleRowsJson.put(name, JSONArray(rows.sorted()))
        }
        payload.put("visibleRowsBySheet", visibleRowsJson)

        val appContext = context.applicationContext
        val payloadFile = File.createTempFile("niceexcel-hf-", ".json", appContext.cacheDir)
        payloadFile.writeText(payload.toString(), Charsets.UTF_8)
        val intent = Intent(appContext, HyperFormulaEngineService::class.java)
        val latch = CountDownLatch(1)
        val responsePath = AtomicReference<String?>()
        val failure = AtomicReference<Throwable?>()
        val responseMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == HyperFormulaEngineService.MSG_RESULT) {
                responsePath.set(message.data?.getString(HyperFormulaEngineService.KEY_RESULT_PATH))
                latch.countDown()
                true
            } else false
        })
        var bound = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
                try {
                    val service = Messenger(requireNotNull(binder) { "HyperFormula service returned a null binder" })
                    val request = Message.obtain(null, HyperFormulaEngineService.MSG_RECALCULATE)
                    request.data = Bundle().apply {
                        putString(HyperFormulaEngineService.KEY_PAYLOAD_PATH, payloadFile.absolutePath)
                    }
                    request.replyTo = responseMessenger
                    service.send(request)
                } catch (error: Throwable) {
                    failure.set(error)
                    latch.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                failure.compareAndSet(null, IllegalStateException("HyperFormula service disconnected"))
                latch.countDown()
            }

            override fun onBindingDied(name: ComponentName?) {
                failure.compareAndSet(null, IllegalStateException("HyperFormula service binding died"))
                latch.countDown()
            }

            override fun onNullBinding(name: ComponentName?) {
                failure.compareAndSet(null, IllegalStateException("HyperFormula service returned a null binding"))
                latch.countDown()
            }
        }

        try {
            // Keep the calculation process and its dependency graph warm between edits.
            Log.i(TAG, "CLIENT_BIND_BEGIN sheets=" + allSheets.size + " formulas=" + formulas.size + " payloadBytes=" + payloadFile.length())
            appContext.startService(intent)
            bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            check(bound) { "Unable to bind the HyperFormula calculation process" }
            Log.i(TAG, "CLIENT_BIND_OK waitingForServiceResponse=true")
            check(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "HyperFormula calculation timed out after $TIMEOUT_SECONDS seconds"
            }
            failure.get()?.let { throw it }
            Log.i(TAG, "CLIENT_RESPONSE_RECEIVED pathPresent=" + (responsePath.get() != null))
            val resultPath = responsePath.get()
                ?: throw IllegalStateException("HyperFormula returned an empty response path")
            val resultFile = File(resultPath).canonicalFile
            val cacheRoot = appContext.cacheDir.canonicalFile
            check(resultFile.path.startsWith(cacheRoot.path + File.separator)) {
                "HyperFormula response must be inside the app cache directory"
            }
            val responseText = resultFile.readText(Charsets.UTF_8)
            runCatching { resultFile.delete() }
            val response = JSONObject(responseText)
            check(response.optBoolean("ok", false)) {
                response.optString("error", "HyperFormula calculation failed")
            }
            Log.i(TAG, "CLIENT_RESULT_OK formulaResults=" + (response.optJSONObject("results")?.length() ?: 0) + " spillResults=" + (response.optJSONObject("spill")?.length() ?: 0))
            fun parseValues(values: JSONObject?): Map<String, String> {
                val output = linkedMapOf<String, String>()
                val source = values ?: JSONObject()
                val keys = source.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = source.opt(key)
                    output[key] = when (value) {
                        null, JSONObject.NULL -> ""
                        is String -> value
                        else -> value.toString()
                    }
                }
                return output
            }
            return HyperFormulaCalculationResult(
                formulaResults = parseValues(response.optJSONObject("results")),
                spillResults = parseValues(response.optJSONObject("spill"))
            )
        } catch (error: Throwable) {
            Log.e(TAG, "HyperFormula calculation request failed", error)
            throw error
        } finally {
            if (bound) runCatching { appContext.unbindService(connection) }
            runCatching { payloadFile.delete() }
            responsePath.get()?.let { path -> runCatching { File(path).delete() } }
        }
    }
}
