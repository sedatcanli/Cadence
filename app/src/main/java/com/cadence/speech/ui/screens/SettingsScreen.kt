package com.cadence.speech.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cadence.speech.BuildConfig
import com.cadence.speech.R
import com.cadence.speech.di.AppContainer
import com.cadence.speech.settings.APP_LANGUAGES
import com.cadence.speech.transcription.VOICE_MODELS
import com.cadence.speech.transcription.VoiceModel
import com.cadence.speech.transcription.VoskModels
import com.cadence.speech.ui.components.MainScaffold
import com.cadence.speech.ui.exitApp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit
) {
    MainScaffold(selectedRoute = "settings", onNavigate = onNavigate) { padding ->
        val preferences = container.preferences
        val context = LocalContext.current

        var hapticEnabled by remember { mutableStateOf(preferences.hapticEnabled) }
        var audioEnabled by remember { mutableStateOf(preferences.audioCueEnabled) }
        var sensitivity by remember { mutableFloatStateOf(preferences.sensitivity) }
        var profileName by remember { mutableStateOf(preferences.profileName) }
        var showLanguagePicker by remember { mutableStateOf(false) }
        val currentLanguageLabel = if (preferences.uiLanguage.isBlank()) {
            stringResource(R.string.settings_language_system)
        } else {
            APP_LANGUAGES.firstOrNull { it.code == preferences.uiLanguage }?.label
                ?: preferences.uiLanguage
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.settings_profile).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(stringResource(R.string.settings_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        preferences.profileName = profileName
                        Toast.makeText(
                            context,
                            R.string.settings_profile_saved,
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    enabled = profileName.isNotBlank()
                ) {
                    Text(stringResource(R.string.settings_profile_save))
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.settings_ui_language).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showLanguagePicker = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLanguageLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.settings_language_change),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.settings_feedback).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_haptic),
                    description = stringResource(R.string.settings_haptic_desc),
                    checked = hapticEnabled,
                    onCheckedChange = {
                        hapticEnabled = it
                        preferences.hapticEnabled = it
                    }
                )
                SwitchRow(
                    title = stringResource(R.string.settings_audio),
                    description = stringResource(R.string.settings_audio_desc),
                    checked = audioEnabled,
                    onCheckedChange = {
                        audioEnabled = it
                        preferences.audioCueEnabled = it
                    }
                )
                SwitchRow(
                    title = stringResource(R.string.settings_wear),
                    description = stringResource(R.string.settings_wear_desc),
                    checked = false,
                    enabled = false,
                    onCheckedChange = {}
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.settings_sensitivity),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Slider(
                value = sensitivity,
                onValueChange = {
                    sensitivity = it
                    preferences.sensitivity = it
                },
                valueRange = 0.5f..2f,
                steps = 14
            )
            Text(
                text = if (sensitivity < 0.9f) "Az" else if (sensitivity > 1.3f) "Çok" else "Dengeli",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_transcription_language).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))

            val scope = rememberCoroutineScope()
            var selectedLanguage by remember { mutableStateOf(preferences.transcriptionLanguage) }
            var readyModels by remember {
                mutableStateOf(
                    VOICE_MODELS.filter { VoskModels.isReady(context, it) }
                        .map { it.code }
                        .toSet()
                )
            }
            var downloadingCode by remember { mutableStateOf<String?>(null) }
            var downloadProgress by remember { mutableIntStateOf(0) }
            var downloadError by remember { mutableStateOf<String?>(null) }

            fun download(model: VoiceModel) {
                if (downloadingCode != null) return
                downloadingCode = model.code
                downloadProgress = 0
                downloadError = null
                scope.launch {
                    runCatching {
                        VoskModels.ensure(context, model) { downloadProgress = it }
                    }.onSuccess {
                        readyModels = readyModels + model.code
                    }.onFailure {
                        downloadError = it.message
                    }
                    downloadingCode = null
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                VOICE_MODELS.forEach { model ->
                    val ready = model.code in readyModels
                    val downloading = downloadingCode == model.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLanguage = model.code
                                preferences.transcriptionLanguage = model.code
                                if (!ready) download(model)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLanguage == model.code,
                            onClick = {
                                selectedLanguage = model.code
                                preferences.transcriptionLanguage = model.code
                                if (!ready) download(model)
                            }
                        )
                        Text(
                            text = model.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        when {
                            downloading -> Text(
                                text = "$downloadProgress%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            ready -> Text(
                                text = stringResource(R.string.settings_language_ready),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> TextButton(onClick = { download(model) }) {
                                Text(stringResource(R.string.settings_language_download))
                            }
                        }
                    }
                }
                if (downloadError != null) {
                    Text(
                        text = stringResource(
                            R.string.settings_language_error,
                            downloadError ?: ""
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_privacy).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = { exitApp(context, container) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_exit),
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        if (showLanguagePicker) {
            LanguagePickerDialog(
                selected = preferences.uiLanguage,
                onSelect = { code ->
                    preferences.uiLanguage = code
                    showLanguagePicker = false
                    (context as? Activity)?.recreate()
                },
                onDismiss = { showLanguagePicker = false }
            )
        }
    }
}

@Composable
private fun LanguagePickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ui_language)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(APP_LANGUAGES, key = { it.code }) { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language.code) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language.code == selected,
                            onClick = { onSelect(language.code) }
                        )
                        Text(
                            text = if (language.code.isBlank()) {
                                stringResource(R.string.settings_language_system)
                            } else {
                                language.label
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.transcript_export_cancel))
            }
        }
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
