package com.signalwatch.app

import android.app.Application
import com.signalwatch.app.notify.Notifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SignalWatchApp : Application() {

    @Inject lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        notifier.createBaseChannels()
    }
}
