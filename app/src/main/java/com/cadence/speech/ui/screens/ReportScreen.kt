package com.cadence.speech.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cadence.speech.R
import com.cadence.speech.di.AppContainer
import com.cadence.speech.domain.Insight
import com.cadence.speech.domain.ScoreCalculator
import com.cadence.speech.domain.insight
import com.cadence.speech.ui.components.MetricRow
import com.cadence.speech.ui.components.ScoreRing

@Composable
fun ReportScreen(
    container: AppContainer,
    reportId: String,
    onNewSession: () -> Unit,
    onBack: () -> Unit
) {
    val report = remember(reportId) { container.repository.get(reportId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.report_new))
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.report_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (report == null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.report_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ScoreRing(score = report.score, size = 190.dp, strokeWidth = 14.dp)
        }

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricRow(
                label = stringResource(R.string.metric_pace),
                value = "${report.pace} ${stringResource(R.string.unit_per_min)}",
                progress = ScoreCalculator.paceScore(report.pace) / 100f
            )
            MetricRow(
                label = stringResource(R.string.metric_fillers),
                value = "${report.fillerCount} ${stringResource(R.string.unit_count)}",
                progress = 1f - (report.fillerCount / 12f).coerceIn(0f, 1f)
            )
            MetricRow(
                label = stringResource(R.string.metric_pauses),
                value = "${report.longPauseCount} ${stringResource(R.string.unit_count)}",
                progress = 1f - (report.longPauseCount / 5f).coerceIn(0f, 1f)
            )
            MetricRow(
                label = stringResource(R.string.metric_pitch),
                value = "${report.pitchStdHz.toInt()} Hz",
                progress = ((report.pitchStdHz - 8f) / 32f).coerceIn(0f, 1f)
            )
            MetricRow(
                label = stringResource(R.string.metric_speech),
                value = "${(report.speechRatio * 100).toInt()}%",
                progress = report.speechRatio.coerceIn(0f, 1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.report_duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(report.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.report_insight).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(insightText(report.insight())),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onNewSession,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.report_new),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun insightText(insight: Insight): Int = when (insight) {
    Insight.FLUENCY -> R.string.insight_fluency
    Insight.PACE -> R.string.insight_pace
    Insight.TONE -> R.string.insight_tone
    Insight.CONTINUITY -> R.string.insight_continuity
    Insight.GOOD -> R.string.insight_good
}
