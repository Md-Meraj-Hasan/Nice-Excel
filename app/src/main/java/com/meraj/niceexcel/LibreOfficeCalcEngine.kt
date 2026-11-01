package com.meraj.niceexcel

import android.app.Activity
import android.net.Uri
import android.os.Looper
import android.os.Build
import java.nio.ByteBuffer
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFWorkbook
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
    private var office: Any? = null

    @Volatile
    private var initializationFailure: Throwable? = null

    fun recalculateSnapshot(
        activity: Activity,
        workbookSnapshot: Map<String, Map<String, String>>
    ): Map<String, String> {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "LibreOffice recalculation must run off the Android UI thread"
        }
        check(Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            "The bundled LibreOffice Calc native engine is available for arm64-v8a only"
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
                    val columnIndex = ref.col.toInt()
                    if (ref.row < 0 || columnIndex < 0 || rawValue.isEmpty()) return@cellLoop
                    val row = sheet.getRow(ref.row) ?: sheet.createRow(ref.row)
                    val cell = row.getCell(columnIndex) ?: row.createCell(columnIndex)
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
                val kitClass = Class.forName("org.libreoffice.kit.LibreOfficeKit")
                kitClass.getMethod("initializeLibrary").invoke(null)
                kitClass.getMethod("init", Activity::class.java).invoke(null, activity)
                val handle = kitClass.getMethod("getLibreOfficeKitHandle").invoke(null) as? ByteBuffer
                    ?: error("LibreOfficeKit returned a null native handle after initialization")
                val officeClass = Class.forName("org.libreoffice.kit.Office")
                activeOffice = officeClass.getConstructor(ByteBuffer::class.java).newInstance(handle)
                office = activeOffice
            } catch (failure: Throwable) {
                initializationFailure = failure
                throw IllegalStateException("Unable to initialize the bundled LibreOffice Calc runtime", failure)
            }
        }

        val officeClass = activeOffice!!.javaClass
        fun loadDocument(url: String): Any? =
            officeClass.getMethod("documentLoad", String::class.java).invoke(activeOffice, url)
        fun officeError(): String =
            runCatching { officeClass.getMethod("getError").invoke(activeOffice)?.toString().orEmpty() }
                .getOrDefault("unknown LibreOfficeKit error")

        val inputUrl = Uri.fromFile(input).toString()
        val outputUrl = Uri.fromFile(output).toString()
        val document = loadDocument(inputUrl)
            ?: error("LibreOffice could not open the generated XLSX workbook: ${officeError()}")

        val documentClass = document.javaClass
        val expectedType = Class.forName("org.libreoffice.kit.Document")
            .getField("DOCTYPE_SPREADSHEET").getInt(null)
        fun checkSpreadsheet(candidate: Any) {
            val actualType = candidate.javaClass.getMethod("getDocumentType").invoke(candidate) as Int
            check(actualType == expectedType) { "LibreOffice opened a non-spreadsheet document" }
        }
        try {
            checkSpreadsheet(document)
            documentClass.getMethod(
                "postUnoCommand", String::class.java, String::class.java, java.lang.Boolean.TYPE
            ).invoke(document, ".uno:CalculateHard", "{}", false)
            documentClass.getMethod(
                "saveAs", String::class.java, String::class.java, String::class.java
            ).invoke(document, outputUrl, "Calc MS Excel 2007 XML", null)
        } finally {
            documentClass.getMethod("destroy").invoke(document)
        }

        check(output.isFile && output.length() > 0L) {
            "LibreOffice did not create a recalculated XLSX file"
        }

        val reopened = loadDocument(outputUrl)
            ?: error("LibreOffice could not reopen its recalculated XLSX output: ${officeError()}")
        try {
            checkSpreadsheet(reopened)
        } finally {
            reopened.javaClass.getMethod("destroy").invoke(reopened)
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
                        val address = CellReference(cell.rowIndex, cell.columnIndex.toShort())
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
