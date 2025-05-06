package com.example.wificonnectapplication

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService

object WifiHelper {
    fun connect(context: Context, ssid: String, password: String): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }

        val conf = WifiConfiguration().apply {
            SSID = "\"" + ssid + "\""
            preSharedKey = "\"" + password + "\""
        }

        val netId = wifiManager.addNetwork(conf)
        if (netId == -1) {
            return false
        }

        wifiManager.disconnect()
        wifiManager.enableNetwork(netId, true)
        wifiManager.reconnect()

        return true
    }
}