package com.meraj.niceexcel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Synchronous worker-thread client for the isolated LibreOffice process.
 * Never call from the Android main thread. A failed/dead native service is
 * reported to the caller so the existing formula evaluator can remain fallback.
 */
object LibreOfficeServiceClient {
    private const val SERVICE_TIMEOUT_SECONDS = 30L
    private const val CALC_TIMEOUT_MINUTES = 10L

    fun recalculateSnapshot(
        context: Context,
        snapshot: Map<String, Map<String, String>>
    ): Map<String, String> {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "LibreOffice service calls must run off the Android UI thread"
        }
        if (snapshot.values.none { cells -> cells.values.any { it.trimStart().startsWith("=") } }) {
            return emptyMap()
        }

        val appContext = context.applicationContext
        // Prepare XLSX locally and pass only file paths over Binder. A 13-sheet workbook
        // can exceed Binder's 1 MB transaction limit when Java-serialized as a Bundle.
        val inputFile = LibreOfficeCalcEngine.createSnapshotWorkbook(appContext, snapshot)
        val outputFile = java.io.File.createTempFile(
            "calculated-", ".xlsx", inputFile.parentFile
        ).apply { delete() }

        val connected = CountDownLatch(1)
        val replied = CountDownLatch(1)
        val remote = AtomicReference<Messenger?>()
        val failure = AtomicReference<Throwable?>()

        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            try {
                val data = message.data
                if (!data.getBoolean(LibreOfficeEngineService.KEY_OK, false)) {
                    failure.set(IllegalStateException(
                        data.getString(LibreOfficeEngineService.KEY_DETAIL)
                            ?: "LibreOffice service returned an unknown error"
                    ))
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                replied.countDown()
            }
            true
        })

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) {
                    failure.set(IllegalStateException("LibreOffice service returned an empty binder"))
                } else {
                    remote.set(Messenger(binder))
                }
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                failure.compareAndSet(null, IllegalStateException("LibreOffice isolated process disconnected"))
                connected.countDown()
                replied.countDown()
            }

            override fun onBindingDied(name: ComponentName?) {
                failure.compareAndSet(null, IllegalStateException("LibreOffice service binding died"))
                connected.countDown()
                replied.countDown()
            }

            override fun onNullBinding(name: ComponentName?) {
                failure.compareAndSet(null, IllegalStateException("LibreOffice service returned a null binding"))
                connected.countDown()
                replied.countDown()
            }
        }

        var bound = false
        try {
            bound = appContext.bindService(
                Intent(appContext, LibreOfficeEngineService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
            check(bound) { "Unable to bind the isolated LibreOffice service" }

            check(connected.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out waiting for the LibreOffice service to connect"
            }
            failure.get()?.let { throw it }
            val service = remote.get()
                ?: throw IllegalStateException("LibreOffice service connection is unavailable")

            val request = Message.obtain(null, LibreOfficeEngineService.MSG_RECALCULATE_XLSX)
            request.replyTo = replyMessenger
            request.data = Bundle().apply {
                putString(LibreOfficeEngineService.KEY_INPUT_PATH, inputFile.absolutePath)
                putString(LibreOfficeEngineService.KEY_OUTPUT_PATH, outputFile.absolutePath)
            }
            service.send(request)

            check(replied.await(CALC_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                "LibreOffice calculation timed out"
            }
            failure.get()?.let { throw it }
            check(outputFile.isFile && outputFile.length() > 0L) {
                "LibreOffice returned success but did not create a calculated workbook"
            }
            return LibreOfficeCalcEngine.readFormulaResults(outputFile)
        } finally {
            if (bound) runCatching { appContext.unbindService(connection) }
            inputFile.delete()
            outputFile.delete()
        }
    }
}
