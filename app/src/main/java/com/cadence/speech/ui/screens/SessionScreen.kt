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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cadence.speech.R
import com.cadence.speech.di.AppContainer
import com.cadence.speech.transcription.LONG_PAUSE_MS
import com.cadence.speech.transcription.TranscriptionStatus
import com.cadence.speech.transcription.fillerRanges
import com.cadence.speech.transcription.formatPause
import com.cadence.speech.ui.components.ChartLegend
import com.cadence.speech.ui.components.LiveScoreRing
import com.cadence.speech.ui.components.ScoreChart
import com.cadence.speech.ui.components.SpeakingBadge
import com.cadence.speech.ui.components.SpeechTimeline
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.session_live).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            if (!metrics.isCalibrating) {
                SpeakingBadge(isSpeaking = metrics.isSpeaking)
            }
        }

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

        Spacer(Modifier.height(24.dp))

        LiveScoreRing(
            live = metrics.liveScore,
            recent = metrics.recentScore,
            overall = metrics.averageScore,
            hasSpeech = metrics.hasAnySpeech,
            size = 156.dp,
            strokeWidth = 12.dp
        )

        if (!metrics.hasAnySpeech) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (metrics.isCalibrating) {
                    stringResource(R.string.session_calibrating, metrics.calibrationProgress)
                } else {
                    stringResource(R.string.session_waiting_speech)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(12.dp))
        SpeechTimeline(history = metrics.speechHistory)
        Spacer(Modifier.height(12.dp))

        if (metrics.hasAnySpeech) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = (stringResource(R.string.session_recent) + " · " +
                    stringResource(R.string.metric_pace)).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            ScoreChart(
                scoreHistory = metrics.scoreHistory,
                paceHistory = metrics.paceHistory,
                fillerMarkers = metrics.fillerMarkers,
                pauseMarkers = metrics.pauseMarkers,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            Spacer(Modifier.height(6.dp))
            ChartLegend(
                score = metrics.recentScore,
                pace = metrics.pacePerMinute,
                fillerCount = metrics.fillerCount,
                pauseCount = metrics.longPauseCount
            )
            Spacer(Modifier.height(8.dp))
        }

        // Transcript display
        val transcription = state.transcription
        val transcriptScroll = rememberScrollState()
        val density = LocalDensity.current
        val transcriptLineHeight = with(density) {
            MaterialTheme.typography.bodyMedium.lineHeight.toDp()
        }
        val transcriptHeight = transcriptLineHeight * 3

        val fillerColor = MaterialTheme.colorScheme.error
        val pauseColor = MaterialTheme.colorScheme.onSurfaceVariant
        val transcriptText = remember(
            transcription.segments,
            transcription.partial,
            transcription.language,
            fillerColor,
            pauseColor
        ) {
            buildAnnotatedString {
                transcription.segments.takeLast(MAX_TRANSCRIPT_SEGMENTS).forEach { segment ->
                    if (segment.pauseBeforeMs >= LONG_PAUSE_MS) {
                        withStyle(SpanStyle(color = pauseColor, fontStyle = FontStyle.Italic)) {
                            append("⏸ ${formatPause(segment.pauseBeforeMs)}  ")
                        }
                    }
                    val offset = length
                    append(segment.text)
                    fillerRanges(segment.text, transcription.language).forEach { range ->
                        addStyle(
                            SpanStyle(color = fillerColor, fontWeight = FontWeight.SemiBold),
                            offset + range.first,
                            offset + range.last + 1
                        )
                    }
                    append(' ')
                }
                if (transcription.partial.isNotBlank()) append(transcription.partial)
            }
        }
        val hasTranscript = transcriptText.isNotBlank()

        LaunchedEffect(transcription.segments.size, transcription.partial) {
            withFrameNanos { }
            transcriptScroll.scrollTo(transcriptScroll.maxValue)
        }
        val statusText = when (transcription.status) {
            TranscriptionStatus.DOWNLOADING ->
                stringResource(R.string.transcript_downloading, transcription.progress)
            TranscriptionStatus.LOADING -> stringResource(R.string.transcript_loading)
            TranscriptionStatus.ERROR ->
                stringResource(R.string.transcript_error, transcription.error ?: "")
            TranscriptionStatus.READY, TranscriptionStatus.IDLE ->
                if (!hasTranscript) stringResource(R.string.transcript_listening) else null
        }
        if (state.isRunning || hasTranscript) {
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
                if (hasTranscript) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(transcriptHeight)
                            .clipToBounds()
                            .verticalScroll(transcriptScroll)
                    ) {
                        Text(
                            text = transcriptText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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

        Spacer(Modifier.height(24.dp))

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

private const val MAX_TRANSCRIPT_SEGMENTS = 40

