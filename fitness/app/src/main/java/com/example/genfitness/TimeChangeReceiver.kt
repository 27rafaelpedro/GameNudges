package com.example.genfitness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("TimeChangeReceiver", "Evento recebido: $action")
        
        val actionsToSync = listOf(
            "com.example.genfitness.ACTION_MIDNIGHT_SYNC",
            Intent.ACTION_TIME_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED
        )

        if (action in actionsToSync) {
            // Dispara uma sincronização imediata
            StepSyncWorker.runOnce(context)
            
            // Reagendar se necessário
            if (action == Intent.ACTION_BOOT_COMPLETED || 
                action == "com.example.genfitness.ACTION_MIDNIGHT_SYNC") {
                StepSyncWorker.scheduleMidnightWakeup(context)
            }
        }
    }
}