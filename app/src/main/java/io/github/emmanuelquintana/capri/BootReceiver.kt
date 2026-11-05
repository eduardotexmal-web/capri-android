package io.github.emmanuelquintana.capri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** "Start with Windows" in her menu, on a phone: she comes back when it starts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UpdateJob.schedule(context) // her look for new versions, every few hours
        val wanted = Store(context).configFlag("startOnBoot", false)
        if (wanted && Settings.canDrawOverlays(context)) CapriService.start(context)
    }
}
