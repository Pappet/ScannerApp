package com.scanner.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.scanner.app.data.db.*
import com.scanner.app.data.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Supported file formats for exporting device inventory data.
 */
enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    CSV("CSV", "csv", "text/csv"),
    JSON("JSON", "json", "application/json"),
    PDF("PDF-Bericht", "pdf", "application/pdf")
}

/**
 * Filtering options for narrowing down the set of devices to export.
 */
data class ExportFilter(
    val categories: Set<DeviceCategory>? = null,  // null = all
    val favoritesOnly: Boolean = false,
    val sinceHours: Int? = null,                   // null = all time
    val includeSignalHistory: Boolean = false
)

/**
 * The outcome of an export operation, containing the generated file and its URI for sharing.
 */
data class ExportResult(
    val file: File,
    val format: ExportFormat,
    val deviceCount: Int,
    val uri: Uri? = null
)

/**
 * Orchestrates the extraction and formatting of device inventory data into shared files.
 * Supports CSV (spreadsheet), JSON (machine-readable), and PDF (printable report) formats.
 */
class ExportManager(private val context: Context) {

    private val repository = DeviceRepository(context)
    private val dateFormatter = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val fileNameFormatter = DateTimeFormatter
        .ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())

    /**
     * Executes the export process for a filtered set of devices.
     * Writes the data to a temporary file in the application's cache directory.
     *
     * @param format The target [ExportFormat].
     * @param filter Criteria for selecting devices [ExportFilter].
     * @return An [ExportResult] containing the file handle and a shareable URI.
     */
    suspend fun export(
        format: ExportFormat,
        filter: ExportFilter = ExportFilter()
    ): ExportResult = withContext(Dispatchers.IO) {
        val devices = getFilteredDevices(filter)
        val timestamp = fileNameFormatter.format(Instant.now())
        val fileName = "netzwerk_scan_$timestamp.${format.extension}"
        val file = File(context.cacheDir, fileName)

        when (format) {
            ExportFormat.CSV -> exportCsv(file, devices)
            ExportFormat.JSON -> exportJson(file, devices, filter.includeSignalHistory)
            ExportFormat.PDF -> exportPdf(file, devices)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        ExportResult(
            file = file,
            format = format,
            deviceCount = devices.size,
            uri = uri
        )
    }

    /**
     * Creates an [Intent.ACTION_SEND] for sharing the exported file with other applications.
     * Automatically grants temporary read permissions for the file URI.
     */
    fun share(result: ExportResult): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = result.format.mimeType
            putExtra(Intent.EXTRA_STREAM, result.uri)
            putExtra(Intent.EXTRA_SUBJECT, "Netzwerk-Scan Export")
            putExtra(
                Intent.EXTRA_TEXT,
                "Netzwerk-Scanner Export: ${result.deviceCount} Geräte (${result.format.label})"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }



    private fun exportCsv(file: File, devices: List<DiscoveredDeviceEntity>) {
        FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
            // BOM for Excel compatibility
            writer.write("\uFEFF")

            // Header
            writer.write("Name;Label;Adresse;Kategorie;Signalstärke (dBm);Zuerst gesehen;Zuletzt gesehen;Anzahl gesehen;Favorit;Notizen;Metadaten")
            writer.newLine()

            // Data rows
            for (device in devices) {
                val row = listOf(
                    CsvEscape.escape(device.name),
                    CsvEscape.escape(device.customLabel ?: ""),
                    device.address,
                    device.deviceCategory.displayName(),
                    device.lastSignalStrength?.toString() ?: "",
                    dateFormatter.format(device.firstSeen),
                    dateFormatter.format(device.lastSeen),
                    device.timesSeen.toString(),
                    if (device.isFavorite) "Ja" else "Nein",
                    CsvEscape.escape(device.notes ?: ""),
                    CsvEscape.escape(device.metadata ?: "")
                ).joinToString(";")

                writer.write(row)
                writer.newLine()
            }
        }
    }



    private suspend fun exportJson(
        file: File,
        devices: List<DiscoveredDeviceEntity>,
        includeHistory: Boolean
    ) {
        val root = JSONObject()
        root.put("exportDate", dateFormatter.format(Instant.now()))
        root.put("appVersion", "1.0")
        root.put("deviceCount", devices.size)

        // Statistics
        val stats = JSONObject()
        stats.put("total", devices.size)
        stats.put("wifi", devices.count { it.deviceCategory == DeviceCategory.WIFI })
        stats.put("bluetooth", devices.count {
            it.deviceCategory in listOf(
                DeviceCategory.BT_CLASSIC, DeviceCategory.BT_BLE, DeviceCategory.BT_DUAL
            )
        })
        stats.put("favorites", devices.count { it.isFavorite })
        root.put("statistics", stats)

        // Devices
        val devicesArray = JSONArray()
        for (device in devices) {
            val obj = JSONObject()
            obj.put("name", device.name)
            obj.put("customLabel", device.customLabel ?: JSONObject.NULL)
            obj.put("address", device.address)
            obj.put("category", device.deviceCategory.name)
            obj.put("categoryLabel", device.deviceCategory.displayName())
            obj.put("lastSignalStrength", device.lastSignalStrength ?: JSONObject.NULL)
            obj.put("firstSeen", device.firstSeen.toString())
            obj.put("lastSeen", device.lastSeen.toString())
            obj.put("timesSeen", device.timesSeen)
            obj.put("isFavorite", device.isFavorite)
            obj.put("notes", device.notes ?: JSONObject.NULL)

            // Parse metadata JSON
            device.metadata?.let { meta ->
                try {
                    obj.put("metadata", JSONObject(meta))
                } catch (_: Exception) {
                    obj.put("metadata", meta)
                }
            }

            devicesArray.put(obj)
        }
        root.put("devices", devicesArray)

        FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(root.toString(2))
        }
    }



    private fun exportPdf(file: File, devices: List<DiscoveredDeviceEntity>) {
        val document = PdfDocument()
        val pageWidth = 595   // A4
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - 2 * margin

        val titlePaint = Paint().apply {
            textSize = 18f; isFakeBoldText = true; color = 0xFF00897B.toInt()
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f; color = 0xFF666666.toInt()
        }
        val headerPaint = Paint().apply {
            textSize = 10f; isFakeBoldText = true; color = 0xFF333333.toInt()
        }
        val bodyPaint = Paint().apply {
            textSize = 9f; color = 0xFF444444.toInt()
        }
        val smallPaint = Paint().apply {
            textSize = 8f; color = 0xFF888888.toInt()
        }
        val linePaint = Paint().apply {
            color = 0xFFDDDDDD.toInt(); strokeWidth = 0.5f
        }
        val accentPaint = Paint().apply {
            color = 0xFF00897B.toInt(); strokeWidth = 2f
        }

        var pageNumber = 1
        var currentPage: PdfDocument.Page
        var canvas: Canvas
        var y: Float = 0f

        fun newPage(): Pair<PdfDocument.Page, Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            val page = document.startPage(pageInfo)
            return page to page.canvas
        }

        fun checkPageBreak(needed: Float): Boolean {
            return y + needed > pageHeight - margin
        }

        // First page
        val (firstPage, firstCanvas) = newPage()
        currentPage = firstPage
        canvas = firstCanvas
        y = margin

        // Title
        canvas.drawText("Netzwerk-Scanner Bericht", margin, y + 18f, titlePaint)
        y += 28f

        // Accent line
        canvas.drawLine(margin, y, margin + 120f, y, accentPaint)
        y += 16f

        // Export info
        canvas.drawText("Erstellt: ${dateFormatter.format(Instant.now())}", margin, y, subtitlePaint)
        y += 16f
        canvas.drawText("Geräte: ${devices.size}", margin, y, subtitlePaint)
        y += 10f

        // Category summary
        val wifiCount = devices.count { it.deviceCategory == DeviceCategory.WIFI }
        val btCount = devices.count {
            it.deviceCategory in listOf(
                DeviceCategory.BT_CLASSIC, DeviceCategory.BT_BLE, DeviceCategory.BT_DUAL
            )
        }
        val favCount = devices.count { it.isFavorite }
        canvas.drawText("WiFi: $wifiCount  |  Bluetooth: $btCount  |  Favoriten: $favCount", margin, y + 14f, subtitlePaint)
        y += 30f

        // Separator
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 16f

        // Table header
        fun drawTableHeader() {
            canvas.drawText("Name / Label", margin, y, headerPaint)
            canvas.drawText("Adresse", margin + 180f, y, headerPaint)
            canvas.drawText("Typ", margin + 320f, y, headerPaint)
            canvas.drawText("Signal", margin + 380f, y, headerPaint)
            canvas.drawText("Zuletzt", margin + 430f, y, headerPaint)
            y += 4f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 10f
        }

        drawTableHeader()

        // Device rows
        for (device in devices) {
            if (checkPageBreak(40f)) {
                document.finishPage(currentPage)
                val (newP, newC) = newPage()
                currentPage = newP
                canvas = newC
                y = margin
                drawTableHeader()
            }

            val displayName = device.customLabel ?: device.name
            val truncatedName = if (displayName.length > 28) displayName.take(26) + "…" else displayName

            canvas.drawText(truncatedName, margin, y, bodyPaint)
            canvas.drawText(device.address, margin + 180f, y, smallPaint)
            canvas.drawText(device.deviceCategory.shortName(), margin + 320f, y, bodyPaint)
            canvas.drawText(
                device.lastSignalStrength?.let { "$it dBm" } ?: "—",
                margin + 380f, y, bodyPaint
            )
            canvas.drawText(
                DateTimeFormatter.ofPattern("dd.MM. HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(device.lastSeen),
                margin + 430f, y, smallPaint
            )
            y += 14f

            // Notes (if any)
            device.notes?.let { notes ->
                val truncNotes = if (notes.length > 80) notes.take(78) + "…" else notes
                canvas.drawText("  ↳ $truncNotes", margin + 4f, y, smallPaint)
                y += 12f
            }

            // Favorites star
            if (device.isFavorite) {
                canvas.drawText("★", margin + 170f, y - 12f, Paint().apply {
                    textSize = 10f; color = 0xFFFFA000.toInt()
                })
            }

            // Light separator
            canvas.drawLine(margin + 4f, y, pageWidth - margin - 4f, y, Paint().apply {
                color = 0xFFEEEEEE.toInt(); strokeWidth = 0.3f
            })
            y += 6f
        }

        // Footer on last page
        y += 16f
        if (!checkPageBreak(30f)) {
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 12f
            canvas.drawText(
                "Generiert von Netzwerk-Scanner App",
                margin, y, smallPaint
            )
        }

        document.finishPage(currentPage)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
    }



    private suspend fun getFilteredDevices(filter: ExportFilter): List<DiscoveredDeviceEntity> {
        val flow = when {
            filter.favoritesOnly -> repository.observeFavorites()
            filter.sinceHours != null -> repository.observeRecentDevices(filter.sinceHours)
            else -> repository.observeAllDevices()
        }

        val devices = flow.first()

        return if (filter.categories != null) {
            devices.filter { it.deviceCategory in filter.categories }
        } else devices
    }

}
