package com.example.wificonnectapplication

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WifiAccessibilityService : AccessibilityService() {

    private var retryCount = 0
    private val maxRetry = 5
    private val retryDelayMs = 2000L


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("WifiConnect", "onAccessibilityEvent")

        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        Log.d("WifiConnect", "onAccessibilityEvent")

        val prefs = getSharedPreferences("wifi_prefs", Context.MODE_PRIVATE)
        val shouldRun = prefs.getBoolean("should_run_accessibility", false)
        Log.d("WifiConnect", "onAccessibilityEvent should_run_accessibility: $shouldRun")

        if (!shouldRun) return

        val rootNode = event.source ?: return

        Log.d("WiFiConnect", "AccessibilityEvent triggered. Starting Wi-Fi automation.")

        prefs.edit().putBoolean("should_run_accessibility", false).apply()

        val targetSSID = prefs.getString("ssid", null) ?: return
        val wifiPassword = prefs.getString("password", null) ?: return
        Log.d("WifiConnect", "onAccessibilityEvent ssid: $targetSSID")
        Log.d("WifiConnect", "onAccessibilityEvent password: $wifiPassword")

        attemptClickSSID(rootNode, targetSSID, wifiPassword)
    }

    override fun onServiceConnected() {
        Log.d("WiFiConnect", "✅ WifiAccessibilityService đã được bật")
    }

    override fun onInterrupt() {}

    private fun clickNodeByText(root: AccessibilityNodeInfo, text: String): Boolean {
        Log.d("WiFiConnect", "Attempting to click node with text: $text")
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            val nodeText = node.text?.trim()
            val check = nodeText?.equals(text)
            if (check == true && node.isClickable) {
                Log.d("WiFiConnect", "Clicking node: $nodeText")
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        Log.d("WiFiConnect", "No clickable node found with text: $text")
        return false
    }

    private fun clickParentNodeByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false

        Log.d("WiFiConnect", "Searching for SSID: $text")

        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            val nodeText = node.text?.toString()?.trim()
            if (nodeText != null && nodeText.equals(text, ignoreCase = true)) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        Log.d("WiFiConnect", "Found clickable ancestor for SSID: $text. Clicking...")
                        current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    current = current.parent
                }
            }
        }
        Log.d("WiFiConnect", "SSID not found or not clickable: $text")
        return false
    }

    private fun setTextInViewById(root: AccessibilityNodeInfo, viewId: String, text: String): Boolean {
        Log.d("WiFiConnect", "Attempting to set text in viewId: $viewId")
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            Log.d("WiFiConnect", "Setting text for password field.")
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        Log.e("WiFiConnect", "Failed to find viewId: $viewId to set text.")
        return false
    }

    private fun handlePasswordOrCheckConnection(root: AccessibilityNodeInfo?, targetSSID: String, wifiPassword: String) {
        if (root == null) return
        Log.d("WiFiConnect", "Handling password or checking connection for SSID: $targetSSID")

        val passwordField = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/password")
        if (passwordField != null && passwordField.isNotEmpty()) {
            Log.d("WiFiConnect", "Password field found. Entering password.")
            setTextInViewById(root, "com.android.settings:id/password", wifiPassword)
            clickNodeByText(root, "Kết nối") || clickNodeByText(root, "Connect")
        } else {
            Log.d("WiFiConnect", "No password field found. Checking connection status.")
            Handler(Looper.getMainLooper()).postDelayed({
                checkWifiConnectionStatus(targetSSID)
            }, 4000) // Delay 4 giây để đợi hệ thống kết nối xong
        }

    }

    @SuppressLint("ServiceCast")
    private fun checkWifiConnectionStatus(expectedSSID: String) {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val currentSSID = wifiManager.connectionInfo.ssid?.replace("\"", "")
        if (currentSSID == expectedSSID) {
            Log.i("WiFiConnect", "✅ Đã kết nối thành công đến $expectedSSID")
        } else {
            Log.e("WiFiConnect", "❌ Chưa kết nối đến SSID mong muốn. SSID hiện tại: $currentSSID")
        }
    }

    private fun attemptClickSSID(root: AccessibilityNodeInfo?, ssid: String, password: String) {
        if (root == null) return

        Log.d("WiFiConnect", "Attempting to click SSID: $ssid (retry $retryCount/$maxRetry)")

        if (clickParentNodeByText(root, ssid)) {
            retryCount = 0
            Handler(Looper.getMainLooper()).postDelayed({
                handlePasswordOrCheckConnection(rootInActiveWindow, ssid, password)
                Handler(Looper.getMainLooper()).postDelayed({
                    tryBackToApp(rootInActiveWindow, ssid)
                }, 3000)
            }, 1500)
        } else if (retryCount < maxRetry) {
            retryCount++
            Log.d("WiFiConnect", "SSID not found. Attempting to scroll and retry.")
            scrollWifiList(rootInActiveWindow)
            Handler(Looper.getMainLooper()).postDelayed({
                attemptClickSSID(rootInActiveWindow, ssid, password)
                Handler(Looper.getMainLooper()).postDelayed({
                    handlePasswordOrCheckConnection(rootInActiveWindow, ssid, password)
                    Handler(Looper.getMainLooper()).postDelayed({
                        tryBackToApp(rootInActiveWindow, ssid)
                    }, 3000)
                }, 1500)
            }, retryDelayMs)
        } else {
            Log.w("WiFiConnect", "SSID '$ssid' not found after $maxRetry retries.")
            retryCount = 0
        }
    }

    private fun scrollWifiList(root: AccessibilityNodeInfo): Boolean {
        Log.d("WiFiConnect", "Scrolling Wi-Fi list.")
        val list = findScrollableNode(root)
        return list?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = child?.let { findScrollableNode(it) }
            if (result != null) return result
        }
        return null
    }

    private fun isConnectedToTargetSSID(targetSSID: String): Boolean {

        val wifiManager = applicationContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val info = wifiManager.connectionInfo
        val currentSSID = info.ssid?.removePrefix("\"")?.removeSuffix("\"")

        Log.d("WiFiConnect", "Checking current SSID: $currentSSID")

//        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCorrectSSID = currentSSID == targetSSID

        Log.d("WiFiConnect", "🔍 Đang kiểm tra kết nối Wi-Fi:")
//        Log.d("WiFiConnect", "➡️ Đã kết nối Wi-Fi: $isWifi")
        Log.d("WiFiConnect", "➡️ SSID hiện tại: $currentSSID - So với SSID mong muốn: $targetSSID")

        return isCorrectSSID
    }

    private fun tryBackToApp(root: AccessibilityNodeInfo?, ssid: String) {
        Log.d("WiFiConnect", "Trying to back to app after connection.")
        Handler(Looper.getMainLooper()).postDelayed({
            if (isConnectedToTargetSSID(ssid)) {
                Log.i("WiFiConnect", "✅ Đã kết nối thành công đến $ssid")
                returnToApp()
            } else {
                Log.e("WiFiConnect", "❌ Vẫn chưa kết nối được với $ssid")
            }
        }, 4000) // Chờ 4 giây (hoặc điều chỉnh 3~5 giây tuỳ thiết bị)
    }

    private fun returnToApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.example.wificonnectapplication")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(launchIntent)
            Log.i("WiFiConnect", "🚀 Đã mở lại ứng dụng.")
        } else {
            Log.e("WiFiConnect", "❌ Không tìm thấy intent để mở lại ứng dụng.")
        }
    }


}