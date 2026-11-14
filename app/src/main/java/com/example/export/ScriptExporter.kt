package com.example.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.ExportFormat
import com.example.model.ExportOutcome
import com.example.model.ScriptLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScriptExporter {

    private const val PAGE_WIDTH = 595   // A4 width in PostScript points (72 dpi)
    private const val PAGE_HEIGHT = 842  // A4 height in PostScript points
    private const val MARGIN_X = 48
    private const val MARGIN_TOP = 54
    private const val MARGIN_BOTTOM = 54

    fun buildSuggestedFileName(seriesTitle: String, extension: String): String {
        val cleanBase = seriesTitle.trim()
            .replace(Regex("[^A-Za-z0-9\\-_ ]"), "")
            .replace(Regex("\\s+"), "_")
            .take(32)
            .ifBlank { "Manhwa_Recap" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${cleanBase}_$stamp.$extension"
    }

    suspend fun saveTxtToDownloads(
        context: Context,
        seriesTitle: String,
        script: String
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val fileName = buildSuggestedFileName(seriesTitle, "txt")
        val uri = createDownloadFileUri(context, fileName, "text/plain")
            ?: throw IllegalStateException("Could not create file in Downloads.")

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(script.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("Unable to open output stream for TXT file.")

        markDownloadComplete(context, uri)

        ExportOutcome(
            format = ExportFormat.TXT,
            fileName = fileName,
            uri = uri,
            mimeType = "text/plain",
            message = "Script saved to Downloads/$fileName"
        )
    }

    suspend fun writeTxtToUri(
        context: Context,
        uri: Uri,
        fileName: String,
        script: String
    ): ExportOutcome = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(script.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("Unable to open selected destination.")

        ExportOutcome(
            format = ExportFormat.TXT,
            fileName = fileName,
            uri = uri,
            mimeType = "text/plain",
            message = "Script saved as $fileName"
        )
    }

    suspend fun savePdfToDownloads(
        context: Context,
        seriesTitle: String,
        language: ScriptLanguage,
        panelCount: Int,
        script: String
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val fileName = buildSuggestedFileName(seriesTitle, "pdf")
        val uri = createDownloadFileUri(context, fileName, "application/pdf")
            ?: throw IllegalStateException("Could not create PDF in Downloads.")

        context.contentResolver.openOutputStream(uri)?.use { out ->
            writeFormattedPdf(
                outputStream = out,
                seriesTitle = seriesTitle,
                language = language,
                panelCount = panelCount,
                script = script
            )
        } ?: throw IllegalStateException("Unable to open output stream for PDF file.")

        markDownloadComplete(context, uri)

        ExportOutcome(
            format = ExportFormat.PDF,
            fileName = fileName,
            uri = uri,
            mimeType = "application/pdf",
            message = "PDF saved to Downloads/$fileName"
        )
    }

    suspend fun writePdfToUri(
        context: Context,
        uri: Uri,
        fileName: String,
        seriesTitle: String,
        language: ScriptLanguage,
        panelCount: Int,
        script: String
    ): ExportOutcome = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            writeFormattedPdf(
                outputStream = out,
                seriesTitle = seriesTitle,
                language = language,
                panelCount = panelCount,
                script = script
            )
        } ?: throw IllegalStateException("Unable to open selected destination.")

        ExportOutcome(
            format = ExportFormat.PDF,
            fileName = fileName,
            uri = uri,
            mimeType = "application/pdf",
            message = "PDF saved as $fileName"
        )
    }

    private fun createDownloadFileUri(
        context: Context,
        fileName: String,
        mimeType: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ManhwaRecap")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    private fun markDownloadComplete(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    /**
     * Uses StaticLayout so both English and Hindi (Devanagari) script characters
     * wrap, shape, and paginate accurately across multi-page A4 PDF sheets.
     */
    private fun writeFormattedPdf(
        outputStream: OutputStream,
        seriesTitle: String,
        language: ScriptLanguage,
        panelCount: Int,
        script: String
    ) {
        val document = PdfDocument()
        val contentWidth = PAGE_WIDTH - (MARGIN_X * 2)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D92648")
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0DCEB")
            strokeWidth = 1f
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#131521")
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#565C73")
            textSize = 10.5f
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1D29")
            textSize = 12f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#757B94")
            textSize = 9.5f
        }

        val resolvedTitle = seriesTitle.trim().ifBlank { "Manhwa Recap Script" }
        val dateText = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val metaLine = "${language.displayName}  •  $panelCount panels  •  $dateText"

        val titleLayout = StaticLayout.Builder
            .obtain(resolvedTitle, 0, resolvedTitle.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.15f)
            .build()

        val bodyLayout = StaticLayout.Builder
            .obtain(script.trim(), 0, script.trim().length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3.5f, 1.38f)
            .setIncludePad(true)
            .build()

        val firstPageHeaderHeight = 10 + titleLayout.height + 24 + 14
        val totalLines = bodyLayout.lineCount
        var currentLine = 0
        var pageNumber = 1

        // Pre-compute page line slices so we can render accurate "Page X of Y" footers
        val pageSlices = mutableListOf<IntRange>()
        while (currentLine < totalLines || pageSlices.isEmpty()) {
            val topY = if (pageSlices.isEmpty()) {
                MARGIN_TOP + firstPageHeaderHeight
            } else {
                MARGIN_TOP
            }
            val maxBottomY = PAGE_HEIGHT - MARGIN_BOTTOM - 22
            val availableHeight = (maxBottomY - topY).coerceAtLeast(40)

            val startLine = currentLine
            val startTop = if (startLine < totalLines) bodyLayout.getLineTop(startLine) else 0

            var endLine = startLine
            while (endLine < totalLines) {
                val lineBottom = bodyLayout.getLineBottom(endLine) - startTop
                if (lineBottom > availableHeight && endLine > startLine) {
                    break
                }
                endLine++
            }
            pageSlices.add(startLine until endLine)
            currentLine = endLine
            if (currentLine >= totalLines) break
        }

        val totalPages = pageSlices.size

        pageSlices.forEachIndexed { index, lineRange ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var cursorY = MARGIN_TOP.toFloat()

            if (index == 0) {
                // Crimson editorial top bar
                canvas.drawRect(
                    MARGIN_X.toFloat(),
                    cursorY,
                    (MARGIN_X + 44).toFloat(),
                    cursorY + 4f,
                    accentPaint
                )
                cursorY += 12f

                canvas.save()
                canvas.translate(MARGIN_X.toFloat(), cursorY)
                titleLayout.draw(canvas)
                canvas.restore()
                cursorY += titleLayout.height + 6f

                canvas.drawText(metaLine, MARGIN_X.toFloat(), cursorY + 10f, metaPaint)
                cursorY += 20f

                canvas.drawLine(
                    MARGIN_X.toFloat(),
                    cursorY,
                    (PAGE_WIDTH - MARGIN_X).toFloat(),
                    cursorY,
                    dividerPaint
                )
                cursorY += 14f
            }

            if (!lineRange.isEmpty()) {
                val firstLineTop = bodyLayout.getLineTop(lineRange.first)
                val lastLineBottom = bodyLayout.getLineBottom(lineRange.last)

                canvas.save()
                canvas.clipRect(
                    MARGIN_X.toFloat(),
                    cursorY,
                    (PAGE_WIDTH - MARGIN_X).toFloat(),
                    cursorY + (lastLineBottom - firstLineTop)
                )
                canvas.translate(MARGIN_X.toFloat(), cursorY - firstLineTop)
                bodyLayout.draw(canvas)
                canvas.restore()
            }

            // Footer
            val footerY = (PAGE_HEIGHT - 28).toFloat()
            canvas.drawLine(
                MARGIN_X.toFloat(),
                footerY - 14f,
                (PAGE_WIDTH - MARGIN_X).toFloat(),
                footerY - 14f,
                dividerPaint
            )
            canvas.drawText(
                "Manhwa Recap Studio",
                MARGIN_X.toFloat(),
                footerY,
                footerPaint
            )
            val pageLabel = "Page $pageNumber of $totalPages"
            val labelWidth = footerPaint.measureText(pageLabel)
            canvas.drawText(
                pageLabel,
                (PAGE_WIDTH - MARGIN_X) - labelWidth,
                footerY,
                footerPaint
            )

            document.finishPage(page)
            pageNumber++
        }

        try {
            document.writeTo(outputStream)
            outputStream.flush()
        } finally {
            document.close()
        }
    }
}
