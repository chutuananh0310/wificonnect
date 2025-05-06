package com.example.wificonnectapplication

import ShellCommand
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

object WifiShellHelper {
    @RequiresApi(Build.VERSION_CODES.O)
    fun connectWifiWithShell(ssid: String, password: String?): String {
         // /vendor/bin/wpa_cli
        // val wpaCliPath = "/vendor/bin/wpa_cli" // Thay thế bằng đường dẫn thực tế
//         val connectCommand =
//            "su -c \"" + wpaCliPath + " -i wlan0 add_network && " + wpaCliPath + " -i wlan0 set_network 0 ssid \\\"" + ssid + "\\\" && " + wpaCliPath + " -i wlan0 set_network 0 psk \\\"" + password + "\\\" && " + wpaCliPath + " -i wlan0 enable_network 0 && " + wpaCliPath + " -i wlan0 select_network 0\""

        val connectCommand =
            "su -c \"wpa_cli -i wlan0 add_network\" -c \"wpa_cli -i wlan0 set_network 0 ssid \\\"" + ssid + "\\\"\" -c \"wpa_cli -i wlan0 set_network 0 psk \\\"" + password + "\\\"\" -c \"wpa_cli -i wlan0 enable_network 0 \" -c \"wpa_cli -i wlan0 select_network 0\""
//            "su -c \"wpa_cli -i wlan0 add_network && wpa_cli -i wlan0 set_network 0 ssid \\\"" + ssid + "\\\" && wpa_cli -i wlan0 set_network 0 psk \\\"" + password + "\\\" && wpa_cli -i wlan0 enable_network 0 && wpa_cli -i wlan0 select_network 0\""


            val result: String? = ShellCommand.execute(connectCommand).toString()
        Log.d("Shell Command Result", result!!)
        return result
    }
}