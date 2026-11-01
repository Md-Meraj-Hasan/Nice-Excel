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
        val connected = CountDownLatch(1)
        val replied = CountDownLatch(1)
        val remote = AtomicReference<Messenger?>()
        val failure = AtomicReference<Throwable?>()
        val result = AtomicReference<Map<String, String>?>()

        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            try {
                val data = message.data
                if (!data.getBoolean(LibreOfficeEngineService.KEY_OK, false)) {
                    failure.set(IllegalStateException(
                        data.getString(LibreOfficeEngineService.KEY_DETAIL)
                            ?: "LibreOffice service returned an unknown error"
                    ))
                } else {
                    val keys = data.getStringArrayList(LibreOfficeEngineService.KEY_RESULT_KEYS)
                        ?: arrayListOf()
                    val values = data.getStringArrayList(LibreOfficeEngineService.KEY_RESULT_VALUES)
                        ?: arrayListOf()
                    if (keys.size != values.size) {
                        failure.set(IllegalStateException("LibreOffice returned mismatched result arrays"))
                    } else {
                        result.set(keys.indices.associate { keys[it] to values[it] })
                    }
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

        val intent = Intent(appContext, LibreOfficeEngineService::class.java)
        check(appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            "Unable to bind the isolated LibreOffice service"
        }

        try {
            check(connected.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out waiting for the LibreOffice service to connect"
            }
            failure.get()?.let { throw it }
            val service = remote.get()
                ?: throw IllegalStateException("LibreOffice service connection is unavailable")

            val serializableSnapshot = HashMap<String, HashMap<String, String>>()
            snapshot.forEach { (sheet, cells) ->
                serializableSnapshot[sheet] = HashMap(cells)
            }
            val request = Message.obtain(null, LibreOfficeEngineService.MSG_RECALCULATE_SNAPSHOT)
            request.replyTo = replyMessenger
            request.data = Bundle().apply {
                putSerializable(LibreOfficeEngineService.KEY_SNAPSHOT, serializableSnapshot)
            }
            service.send(request)

            check(replied.await(CALC_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                "LibreOffice calculation timed out"
            }
            failure.get()?.let { throw it }
            return result.get()
                ?: throw IllegalStateException("LibreOffice returned no formula results")
        } finally {
            runCatching { appContext.unbindService(connection) }
        }
    }
}
