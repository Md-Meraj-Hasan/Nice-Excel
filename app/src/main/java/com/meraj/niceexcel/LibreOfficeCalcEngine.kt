package com.meraj.niceexcel

import android.app.Activity
import android.net.Uri
import android.os.Looper
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * LibreOffice Calc is the preferred formula evaluator for the native Android app.
 *
 * All LibreOfficeKit JNI calls are serialized on one dedicated thread. The NiceExcel
 * grid remains independent of LibreOffice's document renderer; this adapter exchanges
 * XLSX workbooks and returns cached formula results to the existing grid.
 *
 * Call recalculateSnapshot() from a background worker, never from the Android UI thread.
 */
internal object LibreOfficeCalcEngine {
    private val loThread = Executors.newSingleThreadExecutor { task ->
        Thread(task, "NiceExcel-LibreOfficeKit").apply { isDaemon = true }
    }

    @Volatile
    private var office: Office? = null

    @Volatile
    private var initializationFailure: Throwable? = null

    fun recalculateSnapshot(
        activity: Activity,
        workbookSnapshot: Map<String, Map<String, String>>
    ): Map<String, String> {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "LibreOffice recalculation must run off the Android UI thread"
        }
        if (workbookSnapshot.values.none { cells -> cells.values.any { it.trimStart().startsWith("=") } }) {
            return emptyMap()
        }

        val workDir = File(activity.cacheDir, "niceexcel-libreoffice").apply { mkdirs() }
        val input = File.createTempFile("workbook-in-", ".xlsx", workDir)
        val output = File.createTempFile("workbook-out-", ".xlsx", workDir)
        output.delete()

        try {
            writeSnapshot(input, workbookSnapshot)
            loThread.submit(Callable {
                recalculateOnLibreOfficeThread(activity, input, output)
            }).get(10, TimeUnit.MINUTES)
            return readCachedFormulaResults(output)
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun writeSnapshot(file: File, snapshot: Map<String, Map<String, String>>) {
        XSSFWorkbook().use { workbook ->
            snapshot.forEach { (sheetName, values) ->
                val sheet = workbook.createSheet(sheetName)
                values.forEach cellLoop@{ (address, rawValue) ->
                    val ref = runCatching { CellReference(address) }.getOrNull() ?: return@cellLoop
                    if (ref.row < 0 || ref.col < 0 || rawValue.isEmpty()) return@cellLoop
                    val row = sheet.getRow(ref.row) ?: sheet.createRow(ref.row)
                    val cell = row.getCell(ref.col) ?: row.createCell(ref.col)
                    val value = rawValue.trim()
                    when {
                        value.startsWith("=") -> cell.cellFormula = value.drop(1)
                        value.equals("TRUE", ignoreCase = true) -> cell.setCellValue(true)
                        value.equals("FALSE", ignoreCase = true) -> cell.setCellValue(false)
                        value.toDoubleOrNull() != null -> cell.setCellValue(value.toDouble())
                        else -> cell.setCellValue(rawValue)
                    }
                }
            }
            FileOutputStream(file).use { workbook.write(it) }
        }
    }

    private fun recalculateOnLibreOfficeThread(activity: Activity, input: File, output: File) {
        var activeOffice = office
        if (activeOffice == null) {
            initializationFailure?.let {
                throw IllegalStateException("LibreOfficeKit initialization previously failed", it)
            }
            try {
                LibreOfficeKit.initializeLibrary()
                LibreOfficeKit.init(activity)
                val handle = LibreOfficeKit.getLibreOfficeKitHandle()
                    ?: error("LibreOfficeKit returned a null native handle after initialization")
                activeOffice = Office(handle)
                office = activeOffice
            } catch (failure: Throwable) {
                initializationFailure = failure
                throw IllegalStateException("Unable to initialize the bundled LibreOffice Calc runtime", failure)
            }
        }

        val inputUrl = Uri.fromFile(input).toString()
        val outputUrl = Uri.fromFile(output).toString()
        val document = activeOffice!!.documentLoad(inputUrl)
            ?: error("LibreOffice could not open the generated XLSX workbook: ${activeOffice!!.getError()}")

        try {
            check(document.documentType == org.libreoffice.kit.Document.DOCTYPE_SPREADSHEET) {
                "LibreOffice opened the workbook as a non-spreadsheet document"
            }
            document.postUnoCommand(".uno:CalculateHard", "{}", false)
            document.saveAs(outputUrl, "Calc MS Excel 2007 XML", null)
        } finally {
            document.destroy()
        }

        check(output.isFile && output.length() > 0L) {
            "LibreOffice did not create a recalculated XLSX file"
        }

        val reopened = activeOffice!!.documentLoad(outputUrl)
            ?: error("LibreOffice could not reopen its recalculated XLSX output: ${activeOffice!!.getError()}")
        try {
            check(reopened.documentType == org.libreoffice.kit.Document.DOCTYPE_SPREADSHEET) {
                "Recalculated output is not recognized as a spreadsheet"
            }
        } finally {
            reopened.destroy()
        }
    }

    private fun readCachedFormulaResults(file: File): Map<String, String> {
        val results = linkedMapOf<String, String>()
        WorkbookFactory.create(file).use { workbook ->
            val formatter = org.apache.poi.ss.usermodel.DataFormatter()
            for (sheet in workbook) {
                for (row in sheet) {
                    for (cell in row) {
                        if (cell.cellType != CellType.FORMULA) continue
                        val address = CellReference(cell.rowIndex, cell.columnIndex)
                            .formatAsString().replace("$", "")
                        val value = when (cell.cachedFormulaResultType) {
                            CellType.NUMERIC -> cell.numericCellValue.toString()
                            CellType.STRING -> cell.stringCellValue
                            CellType.BOOLEAN -> if (cell.booleanCellValue) "TRUE" else "FALSE"
                            CellType.ERROR -> runCatching {
                                FormulaError.forInt(cell.errorCellValue.toInt()).string
                            }.getOrDefault("#ERROR")
                            CellType.BLANK -> ""
                            else -> formatter.formatCellValue(cell)
                        }
                        results["${sheet.sheetName}!$address"] = value
                    }
                }
            }
        }
        return results
    }
}
