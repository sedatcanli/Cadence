package com.cadence.speech.ui

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.cadence.speech.R
import com.cadence.speech.domain.SessionMode
import com.cadence.speech.domain.SessionReport
import com.cadence.speech.ui.screens.formatDuration
import java.io.File

private const val CHART_WIDTH = 1080
private const val CHART_HEIGHT = 720
private const val PACE_MAX = 240

fun saveChartImage(context: Context, report: SessionReport): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    if (report.scoreHistory.isEmpty()) return false

    return runCatching {
        val bitmap = renderChart(context, report)
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "cadence-${report.id.take(8)}.png"
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Cadence"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        val written = resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: false

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        written
    }.getOrDefault(false)
}

fun shareChartImage(context: Context, report: SessionReport) {
    runCatching {
        val bitmap = renderChart(context, report)
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "cadence-chart-${report.id.take(8)}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.report_chart))
            clipData = ClipData.newRawUri("chart", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.report_chart_share))
        )
    }
}

private fun renderChart(context: Context, report: SessionReport): Bitmap {
    val bitmap = Bitmap.createBitmap(CHART_WIDTH, CHART_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.parseColor("#0B1220"))

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val left = 72f
    val right = CHART_WIDTH - 72f
    val top = 210f
    val bottom = CHART_HEIGHT - 96f

    paint.color = Color.parseColor("#E6ECFF")
    paint.textSize = 46f
    paint.isFakeBoldText = true
    canvas.drawText(context.getString(R.string.report_chart_title), left, 82f, paint)

    paint.isFakeBoldText = false
    paint.color = Color.parseColor("#9AA8C7")
    paint.textSize = 30f
    canvas.drawText(modeLabel(context, report.mode) + " · " + formatDuration(report.durationMs), left, 130f, paint)

    val score = report.scoreHistory.lastOrNull() ?: report.score
    val pace = report.paceHistory.lastOrNull() ?: report.pace
    val legendY = 172f
    var legendX = left
    legendX = drawLegend(canvas, paint, legendX, legendY, "#7C9CFF", "${context.getString(R.string.chart_legend_score)} $score")
    legendX = drawLegend(canvas, paint, legendX, legendY, "#F0B429", "${context.getString(R.string.chart_legend_pace)} $pace")
    legendX = drawLegend(canvas, paint, legendX, legendY, "#E5484D", "${context.getString(R.string.chart_legend_filler)} ${report.fillerCount}")
    drawLegend(canvas, paint, legendX, legendY, "#38BDF8", "${context.getString(R.string.chart_legend_pause)} ${report.longPauseCount}")

    paint.color = Color.parseColor("#1C2740")
    paint.strokeWidth = 2f
    for (index in 1..3) {
        val y = top + (bottom - top) * index / 4f
        canvas.drawLine(left, y, right, y, paint)
    }

    val scores = report.scoreHistory
    if (scores.size < 2) {
        paint.color = Color.parseColor("#9AA8C7")
        paint.textSize = 28f
        canvas.drawText(
            context.getString(R.string.report_chart),
            left,
            (top + bottom) / 2f,
            paint
        )
        return bitmap
    }

    val count = scores.size
    val denom = (count - 1).toFloat()
    fun xForSecond(second: Int): Float = left + (second / denom) * (right - left)
    fun scoreY(value: Int): Float = bottom - (value.coerceIn(0, 100) / 100f) * (bottom - top)
    fun paceY(value: Int): Float = bottom - (value.coerceIn(0, PACE_MAX) / PACE_MAX.toFloat()) * (bottom - top)

    paint.strokeWidth = 3f
    paint.color = Color.parseColor("#38BDF8")
    paint.alpha = 140
    report.pauseMarkers.distinct().forEach { second ->
        val x = xForSecond(second)
        canvas.drawLine(x, bottom - 24f, x, bottom, paint)
    }
    paint.alpha = 255

    if (report.paceHistory.size == count) {
        val pacePath = Path()
        report.paceHistory.forEachIndexed { index, value ->
            val x = left + index / denom * (right - left)
            val y = paceY(value)
            if (index == 0) pacePath.moveTo(x, y) else pacePath.lineTo(x, y)
        }
        paint.color = Color.parseColor("#F0B429")
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        canvas.drawPath(pacePath, paint)
    }

    val scorePath = Path()
    scores.forEachIndexed { index, value ->
        val x = left + index / denom * (right - left)
        val y = scoreY(value)
        if (index == 0) scorePath.moveTo(x, y) else scorePath.lineTo(x, y)
    }
    paint.color = Color.parseColor("#7C9CFF")
    paint.strokeWidth = 5f
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    canvas.drawPath(scorePath, paint)

    paint.style = Paint.Style.FILL
    paint.color = Color.parseColor("#E5484D")
    report.fillerMarkers.distinct().forEach { second ->
        canvas.drawCircle(xForSecond(second), bottom - 6f, 6f, paint)
    }

    paint.color = Color.parseColor("#7C9CFF")
    canvas.drawCircle(right, scoreY(scores.last()), 8f, paint)

    return bitmap
}

private fun drawLegend(
    canvas: Canvas,
    paint: Paint,
    x: Float,
    y: Float,
    color: String,
    text: String
): Float {
    paint.color = Color.parseColor(color)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(x + 8f, y - 10f, 8f, paint)

    paint.color = Color.parseColor("#9AA8C7")
    paint.textSize = 28f
    canvas.drawText(text, x + 24f, y, paint)

    return x + 40f + paint.measureText(text)
}

private fun modeLabel(context: Context, mode: SessionMode): String = context.getString(
    when (mode) {
        SessionMode.INTERVIEW -> R.string.mode_interview
        SessionMode.PRESENTATION -> R.string.mode_presentation
        SessionMode.FREE -> R.string.mode_free
    }
)
