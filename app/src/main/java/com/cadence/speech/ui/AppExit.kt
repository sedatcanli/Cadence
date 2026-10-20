package com.cadence.speech.ui

import android.app.Activity
import android.content.Context
import android.os.Process
import com.cadence.speech.di.AppContainer

fun exitApp(context: Context, container: AppContainer) {
    container.sessionController.stop()
    container.sessionController.dispose()
    (context as? Activity)?.finishAffinity()
    Process.killProcess(Process.myPid())
}
