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
    fun offlineServiceCalculatesCountifMatchAndFilterArrays() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executor = Executors.newSingleThreadExecutor()
        try {
            val formulaTests = linkedMapOf<String, String>()
            listOf("10", "20", "30", "40", "50", "60").forEachIndexed { index, value ->
                formulaTests["J${index + 2}"] = value
            }
            listOf("P01", "P02", "P03", "P04", "P05").forEachIndexed { index, value ->
                formulaTests["M${index + 2}"] = value
            }
            formulaTests["D2"] = "=COUNTIF($J$2:$J$7,\">10\")"
            formulaTests["D14"] = "=MATCH(\"P04\",$M$2:$M$6,0)"
            val workbook = mapOf(
                "Formula Tests" to formulaTests,
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
            val formulas = listOf(
                Triple("Formula Tests", "D2", "=COUNTIF($J$2:$J$7,\">10\")"),
                Triple("Formula Tests", "D14", "=MATCH(\"P04\",$M$2:$M$6,0)"),
                Triple("Sheet2", "E1", "=FILTER(Sheet1!B2:B4,Sheet1!C2:C4>2)")
            )
            val future = executor.submit<HyperFormulaCalculationResult> {
                HyperFormulaServiceClient.recalculateSnapshot(context, workbook, formulas)
            }
            val result = future.get(60, TimeUnit.SECONDS)
            assertEquals("5", result.formulaResults["Formula Tests!D2"])
            assertEquals("4", result.formulaResults["Formula Tests!D14"])
            assertEquals("Notebook", result.formulaResults["Sheet2!E1"])
            assertEquals("Pencil", result.spillResults["Sheet2!E2"])
        } finally {
            executor.shutdownNow()
        }
    }
}
