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
    fun offlineServiceCalculatesAndReturnsFilterArrayCells() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executor = Executors.newSingleThreadExecutor()
        try {
            val workbook = mapOf(
                "Sheet1" to mapOf(
                    "A1" to "ID", "B1" to "Item", "C1" to "Price",
                    "A2" to "101", "B2" to "Pen", "C2" to "2",
                    "A3" to "105", "B3" to "Notebook", "C3" to "5",
                    "A4" to "108", "B4" to "Pencil", "C4" to "3"
                ),
                "Sheet2" to mapOf(
                    "E1" to "=FILTER(Sheet1!B2:B4,Sheet1!C2:C4>2)"
                )
            )
            val future = executor.submit<HyperFormulaCalculationResult> {
                HyperFormulaServiceClient.recalculateSnapshot(
                    context,
                    workbook,
                    listOf(Triple("Sheet2", "E1", "=FILTER(Sheet1!B2:B4,Sheet1!C2:C4>2)"))
                )
            }
            val result = future.get(60, TimeUnit.SECONDS)
            assertEquals("Notebook", result.formulaResults["Sheet2!E1"])
            assertEquals("Pencil", result.spillResults["Sheet2!E2"])
        } finally {
            executor.shutdownNow()
        }
    }
}
