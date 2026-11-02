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
    private const val TIMEOUT_SECONDS = 45L

    fun recalculateSnapshot(
        context: Context,
        allSheets: Map<String, Map<String, String>>,
        formulas: List<Triple<String, String, String>>
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

        val appContext = context.applicationContext
        val payloadFile = File.createTempFile("niceexcel-hf-", ".json", appContext.cacheDir)
        payloadFile.writeText(payload.toString(), Charsets.UTF_8)
        val intent = Intent(appContext, HyperFormulaEngineService::class.java)
        val latch = CountDownLatch(1)
        val responseJson = AtomicReference<String?>()
        val failure = AtomicReference<Throwable?>()
        val responseMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == HyperFormulaEngineService.MSG_RESULT) {
                responseJson.set(message.data?.getString(HyperFormulaEngineService.KEY_RESULT))
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
            appContext.startService(intent)
            bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            check(bound) { "Unable to bind the HyperFormula calculation process" }
            check(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "HyperFormula calculation timed out after $TIMEOUT_SECONDS seconds"
            }
            failure.get()?.let { throw it }
            val responseText = responseJson.get()
                ?: throw IllegalStateException("HyperFormula returned an empty response")
            val response = JSONObject(responseText)
            check(response.optBoolean("ok", false)) {
                response.optString("error", "HyperFormula calculation failed")
            }
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
        }
    }
}
