package com.cadence.speech.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cadence.speech.R
import com.cadence.speech.di.AppContainer
import com.cadence.speech.transcription.TranscriptionStatus
import com.cadence.speech.ui.components.ScoreRing
import com.cadence.speech.ui.components.StatPill
import com.cadence.speech.ui.theme.Primary

@Composable
fun SessionScreen(
    container: AppContainer,
    onFinished: (String) -> Unit
) {
    val state by container.sessionController.state.collectAsState()

    LaunchedEffect(state.lastReportId) {
        val id = state.lastReportId
        if (id != null) {
            container.sessionController.consumeLastReport()
            onFinished(id)
        }
    }

    BackHandler(enabled = state.isRunning) {
        container.sessionController.stop()
    }

    val metrics = state.metrics
    val level = ((metrics.rmsDb + 60f) / 60f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.session_live).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = formatDuration(metrics.elapsedMs),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.session_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        ScoreRing(
                score = if (metrics.hasAnySpeech) metrics.averageScore else 0,
                size = 156.dp,
                strokeWidth = 12.dp,
                caption = if (metrics.hasAnySpeech) stringResource(R.string.session_avg_score) else ""
            )

        if (metrics.hasAnySpeech) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.session_live_score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = metrics.liveScore.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.session_avg_score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = metrics.averageScore.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Transcript display
        val transcription = state.transcription
        val statusText = when (transcription.status) {
            TranscriptionStatus.DOWNLOADING ->
                stringResource(R.string.transcript_downloading, transcription.progress)
            TranscriptionStatus.LOADING -> stringResource(R.string.transcript_loading)
            TranscriptionStatus.ERROR ->
                stringResource(R.string.transcript_error, transcription.error ?: "")
            TranscriptionStatus.READY, TranscriptionStatus.IDLE ->
                if (transcription.text.isBlank()) stringResource(R.string.transcript_listening) else null
        }
        if (state.isRunning || transcription.text.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.transcript_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                if (transcription.text.isNotBlank()) {
                    Text(
                        text = transcription.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (statusText != null) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatPill(
                label = stringResource(R.string.metric_pace),
                value = "${metrics.pacePerMinute}",
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = stringResource(R.string.metric_fillers),
                value = "${metrics.fillerCount}",
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = stringResource(R.string.metric_pauses),
                value = "${metrics.longPauseCount}",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(28.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.metric_speech),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { level },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Primary,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { container.sessionController.stop() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE5484D),
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(R.string.stop_session),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
