package com.cadence.speech.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cadence.speech.R
import com.cadence.speech.di.AppContainer
import com.cadence.speech.domain.FREE_SESSIONS_PER_DAY
import com.cadence.speech.domain.SessionMode
import com.cadence.speech.domain.isDailyLimitReached
import com.cadence.speech.domain.sessionsToday
import com.cadence.speech.ui.components.MainScaffold
import com.cadence.speech.ui.components.PaywallDialog
import com.cadence.speech.ui.components.ScoreRing
import com.cadence.speech.ui.exitApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    container: AppContainer,
    onStart: (SessionMode) -> Unit,
    onNavigate: (String) -> Unit
) {
    MainScaffold(selectedRoute = "home", onNavigate = onNavigate) { padding ->
        val sessions by container.repository.sessions.collectAsState()
        val last = sessions.firstOrNull()
        val preferences = container.preferences
        val trialActive = preferences.isTrialActive()
        val todayCount = sessionsToday(sessions)
        val limitReached = isDailyLimitReached(sessions, trialActive)
        var showPaywall by remember { mutableStateOf(false) }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (!trialActive && !preferences.paywallAcknowledged) {
                preferences.paywallAcknowledged = true
                showPaywall = true
            }
        }

        var selectedModeName by rememberSaveable { mutableStateOf(SessionMode.INTERVIEW.name) }
        val selectedMode = SessionMode.valueOf(selectedModeName)

        val context = LocalContext.current
        val permissions = remember {
            buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result[Manifest.permission.RECORD_AUDIO] == true) {
                onStart(selectedMode)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.permission_mic),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = if (preferences.profileName.isBlank()) {
                        stringResource(R.string.home_greeting)
                    } else {
                        stringResource(R.string.home_greeting_named, preferences.profileName)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SessionMode.entries.forEach { mode ->
                        ModeCard(
                            title = stringResource(modeLabel(mode)),
                            selected = selectedMode == mode,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedModeName = mode.name }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(modeHint(selectedMode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (trialActive) {
                        stringResource(R.string.trial_days_left, preferences.trialDaysLeft())
                    } else {
                        stringResource(
                            R.string.free_plan_limit,
                            todayCount,
                            FREE_SESSIONS_PER_DAY
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (limitReached) {
                            showPaywall = true
                            return@Button
                        }
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) onStart(selectedMode) else launcher.launch(permissions)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.start_session),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.home_last_score).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                if (last == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_sessions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(modeLabel(last.mode)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = formatDate(last.startedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ScoreRing(score = last.score, size = 78.dp, strokeWidth = 8.dp, caption = "")
                        }
                    }
                }

                Spacer(Modifier.height(80.dp))
            }

            OutlinedButton(
                onClick = { exitApp(context, container) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Text(stringResource(R.string.home_exit))
            }
        }

        if (showPaywall) {
            PaywallDialog(onDismiss = { showPaywall = false })
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun modeLabel(mode: SessionMode): Int = when (mode) {
    SessionMode.INTERVIEW -> R.string.mode_interview
    SessionMode.PRESENTATION -> R.string.mode_presentation
    SessionMode.FREE -> R.string.mode_free
}

internal fun modeHint(mode: SessionMode): Int = when (mode) {
    SessionMode.INTERVIEW -> R.string.mode_hint_interview
    SessionMode.PRESENTATION -> R.string.mode_hint_presentation
    SessionMode.FREE -> R.string.mode_hint_free
}

internal fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
