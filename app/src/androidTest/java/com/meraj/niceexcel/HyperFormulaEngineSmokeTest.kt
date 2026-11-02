package com.meraj.niceexcel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HyperFormulaEngineSmokeTest {
    @Test
    fun offlineServiceCalculatesWithoutUsingTheMainUiThread() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Map<String, String>> {
                HyperFormulaServiceClient.recalculateSnapshot(
                    context,
                    mapOf("Sheet1" to mapOf("A1" to "5", "A2" to "=A1*2")),
                    listOf(Triple("Sheet1", "A2", "=A1*2"))
                )
            }
            val result = future.get(60, TimeUnit.SECONDS)
            assertEquals("10", result["Sheet1!A2"])
        } finally {
            executor.shutdownNow()
        }
    }
}
