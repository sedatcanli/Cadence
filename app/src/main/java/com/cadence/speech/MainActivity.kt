package com.cadence.speech

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cadence.speech.settings.LocaleHelper
import com.cadence.speech.ui.CadenceNavHost
import com.cadence.speech.ui.theme.CadenceTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CadenceApp).container
        setContent {
            CadenceTheme {
                CadenceNavHost(container = container)
            }
        }
    }
}
