package com.example.wificonnectapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class AlarmReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val db = WifiDatabase.getInstance(context)
        GlobalScope.launch {
            db.wifiDao().resetUsed()
        }
    }
}