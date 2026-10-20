package com.cadence.speech.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cadence.speech.audio.CoachingEvent
import com.cadence.speech.settings.UserPreferences

class PhoneHapticChannel(
    context: Context,
    private val preferences: UserPreferences
) : FeedbackChannel {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun play(event: CoachingEvent) {
        if (!preferences.hapticEnabled) return
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        val pattern = when (event) {
            CoachingEvent.FILLER -> longArrayOf(0, 80)
            CoachingEvent.PACE_FAST -> longArrayOf(0, 80, 80, 80)
            CoachingEvent.LONG_PAUSE -> longArrayOf(0, 400)
            CoachingEvent.TOO_QUIET -> longArrayOf(0, 60, 60, 60, 60, 60)
        }

        device.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
