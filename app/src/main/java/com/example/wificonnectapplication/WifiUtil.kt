@file:Suppress("DEPRECATION")

package com.example.wificonnectapplication

// WifiUtil.kt
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.runBlocking // Chỉ dùng cho ví dụ, nên dùng lifecycleScope.launch trong Activity/Fragment
import kotlinx.coroutines.delay

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.appcompat.app.AlertDialog
import android.provider.Settings
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.startActivity
import androidx.fragment.app.FragmentActivity
import com.example.wificonnectapplication.PermissionChecker.checkChangeWifiStatePermission
import com.example.wificonnectapplication.PermissionChecker.checkWriteSettingsPermission
import java.io.File
import android.content.SharedPreferences
import android.text.TextUtils
import java.io.FileOutputStream


object WifiUtil {

    private const val TAG = "WifiUtil"
    private const val REQUEST_LOCATION_SETTINGS = 123 // Mã request duy nhất
    private var currentSuggestions: List<WifiNetworkSuggestion> = emptyList()


    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    fun setWifiEnabled(context: Context, enabled: Boolean): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.setWifiEnabled(enabled)
    }

    fun scanWifiNetworks(context: Context, onScanResults: (List<ScanResult>) -> Unit) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiScanReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION, false)
                if (success) {
                    val scanResults = wifiManager.scanResults
                    onScanResults(scanResults)
                } else {
//                    Toast.makeText(context, "Lỗi quét Wi-Fi", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Lỗi quét Wi-Fi")
                    onScanResults(emptyList()) // Báo lỗi bằng danh sách rỗng
                }
                context.unregisterReceiver(this) // Hủy đăng ký receiver
            }
        }

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Cần quyền vị trí để quét Wi-Fi", Toast.LENGTH_SHORT).show()
            onScanResults(emptyList())
            context.unregisterReceiver(wifiScanReceiver)
            return
        }

        val success = wifiManager.startScan()
        if (!success) {
            Toast.makeText(context, "Không thể bắt đầu quét Wi-Fi", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Không thể bắt đầu quét Wi-Fi")
            onScanResults(emptyList())
            context.unregisterReceiver(wifiScanReceiver)
        }
    }

    fun getConnectedWifiInfo(context: Context): WifiInfo? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.connectionInfo
    }

    fun connectToWifi(context: Context, ssid: String, password: String? = null) : Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CHANGE_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Ứng dụng cần quyền thay đổi trạng thái Wi-Fi", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Ứng dụng thiếu quyền CHANGE_WIFI_STATE")
            return false
        }

        val wifiConfig = WifiConfiguration().apply {
            this.SSID = "\"${ssid}\""
            when {
                password.isNullOrEmpty() -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
                else -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    preSharedKey = "\"${password}\""
                }
                // Thêm các trường hợp cho WEP nếu cần
            }
        }

        val configuredNetworks = wifiManager.configuredNetworks
        val netId = configuredNetworks?.firstOrNull { it.SSID == wifiConfig.SSID }?.networkId ?: -1
        val addNetworkId = if (netId == -1) {
            wifiManager.addNetwork(wifiConfig)
        } else {
            wifiConfig.networkId = netId
            wifiManager.updateNetwork(wifiConfig)
        }

        if (addNetworkId != -1 || netId != -1) {
            wifiManager.disconnect()
            runBlocking { delay(1000) } // Chờ 1 giây

            val networkIdToEnable = if (addNetworkId != -1) addNetworkId else netId
            wifiManager.enableNetwork(networkIdToEnable, true)
            runBlocking { delay(1000) } // Chờ 1 giây

            val isReconnectSuccessful = wifiManager.reconnect()
            Log.d(TAG, "Reconnect successful: $isReconnectSuccessful")
            return isReconnectSuccessful

//            if(addNetworkId != -1)
//            {
//                wifiManager.enableNetwork(addNetworkId, true)
//            }
//            else if(netId != -1)
//            {
//                wifiManager.enableNetwork(netId, true)
//            }
//
////            wifiManager.enableNetwork(addNetworkId, true)
//            wifiManager.reconnect()
//            return true
        } else {
            Toast.makeText(context, "Không thể cấu hình mạng", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Không thể cấu hình mạng cho SSID: $ssid")
            return false
        }

        return true
    }

    fun connectToWifi2(context: Context, ssid: String, password: String? = null) : Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CHANGE_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Ứng dụng cần quyền thay đổi trạng thái Wi-Fi", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Ứng dụng thiếu quyền CHANGE_WIFI_STATE")
            return false
        }

        val wifiConfig = WifiConfiguration().apply {
            this.SSID = "\"${ssid}\""
            when {
                password.isNullOrEmpty() -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
                else -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    preSharedKey = "\"${password}\""
                }
                // Thêm các trường hợp cho WEP nếu cần
            }
        }

        val configuredNetworks = wifiManager.configuredNetworks
        val netId = configuredNetworks?.firstOrNull { it.SSID == wifiConfig.SSID }?.networkId ?: -1
        val addNetworkId = if (netId == -1) {
            wifiManager.addNetwork(wifiConfig)
        } else {
            wifiConfig.networkId = netId
            wifiManager.updateNetwork(wifiConfig)
        }


        if (addNetworkId != -1 || netId != -1) {
            wifiManager.disconnect()
            runBlocking { delay(1000) }

            val networkIdToEnable = if (addNetworkId != -1) addNetworkId else netId
            wifiManager.enableNetwork(networkIdToEnable, true)
            runBlocking { delay(2000) } // Chờ lâu hơn một chút trước khi reconnect

            val isReconnectInitiated = wifiManager.reconnect()
            Log.d(TAG, "Reconnect initiated: $isReconnectInitiated")

            if (isReconnectInitiated) {
                // Chờ một khoảng thời gian để kết nối và kiểm tra trạng thái
                runBlocking { delay(5000) } // Chờ 5 giây để kết nối

                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val wifiInfo = wifiManager.connectionInfo
                        if (wifiInfo != null && wifiInfo.ssid.equals("\"${ssid}\"", ignoreCase = true)) {
                            Log.d(TAG, "Kết nối thành công với SSID: $ssid")
                            return true
                        } else {
                            Log.w(TAG, "Đã kết nối Wi-Fi nhưng không phải SSID mong muốn hoặc không có thông tin SSID.")
                            return false
                        }
                    } else {
                        Log.w(TAG, "Đã kết nối mạng nhưng không phải Wi-Fi.")
                        return false
                    }
                } else {
                    Log.w(TAG, "Không có mạng hoạt động sau khi reconnect.")
                    return false
                }
            } else {
                Log.e(TAG, "Không thể khởi tạo reconnect.")
                return false
            }
        } else {
            // ... (phần xử lý lỗi) ...
            return false
        }
    }


    suspend fun connectToWifiAndWaitForConnection(
        context: Context,
        ssid: String,
        password: String? = null
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            continuation.resume(false) // Không có quyền
            // Hiển thị AlertDialog trên UI thread
            (context as? androidx.fragment.app.FragmentActivity)?.runOnUiThread {
                AlertDialog.Builder(context)
                    .setTitle("Yêu cầu quyền vị trí")
                    .setMessage("Ứng dụng cần quyền vị trí để quét và kết nối Wi-Fi. Vui lòng cấp quyền trong cài đặt.")
                    .setPositiveButton("Đi tới cài đặt") { _, _ ->
                        // Mở cài đặt ứng dụng để người dùng cấp quyền
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            val uri = android.net.Uri.fromParts("package", context.packageName, null)
                            data = uri
                        }
                        context.startActivity(intent)
                        continuation.resume(false) // Tiếp tục coroutine với kết quả false (chưa có quyền)
                    }
                    .setNegativeButton("Hủy") { _, _ ->
                        continuation.resume(false) // Người dùng hủy
                    }
                    .setCancelable(false) // Không cho phép đóng bằng cách chạm ra ngoài
                    .show()
            }
            return@suspendCancellableCoroutine
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            continuation.resume(false) // Không có quyền
            return@suspendCancellableCoroutine
        }

        val wifiConfig = WifiConfiguration().apply {
            this.SSID = "\"${ssid}\""
            when {
                password.isNullOrEmpty() -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                else -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    preSharedKey = "\"${password}\""
                }
            }
        }

        val configuredNetworks = wifiManager.configuredNetworks
        val netId = configuredNetworks?.firstOrNull { it.SSID == wifiConfig.SSID }?.networkId ?: -1
        val addNetworkId = if (netId == -1) wifiManager.addNetwork(wifiConfig) else {
            wifiConfig.networkId = netId
            wifiManager.updateNetwork(wifiConfig)
        }

        if (addNetworkId == -1 && netId == -1) {
            continuation.resume(false) // Không thể cấu hình mạng
            return@suspendCancellableCoroutine
        }

        wifiManager.disconnect()
        val networkIdToEnable = if (addNetworkId != -1) addNetworkId else netId
        wifiManager.enableNetwork(networkIdToEnable, true)
        wifiManager.reconnect()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val activeNetwork = connectivityManager.getNetworkCapabilities(network)
                if (activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    val currentWifiInfo = wifiManager.connectionInfo
                    if (currentWifiInfo != null && currentWifiInfo.ssid.equals("\"${ssid}\"", ignoreCase = true)) {
                        Log.d(TAG, "Đã kết nối thành công và đúng SSID: ${currentWifiInfo.ssid}")
                        connectivityManager.unregisterNetworkCallback(this)
                        continuation.resume(true)
                    }
                    else
                    {
                        Log.d(TAG, "không thể kết nối đến SSID: ${currentWifiInfo.ssid}")
                        connectivityManager.unregisterNetworkCallback(this)
                        continuation.resume(false)
                    }
                }
            }

            override fun onLost(network: Network) {
                val activeNetwork = connectivityManager.getNetworkCapabilities(network)
                if (activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    Log.w(TAG, "Kết nối Wi-Fi bị mất.")
                    connectivityManager.unregisterNetworkCallback(this)
                    continuation.resume(false)
                }
            }

            override fun onUnavailable() {
                Log.w(TAG, "Không thể kết nối Wi-Fi.")
                connectivityManager.unregisterNetworkCallback(this)
                continuation.resume(false)
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        continuation.invokeOnCancellation {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    suspend fun connectWifi(
        context: Context,
        ssid: String,
        password: String? = null
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Kiểm tra và yêu cầu quyền vị trí (cho Android M trở lên)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            (context as? FragmentActivity)?.runOnUiThread {
                AlertDialog.Builder(context)
                    .setTitle("Yêu cầu quyền vị trí")
                    .setMessage("Ứng dụng cần quyền vị trí để quét và kết nối Wi-Fi. Vui lòng cấp quyền trong cài đặt.")
                    .setPositiveButton("Đi tới cài đặt") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            val uri = android.net.Uri.fromParts("package", context.packageName, null)
                            data = uri
                        }
                        context.startActivity(intent)
                        continuation.resume(false)
                    }
                    .setNegativeButton("Hủy") { _, _ ->
                        continuation.resume(false)
                    }
                    .setCancelable(false)
                    .show()
            }
            return@suspendCancellableCoroutine
        }

        // Kiểm tra quyền thay đổi trạng thái Wi-Fi
        Log.d(TAG, "Kiểm tra quyền thay đổi trạng thái Wi-Fi")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Ứng dụng thiếu quyền CHANGE_WIFI_STATE")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "1 Build.VERSION" + Build.VERSION.SDK_INT)
