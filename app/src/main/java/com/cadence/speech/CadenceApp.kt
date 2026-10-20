package com.cadence.speech

import android.app.Application
import com.cadence.speech.di.AppContainer

class CadenceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
