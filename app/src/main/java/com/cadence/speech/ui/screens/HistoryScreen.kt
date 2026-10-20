package com.cadence.speech.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cadence.speech.R
import com.cadence.speech.di.AppContainer
import com.cadence.speech.domain.SessionReport
import com.cadence.speech.ui.components.MainScaffold
import com.cadence.speech.ui.components.PaywallDialog
import com.cadence.speech.ui.components.ProgressChart
import com.cadence.speech.ui.components.ScoreChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    MainScaffold(selectedRoute = "history", onNavigate = onNavigate) { padding ->
        val sessions by container.repository.sessions.collectAsState()
        var showPaywall by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(18.dp))

            val trialActive = container.preferences.isTrialActive()

            if (sessions.isNotEmpty()) {
                val ascending = sessions.sortedBy { it.startedAt }
                if (trialActive) {
                    ProgressCard(sessions = ascending)
                } else {
                    LockedProgressCard(onUnlock = { showPaywall = true })
                }
                Spacer(Modifier.height(18.dp))
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sessions, key = { it.id }) { report ->
                        HistoryRow(report = report, onClick = { onOpen(report.id) })
                    }
                }
            }
        }

        if (showPaywall) {
            PaywallDialog(onDismiss = { showPaywall = false })
        }
    }
}

@Composable
private fun ProgressCard(sessions: List<SessionReport>) {
    val scores = sessions.map { it.score }
    val average = scores.average().roundToInt()
    val best = scores.maxOrNull() ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp)
    ) {
        Text(
            text = stringResource(R.string.progress_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.progress_stats, sessions.size, average, best),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        ProgressChart(
            scores = scores,
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatShortDate(sessions.first().startedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatShortDate(sessions.last().startedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LockedProgressCard(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp)
    ) {
        Text(
            text = stringResource(R.string.progress_locked_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.progress_locked_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onUnlock) {
            Text(stringResource(R.string.progress_unlock))
        }
    }
}

private fun formatShortDate(timestamp: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun HistoryRow(report: SessionReport, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(modeLabel(report.mode)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatDate(report.startedAt)} · ${formatDuration(report.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (report.scoreHistory.size >= 2) {
                Spacer(Modifier.height(6.dp))
                ScoreChart(
                    scoreHistory = report.scoreHistory,
                    paceHistory = report.paceHistory,
                    fillerMarkers = report.fillerMarkers,
                    pauseMarkers = report.pauseMarkers,
                    modifier = Modifier
                        .width(180.dp)
                        .height(32.dp)
                )
            }
        }
        Text(
            text = report.score.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (report.score >= 60) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
        )
    }
}
