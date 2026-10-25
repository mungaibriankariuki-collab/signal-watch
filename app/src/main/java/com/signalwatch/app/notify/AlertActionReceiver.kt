package com.signalwatch.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** "Dismiss" from the notification (or swiping it away). */
class AlertActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotifierEntryPoint {
        fun notifier(): Notifier
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS) return
        EntryPointAccessors.fromApplication(context.applicationContext, NotifierEntryPoint::class.java)
            .notifier()
            .dismissSignal()
    }

    companion object {
        const val ACTION_DISMISS = "com.signalwatch.app.action.DISMISS_SIGNAL"
    }
}