//            openWifiSettingsWithSuggestion(context, ssid, password.toString())
            connectToWifiRoot(context, ssid, password.toString())
        }
        else {
            Log.d(TAG, "2 Build.VERSION" + Build.VERSION.SDK_INT)
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val activeNetwork = connectivityManager.getNetworkCapabilities(network)
                    if (activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                        val currentWifiInfo = wifiManager.connectionInfo
                        if (currentWifiInfo != null && currentWifiInfo.ssid.equals(
                                "\"${ssid}\"",
                                ignoreCase = true
                            )
                        ) {
                            Log.d(TAG, "Đã kết nối thành công với SSID: ${currentWifiInfo.ssid}")
                            connectivityManager.unregisterNetworkCallback(this)
                            continuation.resume(true)
                        } else {
                            Log.w(TAG, "Đã kết nối Wi-Fi nhưng không phải SSID mong muốn")
                            connectivityManager.unregisterNetworkCallback(this)
                            continuation.resume(false)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    val activeNetwork = connectivityManager.getNetworkCapabilities(network)
                    if (activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                        Log.w(TAG, "Kết nối Wi-Fi bị mất (Android 9-)")
                        connectivityManager.unregisterNetworkCallback(this)
                        continuation.resume(false)
                    }
                }

                override fun onUnavailable() {
                    Log.w(TAG, "Không thể kết nối Wi-Fi (Android 9-)")
                    connectivityManager.unregisterNetworkCallback(this)
                    continuation.resume(false)
                }
            }

            val wifiConfig = WifiConfiguration().apply {
                this.SSID = "\"${ssid}\""
                when {
                    password.isNullOrEmpty() -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    else -> {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK)
                        preSharedKey = "\"${password}\""
                    }
                }
            }

            val netId =
                wifiManager.configuredNetworks?.firstOrNull { it.SSID == wifiConfig.SSID }?.networkId
                    ?: -1
            val addNetworkId = if (netId == -1) wifiManager.addNetwork(wifiConfig) else {
                wifiConfig.networkId = netId
                wifiManager.updateNetwork(wifiConfig)
            }

            if (addNetworkId != -1 || netId != -1) {
                wifiManager.disconnect()
                val networkIdToEnable = if (addNetworkId != -1) addNetworkId else netId
                wifiManager.enableNetwork(networkIdToEnable, true)
                wifiManager.reconnect()

                val networkRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()

                connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
                continuation.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                }

            } else {
                Log.e(TAG, "Không thể thêm hoặc cập nhật mạng (Android 9-)")
                continuation.resume(false)
            }

        }

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            // Gọi hàm này ở nơi bạn muốn kiểm tra quyền
//            val hasChangeWifiStatePermission = checkChangeWifiStatePermission(context)
//            Log.d(TAG, "Quyền CHANGE_WIFI_STATE được cấp: $hasChangeWifiStatePermission")
//
//                val hasWriteSettingsPermission = checkWriteSettingsPermission(context)
//            Log.d(TAG, "Quyền WRITE_SETTINGS được cấp: $hasWriteSettingsPermission")
//
//            // Sử dụng WifiNetworkSpecifier cho Android 10 trở lên
////            val specifier = WifiNetworkSpecifier.Builder()
////                .setSsid(ssid)
////                .setWpa2Passphrase(password!!) // hoặc setOpenNetwork(true), setWpa3Passphrase(), etc.
////                .build()
////
////            val networkRequest = NetworkRequest.Builder()
////                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
////                .setNetworkSpecifier(specifier)
////                .build()
//
//            try {
////                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
////
////                val suggestion = WifiNetworkSuggestion.Builder()
////                    .setSsid(ssid)
////                    .setWpa2Passphrase(password)
////                    .build()
////
////                val suggestionsList = listOf(suggestion)
////
////                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
////                val status = wifiManager.addNetworkSuggestions(suggestionsList)
////
////                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
////                    // Đề xuất thành công — Hệ thống sẽ tự động kết nối khi có thể
////                    Log.d(TAG, "Đề xuất thành công — Hệ thống sẽ tự động kết nối khi có thể")
////                } else {
////                    // Gợi ý thất bại — bạn có thể xử lý lỗi tại đây
////                    Log.d(TAG, "Gợi ý thất bại — bạn có thể xử lý lỗi tại đây")
////                }
//
////                connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
////                    override fun onAvailable(network: Network) {
////                        super.onAvailable(network)
////                        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
////
////                        connectivityManager.bindProcessToNetwork(network)
////
////                        connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
////                            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
////                                // Có internet
////                                Log.d(TAG, "Đã kết nối thành công với SSID Có internet")
////                            } else {
////                                // Không có internet
////                                Log.d(TAG, "Đã kết nối thành công với SSID Không có internet")
////                            }
////                        }
////
////                        val activeNetwork = connectivityManager.getNetworkCapabilities(network)
////                        if (activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
////                            val currentWifiInfo = wifiManager.connectionInfo
////                            if (currentWifiInfo != null && currentWifiInfo.ssid.equals("\"${ssid}\"", ignoreCase = true)) {
////                                Log.d(TAG, "Đã kết nối thành công với SSID: ${currentWifiInfo.ssid}")
//////                                connectivityManager.unregisterNetworkCallback(this)
////                                continuation.resume(true)
////                            } else {
////                                Log.w(TAG, "Đã kết nối Wi-Fi nhưng không phải SSID mong muốn")
////                                connectivityManager.unregisterNetworkCallback(this)
////                                continuation.resume(false)
////                            }
////                        }
////                    }
////
////                    override fun onUnavailable() {
////                        super.onUnavailable()
////                        // Xử lý nếu không kết nối được
////                    }
////                })
//
////                connectivityManager.requestNetwork(networkRequest, networkCallback)
////                continuation.invokeOnCancellation {
////                    connectivityManager.unregisterNetworkCallback(networkCallback)
////                }
//
////                addWifiToSupplicant(ssid, password!!)
////                connectToWifiRoot(ssid, password!!)
//
//                val currentWifiInfo = checkSupplicantPath()
//                Log.d("WifiUtil", "currentWifiInfo: ${currentWifiInfo}") // In cả stack trace
//
//
////                val log = connectToWifiAsRoot(ssid, password!!)
////                Log.d("WifiConnect", log)
//
////                val log = connectToWifiRoot(ssid, password!!)
////                Log.d("WifiConnect", log)
//
////                if (ensureWpaCli(context)) {
////                    // bây giờ bạn có thể gọi "/data/local/tmp/wpa_cli" thay vì "wpa_cli"
////
////
////                } else {
////                    Log.e("WifiConnect", "Không thể cài wpa_cli, fallback sang method khác")
////                }
//
//
////                connectToWifiRooted(ssid, password!!)
//
////                connectToWifiAsRoot(ssid, password!!)
//                connectToWifiRoot(context, ssid, password!!)
////                connectToWifiRoot2(context, ssid, password!!)
//
//                val result = runShellAsRoot("cat /data/misc/wifi/wpa_supplicant.conf")
//                Log.d("WifiCheck", result)
//
//                val wifiConfigStore = runShellAsRoot("cat /data/misc/wifi/WifiConfigStore.xml")
//                Log.d("WifiConfigStore", wifiConfigStore)
//
//                val ssida = runShellAsRoot("dumpsys wifi | grep 'SSID'")
//                Log.d("SSID_CHECK", ssida)
//
//                logWifiDiagnostics()
//
//
//
////                val result2 = runShellAsRoot("dumpsys wifi | grep SSID")
////                Log.d("WifiStatus", result2)
//
////                if (success) {
////                    Toast.makeText(context, "✅ Kết nối Wi-Fi đã được thiết lập!", Toast.LENGTH_SHORT).show()
////                } else {
////                    Toast.makeText(context, "❌ Thất bại khi kết nối Wi-Fi!", Toast.LENGTH_LONG).show()
////                }
//
//            }
//            catch (e: Exception)
//            {
//                Log.e("WifiUtil", "Lỗi khi requestNetwork: ${e.message}", e) // In cả stack trace
//            }
//
//
//
//        } else {
//            // Sử dụng phương pháp addNetwork cho Android 9 trở xuống
//
//        }
    }

    fun disconnectWifi(context: Context) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.disconnect()
    }

    fun forgetNetwork(context: Context, networkId: Int): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val disabled = wifiManager.disableNetwork(networkId)
