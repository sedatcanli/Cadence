package com.cadence.speech

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cadence.speech.ui.CadenceNavHost
import com.cadence.speech.ui.theme.CadenceTheme

class MainActivity : ComponentActivity() {

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
