package com.cadence.speech.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cadence.speech.R
import com.cadence.speech.ui.theme.Accent
import com.cadence.speech.ui.theme.Primary

@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    strokeWidth: Dp = 14.dp,
    caption: String = stringResource(R.string.report_score)
) {
    val animated by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        label = "score"
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = if (animated > 0.6f) Primary else Accent,
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = caption.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ScoreChart(
    scoreHistory: List<Int>,
    paceHistory: List<Int>,
    fillerMarkers: List<Int>,
    pauseMarkers: List<Int>,
    modifier: Modifier = Modifier
) {
    val scoreColor = Primary
    val paceColor = Accent
    val fillerColor = MaterialTheme.colorScheme.error
    val pauseColor = PauseMarker
    val gridColor = Color.White.copy(alpha = 0.07f)

    Canvas(modifier = modifier) {
        val gridStroke = 1.dp.toPx()
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = gridStroke
            )
        }

        if (scoreHistory.size < 2) return@Canvas

        val count = scoreHistory.size
        val denominators = (count - 1).toFloat()
        fun xForSecond(second: Int): Float =
            (second / denominators * size.width).coerceIn(0f, size.width)

        fun scoreY(value: Int): Float =
            size.height - (value.coerceIn(0, 100) / 100f) * size.height

        fun paceY(value: Int): Float =
            size.height - (value.coerceIn(0, PACE_CHART_MAX) / PACE_CHART_MAX.toFloat()) * size.height

        pauseMarkers.distinct().forEach { second ->
            val x = xForSecond(second)
            drawLine(
                color = pauseColor.copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(x, size.height - 12.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        if (paceHistory.size == count) {
            val pacePath = androidx.compose.ui.graphics.Path()
            paceHistory.forEachIndexed { index, value ->
                val x = index / denominators * size.width
                val y = paceY(value)
                if (index == 0) pacePath.moveTo(x, y) else pacePath.lineTo(x, y)
            }
            drawPath(
                path = pacePath,
                color = paceColor.copy(alpha = 0.9f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val scorePath = androidx.compose.ui.graphics.Path()
        scoreHistory.forEachIndexed { index, value ->
            val x = index / denominators * size.width
            val y = scoreY(value)
            if (index == 0) scorePath.moveTo(x, y) else scorePath.lineTo(x, y)
        }
        drawPath(
            path = scorePath,
            color = scoreColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        fillerMarkers.distinct().forEach { second ->
            drawCircle(
                color = fillerColor,
                radius = 2.5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    xForSecond(second),
                    size.height - 3.dp.toPx()
                )
            )
        }

        val lastX = size.width
        val lastY = scoreY(scoreHistory.last())
        drawCircle(
            color = scoreColor,
            radius = 3.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(lastX, lastY)
        )
    }
}

@Composable
fun ChartLegend(
    score: Int,
    pace: Int,
    fillerCount: Int,
    pauseCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(
            color = Primary,
            text = "${stringResource(R.string.chart_legend_score)} $score"
        )
        LegendItem(
            color = Accent,
            text = "${stringResource(R.string.chart_legend_pace)} $pace"
        )
        LegendItem(
            color = MaterialTheme.colorScheme.error,
            text = "${stringResource(R.string.chart_legend_filler)} $fillerCount"
        )
        LegendItem(
            color = PauseMarker,
            text = "${stringResource(R.string.chart_legend_pause)} $pauseCount"
        )
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val PauseMarker = Color(0xFF38BDF8)

private const val PACE_CHART_MAX = 240

@Composable
fun MetricRow(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Primary,
            trackColor = Color.White.copy(alpha = 0.08f)
        )
    }
}

@Composable
fun MainScaffold(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedRoute == "home",
                    onClick = { onNavigate("home") },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_home)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Primary.copy(alpha = 0.18f)
                    )
                )
                NavigationBarItem(
                    selected = selectedRoute == "history",
                    onClick = { onNavigate("history") },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_history)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Primary.copy(alpha = 0.18f)
                    )
                )
                NavigationBarItem(
                    selected = selectedRoute == "settings",
                    onClick = { onNavigate("settings") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Primary.copy(alpha = 0.18f)
                    )
                )
            }
        }
    ) { padding ->
        content(padding)
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