//        val forgotten = wifiManager.f .forget(networkId)
        val saved = wifiManager.saveConfiguration()
//        return disabled && forgotten && saved
        return disabled && saved
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getWifiConfigurations(context: Context) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        } else {

        }
        wifiManager.configuredNetworks
    }


//    fun connectToWifiRoot(ssid: String, password: String) {
//        try {
//            val commands = listOf(
//                "svc wifi enable",
//                "wpa_cli -i wlan0 add_network",
//                "wpa_cli -i wlan0 set_network 0 ssid '\"$ssid\"'",
//                "wpa_cli -i wlan0 set_network 0 psk '\"$password\"'",
//                "wpa_cli -i wlan0 enable_network 0",
//                "wpa_cli -i wlan0 save_config",
//                "wpa_cli -i wlan0 select_network 0"
//            )
//
//            for (cmd in commands) {
//                runShellAsRoot(cmd)
//            }
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }

//    fun runShellAsRoot(command: String) {
//        try {
//            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
//            process.waitFor()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }

//    fun addWifiToSupplicant(ssid: String, password: String) {
//        val configEntry = """
//        network={
//            ssid="$ssid"
//            psk="$password"
//            priority=1
//        }
//    """.trimIndent()
//
//        val path = "/data/misc/wifi/wpa_supplicant.conf"
//
//        // Dán cấu hình vào cuối file
//        val command = "echo '$configEntry' >> $path"
//
//        // Thêm quyền ghi + khởi động lại Wi-Fi
//        val cmds = listOf(
//            "mount -o remount,rw /data",
//            command,
//            "chmod 660 $path",
//            "chown system:wifi $path",
//            "svc wifi disable",
//            "sleep 1",
//            "svc wifi enable"
//        )
//
//        cmds.forEach {
//            runShellAsRoot(it)
//        }
//    }



    fun runShellAsRoot2(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun checkSupplicantPath(): String? {
        val possiblePaths = listOf(
            "/data/misc/wifi/wpa_supplicant.conf",
            "/data/misc/wifi/WifiConfigStore.xml",
            "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
            "/data/vendor/wifi/wpa/supplicant.conf"
        )

        for (path in possiblePaths) {
            val result = runShellAsRoot2("[ -f $path ] && echo exists")
            if (result.trim() == "exists") {
                return path
            }
        }
        return null
    }

//    fun connectWifiByEditingSupplicant(ssid: String, password: String) {
//        val confPath = "/data/misc/wifi/wpa_supplicant.conf"
//
//        val header = """
//        ctrl_interface=DIR=/data/misc/wifi/sockets
//        update_config=1
//        country=US
//    """.trimIndent()
//
//        val networkBlock = """
//        network={
//            ssid="$ssid"
//            psk="$password"
//            key_mgmt=WPA2_PSK
//            priority=1
//        }
//    """.trimIndent()
//
//        val fullConfig = "$header\n\n$networkBlock"
//
//        val commands = listOf(
//            "mount -o remount,rw /data",
//            "chmod 777 $confPath",
//            "echo '$fullConfig' > $confPath", // ⚠️ ghi đè hoàn toàn
//            "chmod 660 $confPath",
//            "chown system:wifi $confPath",
//            "svc wifi disable",
//            "sleep 2",
//            "svc wifi enable"
//        )
//
//        for (cmd in commands) {
//            runShellAsRoot(cmd)
//        }
//    }
//
    fun runShellAsRoot(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun connectToWifiRooted(ssid: String, password: String): Pair<Boolean, String> {
        val shell = Runtime.getRuntime()

        fun runCommand(cmd: String): Pair<Boolean, String> {
            return try {
                val process = shell.exec(arrayOf("su", "-c", cmd))
                val exitCode = process.waitFor()
                val output = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText().trim()
                Pair(exitCode == 0, if (output.isNotEmpty()) output else error)
            } catch (e: Exception) {
                Pair(false, e.message ?: "Unknown error")
            }
        }

        val logs = StringBuilder()

        // 1. Xoá các file cấu hình Wi-Fi cũ và lịch sử SSID đã bị block
        logs.appendLine("🧹 Bước 1: Xoá cấu hình Wi-Fi cũ...")
        val cleanupFiles = listOf(
            "/data/misc/wifi/WifiConfigStore.xml",
            "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
            "/data/misc/wifi/networkHistory.txt",
            "/data/misc/wifi/WifiConfigStore.db"
        )
        for (file in cleanupFiles) {
            val (success, msg) = runCommand("rm -f $file")
            logs.appendLine(" - Xoá $file: ${if (success) "✅" else "❌ $msg"}")
        }

        // 2. Ghi đè wpa_supplicant.conf với cấu hình mới
        logs.appendLine("\n📝 Bước 2: Ghi cấu hình SSID mới...")
        val supplicantConfig = """
        ctrl_interface=DIR=/data/misc/wifi/sockets
        update_config=1
        country=US

        network={
            ssid="$ssid"
            psk="$password"
            key_mgmt=WPA2_PSK
            priority=1
        }
    """.trimIndent()

        // Ghi file tạm
        val tempPath = "/sdcard/new_supplicant.conf"
        val writeFile = File(tempPath)
        return try {
            writeFile.writeText(supplicantConfig)

            val (copySuccess, copyMsg) = runCommand("cp $tempPath /data/misc/wifi/wpa_supplicant.conf && chmod 660 /data/misc/wifi/wpa_supplicant.conf && chown system:wifi /data/misc/wifi/wpa_supplicant.conf")
            logs.appendLine(" - Ghi cấu hình: ${if (copySuccess) "✅" else "❌ $copyMsg"}")

            // 3. Restart Wi-Fi bằng svc (nhanh hơn reboot)
            logs.appendLine("\n🔄 Bước 3: Restart Wi-Fi...")
            val (disableOk, disableLog) = runCommand("svc wifi disable")
            logs.appendLine(" - Tắt Wi-Fi: ${if (disableOk) "✅" else "❌ $disableLog"}")
            Thread.sleep(3000)

            val (enableOk, enableLog) = runCommand("svc wifi enable")
            logs.appendLine(" - Bật Wi-Fi: ${if (enableOk) "✅" else "❌ $enableLog"}")

            Pair(enableOk, logs.toString())
        } catch (e: Exception) {
            logs.appendLine("❌ Lỗi ghi file: ${e.message}")
            Pair(false, logs.toString())
        }
    }

    fun connectToWifiAsRoot(ssid: String, password: String): String {
        val logs = StringBuilder()

        fun runShell(cmd: String): Pair<Boolean, String> {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val result = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText().trim()
                process.waitFor()
                Pair(process.exitValue() == 0, if (result.isNotEmpty()) result else error)
            } catch (e: Exception) {
                Pair(false, e.message ?: "Unknown error")
            }
        }

        logs.appendLine("📶 Bắt đầu kết nối tới SSID: $ssid")

        // 1. Xoá các file cấu hình Wi-Fi cũ và lịch sử SSID đã bị block
        logs.appendLine("🧹 Xoá cấu hình Wi-Fi cũ...")
        val cleanupFiles = listOf(
            "/data/misc/wifi/WifiConfigStore.xml",
            "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
            "/data/misc/wifi/networkHistory.txt",
            "/data/misc/wifi/WifiConfigStore.db"
        )
        for (file in cleanupFiles) {
            val (success, msg) = runShell("rm -f $file")
            logs.appendLine(" - Xoá $file: ${if (success) "✅" else "❌ $msg"}")
        }

        // 0. Clear ephemeral block list
        val (ephemeralOk, ephemeralLog) = runShell("cmd wifi clear-deleted-ephemeral-networks")
        logs.appendLine("🗑️ Xóa danh sách SSID ephemeral đã block…: ${if (ephemeralOk) "✅" else "❌ $ephemeralLog"}")
        Thread.sleep(500)

        // 1. Ghi file cấu hình tạm
        val tempPath = "/data/local/tmp/new_supplicant.conf"
        val config = """
        ctrl_interface=DIR=/data/misc/wifi/sockets
        update_config=1
        country=US

        network={
            ssid=$ssid
            psk=$password
            key_mgmt=WPA-PSK
            priority=1
        }
    """.trimIndent()

        val writeCmd = "echo '${config.replace("'", "'\\''")}' > $tempPath"
        val (writeOk, writeLog) = runShell(writeCmd)
        logs.appendLine("📝 Ghi file cấu hình tạm: ${if (writeOk) "✅" else "❌ $writeLog"}")

        // 2. Ghi đè vào wpa_supplicant.conf
        val copyCmd = """
        cp $tempPath /data/misc/wifi/wpa_supplicant.conf && \
        chmod 660 /data/misc/wifi/wpa_supplicant.conf && \
        chown system:wifi /data/misc/wifi/wpa_supplicant.conf
    """.trimIndent()

        val (copyOk, copyLog) = runShell(copyCmd)
        logs.appendLine("📂 Ghi đè file cấu hình chính: ${if (copyOk) "✅" else "❌ $copyLog"}")

        val (killallOk, killallLog) = runShell("killall wpa_supplicant")
        logs.appendLine("📴 kill all: ${if (killallOk) "✅" else "❌ $killallLog"}")

//        val (setpropOk, setpropLog) = runShell("setprop ctl.restart wpa_supplicant")
//        logs.appendLine("📴 restart wpa_supplicant: ${if (setpropOk) "✅" else "❌ $setpropLog"}")

        // 3. Restart Wi-Fi
        val (disableOk, disableLog) = runShell("svc wifi disable")
        logs.appendLine("📴 Tắt Wi-Fi: ${if (disableOk) "✅" else "❌ $disableLog"}")
        Thread.sleep(2000)

        val (enableOk, enableLog) = runShell("svc wifi enable")
        logs.appendLine("📳 Bật lại Wi-Fi: ${if (enableOk) "✅" else "❌ $enableLog"}")

        // Bước 4: Thêm mạng mới bằng ADB shell (Android 9+)
        val cmds = listOf(
            "cmd wifi add-network", // Gán NetId mới
            "cmd wifi set-ssid 0 \"$ssid\"",
            "cmd wifi set-psk 0 \"$password\"",
            "cmd wifi enable-network 0",
            "cmd wifi select-network 0"
        )

        for (cmd in cmds) {
            val (ok, log) = runShell(cmd)
            logs.appendLine("🔧 $cmd: ${if (ok) "✅" else "❌ $log"}")
        }

        logs.appendLine("✅ Hoàn tất xử lý. Hệ thống sẽ tự kết nối nếu cấu hình hợp lệ.")

        // 3. Restart Wi-Fi




        return logs.toString()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToWifiRoot(context: Context, ssid: String, password: String) {
        fun log(message: String) = Log.d("WifiConnect", message)

        fun runRootCommand(cmd: String): String {
            return try {
//                val fullCmd = arrayOf("su", "-c", "sh -c '${cmd}'")
//                val process = Runtime.getRuntime().exec(fullCmd)
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                process.waitFor()
                if (error.isNotBlank()) Log.e("WiFiConnect", "⚠️ stderr: $error")
                if (output.isNotBlank()) Log.d("WiFiConnect", "ℹ️ stdout: $output")
                output.trim()
            } catch (e: Exception) {
                Log.e("WiFiConnect", "❌ Lỗi khi chạy lệnh: $cmd\n${e.message}")
                ""
            }
        }

        log("📶 Bắt đầu kết nối tới SSID: $ssid")

        val tmpPath = "/data/local/tmp/wpa_supplicant.conf"
        val finalPath = "/data/misc/wifi/wpa_supplicant.conf"

        // 0. Xóa danh sách SSID ephemeral đã block
        log("🗑️ Clear ephemeral SSID list…")
        runRootCommand("cmd wifi clear-deleted-ephemeral-networks")
        Thread.sleep(500)

        // 1. Tắt Wi-Fi
        log("📴 Tắt Wi-Fi...")
        runRootCommand("svc wifi disable")
        Thread.sleep(1500)

        // 2. Ghi file wpa_supplicant mới
        log("📝 Tạo file cấu hình Wi-Fi...")
        val conf = """
        ctrl_interface=DIR=/data/misc/wifi/sockets
        update_config=1
        network={
            ssid="$ssid"
            psk="$password"
            key_mgmt=WPA-PSK
            priority=1
            scan_ssid=1
        }
    """.trimIndent().replace("\"", "\\\"")

        runRootCommand("printf \"$conf\" > $tmpPath")
        runRootCommand("chmod 644 $tmpPath")

        // 3. Chép vào nơi cấu hình hệ thống
        log("📂 Ghi đè file cấu hình...")
        runRootCommand("cp $tmpPath $finalPath")
        runRootCommand("chown system:wifi $finalPath")
        runRootCommand("chmod 660 $finalPath")

        log("📶 Add suggestion")
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

        // Huỷ suggest cũ nếu có
        if (currentSuggestions.isNotEmpty()) {
            wifiManager.removeNetworkSuggestions(currentSuggestions)
        }

        wifiManager.addNetworkSuggestions(listOf(suggestion))

        // 4. Khởi động lại wpa_supplicant
        log("🔁 Khởi động lại wpa_supplicant...")
        runRootCommand("setprop ctl.stop wpa_supplicant")
        Thread.sleep(500)
        runRootCommand("setprop ctl.start wpa_supplicant")
        Thread.sleep(1500)



        // 5. Gửi lệnh reconfigure
        log("📡 Reconfigure...")
        val wpaPath = "/data/local/tmp/wpa_cli"
        val socketPath = "/data/vendor/wifi/wpa/sockets"
//        "wpa_cli -i wlan0 add_network",
//                "wpa_cli -i wlan0 set_network 0 ssid '\"$ssid\"'",
//                "wpa_cli -i wlan0 set_network 0 psk '\"$password\"'",
//                "wpa_cli -i wlan0 enable_network 0",
//                "wpa_cli -i wlan0 save_config",
//                "wpa_cli -i wlan0 select_network 0"
//        ensureWpaCliExists(context)
//        connectWithWpaCliAutoDetect(ssid, password)

//        /data/local/tmp/wpa_cli -p $socketPath

        log("📡 Reconfigure... add_network")
        runRootCommand("$wpaPath -p $socketPath -i wlan0 add_network")
        log("📡 Reconfigure... set_network ssid")
//        val ssidQuoted = "\"$ssid\""
//        val ssidEscaped = "\\\"$ssid\\\""
//        val cmd = "$wpaPath -p $socketPath -i wlan0 set_network 0 ssid \"$ssidEscaped\""
//        runRootCommand(cmd)
//        val ssidEscaped = "\\\"$ssid\\\""
        val quotedSsid = "\"$ssid\""
        val command = "echo 'set_network 0 ssid $quotedSsid' | $wpaPath -p $socketPath -i wlan0"
//        runRootCommand("$wpaPath -p $socketPath -i wlan0 set_network 0 ssid $ssidEscaped")
        runRootCommand(command)

        log("📡 Reconfigure... set_network psk")
        runRootCommand("$wpaPath -p $socketPath -i wlan0 set_network 0 psk $password")
        log("📡 Reconfigure... enable_network")
        runRootCommand("$wpaPath -p $socketPath -i wlan0 enable_network 0")
        log("📡 Reconfigure... save_config")
        runRootCommand("$wpaPath -p $socketPath -i wlan0 save_config")
        log("📡 Reconfigure...select_network")
        runRootCommand("$wpaPath -p $socketPath -i wlan0 select_network 0")

        runRootCommand("$wpaPath -p $socketPath -i wlan0 reconfigure")
        runRootCommand("$wpaPath -p $socketPath -i wlan0 reconnect")

        // 6. Bật Wi-Fi
        log("📶 Bật lại Wi-Fi...")
        runRootCommand("svc wifi enable")

        log("✅ Kết thúc. Chờ vài giây để kết nối hoàn tất.")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToWifiRoot2(context: Context, ssid: String, password: String) {
        fun log(message: String) = Log.d("WifiConnect", message)

        fun runRoot(cmd: String): String {
            return try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val out = p.inputStream.bufferedReader().readText().trim()
                val err = p.errorStream.bufferedReader().readText().trim()
                p.waitFor()
                if (err.isNotEmpty()) log("⚠️ stderr: $err")
                if (out.isNotEmpty()) log("ℹ️ stdout: $out")
                out
            } catch (e: Exception) {
                log("❌ Lỗi chạy root: $cmd\n${e.message}")
                ""
            }
        }

        val wpaPath = "/data/local/tmp/wpa_cli"
        val tmpConf = "/data/local/tmp/wpa_supplicant.conf"
        val sysConf = "/data/misc/wifi/wpa_supplicant.conf"

        log("📶 Bắt đầu connect SSID=$ssid")

        // 1. Tắt Wi-Fi
        log("📴 Tắt Wi-Fi")
        runRoot("svc wifi disable")
        Thread.sleep(1500)

        // 2. Tạo conf tạm
        log("📝 Ghi conf tạm")
        val conf = """
      ctrl_interface=DIR=/data/misc/wifi/sockets
      update_config=1

      network={
        ssid="$ssid"
        psk="$password"
        key_mgmt=WPA-PSK
        priority=1
        scan_ssid=1
      }
    """.trimIndent().replace("\"", "\\\"")
        runRoot("printf \"$conf\" > $tmpConf")
        runRoot("chmod 644 $tmpConf")

        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()
        val wifiManager = context.getSystemService(WifiManager::class.java)
        wifiManager.addNetworkSuggestions(listOf(suggestion))

        // 3. Đè conf hệ thống
        log("📂 Đè conf hệ thống")
        runRoot("cp $tmpConf $sysConf")
        runRoot("chown system:wifi $sysConf")
        runRoot("chmod 660 $sysConf")
        Thread.sleep(500)

        // 4. Dừng & start lại wpa_supplicant
        log("🔁 Restart wpa_supplicant")
        // cố gắng killall nếu có
        runRoot("killall wpa_supplicant")
        Thread.sleep(500)
        runRoot("wpa_supplicant -B -i wlan0 -c $sysConf")
        Thread.sleep(1000)

        // 5. Gửi lệnh reconfigure + reconnect bằng full path
        log("📡 Reconfigure...")
        runRoot("$wpaPath -i wlan0 reconfigure")
        Thread.sleep(1000)
        log("📡 Reconnect...")
        runRoot("$wpaPath -i wlan0 reconnect")
        Thread.sleep(1000)

        // 5. Lấy IP qua DHCP
        log("🌐 Lấy IP DHCP")
        // nếu bạn có dhcpcd
        runRoot("dhcpcd wlan0")
        // hoặc thử dhclient
        // runRoot("dhclient wlan0")
        Thread.sleep(2000)


        // 6. Bật lại Wi-Fi (còn nếu wpa_supplicant đã attach thì không cần)
        log("📳 Bật lại Wi-Fi")
        runRoot("svc wifi enable")
        Thread.sleep(2000)

        // 7. Kiểm tra kết nối
        log("🔍 Kiểm tra trạng thái")
        runRoot("dumpsys wifi | grep SSID")
        runRoot("ip addr show wlan0")

        log("✅ Hoàn tất connect SSID (xem log để debug tiếp)")
    }


    fun logWifiDiagnostics() {
        val tags = listOf(
            "WifiConfigStore",
            "WifiNative",
            "SupplicantState",
            "WifiConnectivityManager",
            "wpa_supplicant"
        )

        val grepPattern = tags.joinToString("|") { it }

        val command = "logcat -d | grep -E '$grepPattern'"

        try {
            Log.d("WifiDiag", "📡 Đang thu thập log Wi-Fi...")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()

            if (output.isBlank()) {
                Log.w("WifiDiag", "⚠️ Không tìm thấy log khớp với các tag đã chỉ định.")
            } else {
                Log.d("WifiDiag", "📄 Log Wi-Fi thu được:\n$output")
            }
        } catch (e: Exception) {
            Log.e("WifiDiag", "❌ Không thể đọc logcat: ${e.message}")
        }
    }


    fun ensureWpaCli(context: Context): Boolean {
        val logTag = "WifiConnect"
        fun log(msg: String) = Log.d(logTag, msg)

        // Kiểm tra đã có wpa_cli trong PATH chưa
        val hasWpa = Runtime.getRuntime()
            .exec(arrayOf("su", "-c", "which wpa_cli"))
            .inputStream.bufferedReader().readText()
            .trim().isNotEmpty()
        if (hasWpa) {
            log("✅ wpa_cli đã có sẵn trên thiết bị")
            return true
        }

        // Đường dẫn tạm trên thiết bị
        val tmpPath = "/data/local/tmp/wpa_cli"

        // 1. Trích xuất từ assets
        log("📦 Trích xuất wpa_cli từ assets → $tmpPath")
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $tmpPath"))
            context.assets.open("wpa_cli").use { input ->
                process.outputStream.use { out ->
                    input.copyTo(out)
                }
            }
            val code = process.waitFor()
            Log.d("WifiConnect", "Exit code: $code")

        } catch (e: Exception) {
            log("❌ Lỗi extract wpa_cli: ${e.message}")
            return false
        }

        // 2. Cấp quyền exec
        log("🔒 Chmod + chown cho wpa_cli")
        Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $tmpPath")).waitFor()
        Runtime.getRuntime().exec(arrayOf("su", "-c", "chown root:root $tmpPath")).waitFor()

        // 3. Kiểm tra lại
        val ok = Runtime.getRuntime()
            .exec(arrayOf("su", "-c", tmpPath + " -v"))
            .inputStream.bufferedReader().readText()
            .isNotBlank()
        if (ok) log("✅ wpa_cli đã sẵn sàng tại $tmpPath")
        else log("❌ Không chạy được wpa_cli tại $tmpPath")

        return ok
    }

    fun ensureWpaCliExists(context: Context) {
        val destPath = "/data/local/tmp/wpa_cli"
        val destFile = File(destPath)

        // Nếu file đã tồn tại, không cần copy lại
        if (destFile.exists()) {
            Log.d("WiFiConnect", "✅ File wpa_cli đã tồn tại tại $destPath")
            return
        }

        val cacheFile = File(context.cacheDir, "wpa_cli")

        if (!cacheFile.exists()) {
            try {
                Log.d("WiFiConnect", "📦 Copy wpa_cli từ assets vào cache...")
                context.assets.open("wpa_cli").use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("WiFiConnect", "✅ Đã copy vào cache: ${cacheFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("WiFiConnect", "❌ Lỗi khi copy wpa_cli vào cache: ${e.message}")
                return
            }
        }

        try {
            Log.d("WiFiConnect", "🔐 Đang chuyển wpa_cli vào /data/local/tmp với quyền root...")

            val command = """
            cp ${cacheFile.absolutePath} /data/local/tmp/wpa_cli
            chmod 755 /data/local/tmp/wpa_cli
        """.trimIndent()

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor()

            Log.d("WiFiConnect", "✅ Đã copy và chmod xong wpa_cli tại /data/local/tmp")
        } catch (e: Exception) {
            Log.e("WiFiConnect", "❌ Lỗi khi chuyển wpa_cli bằng su: ${e.message}")
        }
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    fun openWifiSettingsWithSuggestion(context: Context, ssid: String, password: String) {
        val shell = Runtime.getRuntime()

        fun runCommand(cmd: String): Pair<Boolean, String> {
            return try {
                val process = shell.exec(arrayOf("su", "-c", cmd))
                val exitCode = process.waitFor()
                val output = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText().trim()
                Pair(exitCode == 0, if (output.isNotEmpty()) output else error)
            } catch (e: Exception) {
                Pair(false, e.message ?: "Unknown error")
            }
        }

        val logs = StringBuilder()

        val (disableOk, disableLog) = runCommand("svc wifi disable")
        logs.appendLine(" - Tắt Wi-Fi: ${if (disableOk) "✅" else "❌ $disableLog"}")
        Thread.sleep(3000)



        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

        // Huỷ suggest cũ nếu có
        if (currentSuggestions.isNotEmpty()) {
            wifiManager.removeNetworkSuggestions(currentSuggestions)
        }

        // Tạo suggest mới
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        currentSuggestions = listOf(suggestion)

        val status = wifiManager.addNetworkSuggestions(currentSuggestions)
        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            Log.d("WiFiSuggest", "✅ Suggest Wi-Fi thành công")
        } else {
            Log.e("WiFiSuggest", "❌ Lỗi suggest Wi-Fi: $status")
        }

        val (enableOk, enableLog) = runCommand("svc wifi enable")
        logs.appendLine(" - Bật Wi-Fi: ${if (enableOk) "✅" else "❌ $enableLog"}")

        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)



    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectWifiViaAccessibility(context: Context, ssid: String, password: String) {
        checkAndRequestAccessibility(context)

        Log.d("WifiConnect", "wifi_prefs $ssid | $password")
        val prefs = context.applicationContext.getSharedPreferences("wifi_prefs", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("should_run_accessibility", true)
            .putString("ssid", ssid)
            .putString("password", password)
            .apply()

    }


//    private val accessibilityServiceId by lazy {
//        ComponentName(this, WifiAccessibilityService::class.java).flattenToString()
//    }


    private fun checkAndRequestAccessibility(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val accessibilityServiceId by lazy {
                ComponentName(context, WifiAccessibilityService::class.java).flattenToString()
            }
            if (!isAccessibilityServiceEnabled(context, accessibilityServiceId)) {
                Log.d("WiFiConnect", "⚠️ Trợ năng chưa được bật. Hiển thị cảnh báo...")

                android.app.AlertDialog.Builder(context)
                    .setTitle("Yêu cầu quyền Trợ năng")
                    .setMessage("Ứng dụng cần quyền Trợ năng để tự động kết nối Wi-Fi.\n\nBấm OK để mở cài đặt và cấp quyền.")
                    .setCancelable(false) // không cho bấm ra ngoài
                    .setPositiveButton("OK") { _, _ ->
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    .show()
            } else {
                Log.d("WiFiConnect", "✅ Trợ năng đã được bật.")
                // Có thể tiếp tục thực hiện kết nối Wi-Fi ở đây
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceId: String): Boolean {
        val enabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }

        if (enabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(settingValue)
            while (colonSplitter.hasNext()) {
                if (colonSplitter.next().equals(serviceId, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    fun connectWithWpaCliAutoDetect(targetSSID: String, networkId: String) {
        val possibleSocketPaths = listOf(
            "/data/misc/wifi/sockets",
            "/data/vendor/wifi/wpa/sockets",
            "/data/vendor/wifi/wpa_supplicant",
            "/data/misc/wifi",
            "/data/vendor/wifi"
        )

        var detectedPath: String? = null

        for (path in possibleSocketPaths) {
            val socketFile = File("$path/wlan0")
            if (socketFile.exists()) {
                detectedPath = path
                Log.d("WiFiConnect", "✅ Tìm thấy socket wlan0 tại: $path")
                break
            }
        }

        if (detectedPath == null) {
            Log.e("WiFiConnect", "🚫 Không tìm thấy socket wlan0 trong các thư mục đã biết.")
            return
        }

        try {
            val statusCmd = arrayOf("su", "-c", "/data/local/tmp/wpa_cli -p $detectedPath -i wlan0 status")
            val statusOutput = Runtime.getRuntime().exec(statusCmd).inputStream.bufferedReader().use { it.readText() }

            Log.d("WiFiConnect", "📡 Kết quả status:\n$statusOutput")

            val currentSSID = Regex("ssid=(.+)").find(statusOutput)?.groupValues?.get(1)?.trim('"')

            if (currentSSID == targetSSID) {
                Log.d("WiFiConnect", "✅ Đã kết nối đúng SSID: $currentSSID")
                return
            }

            Log.w("WiFiConnect", "🔄 SSID hiện tại là: $currentSSID. Đang chuyển sang SSID: $targetSSID")

            val commands = listOf(
                "wpa_cli -p $detectedPath -i wlan0 disconnect",
                "wpa_cli -p $detectedPath -i wlan0 select_network $networkId",
                "wpa_cli -p $detectedPath -i wlan0 reconnect"
            )

            for (cmd in commands) {
                val fullCmd = arrayOf("su", "-c", "/data/local/tmp/$cmd")
                val process = Runtime.getRuntime().exec(fullCmd)
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }

                Log.d("WiFiConnect", "🔧 Lệnh `$cmd` → Kết quả:\n$stdout")
                if (stderr.isNotBlank()) {
                    Log.w("WiFiConnect", "⚠️ stderr: $stderr")
                }

                process.waitFor()
            }

            Log.d("WiFiConnect", "🚀 Đã gửi lệnh kết nối đến SSID: $targetSSID (network id: $networkId)")

        } catch (e: Exception) {
            Log.e("WiFiConnect", "❌ Lỗi khi thực hiện wpa_cli: ${e.message}", e)
        }
    }

    fun getWpaSocketPath(): String? {
        val possibleParents = listOf(
            "/data/vendor/wifi/wpa/sockets",
            "/data/vendor/wifi",
            "/data/misc/wifi",
            "/data/misc/wifi/sockets"
        )

        for (parent in possibleParents) {
            val socketFile = File(parent, "wlan0")
            if (socketFile.exists()) {
                Log.d("WiFiConnect", "✅ Socket tìm thấy tại: ${socketFile.absolutePath}")
                return parent
            }
        }

        Log.e("WiFiConnect", "🚫 Không tìm thấy socket wlan0 trong các thư mục đã biết.")
        return null
    }

    fun runWpaCliCommand(command: String): String {
        val socketPath = getWpaSocketPath() ?: return "Socket not found"

        val cmd = arrayOf("su", "-c", "/data/local/tmp/wpa_cli -p $socketPath -i wlan0 $command")
        return try {
            val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e("WiFiConnect", "🚫 Lỗi khi chạy wpa_cli: ${e.message}")
            "Error: ${e.message}"
        }
    }


}