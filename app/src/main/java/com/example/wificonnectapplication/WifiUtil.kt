@file:Suppress("DEPRECATION")

package com.example.wificonnectapplication

// WifiUtil.kt
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
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
import androidx.fragment.app.FragmentActivity
import com.example.wificonnectapplication.PermissionChecker.checkChangeWifiStatePermission
import com.example.wificonnectapplication.PermissionChecker.checkWriteSettingsPermission


object WifiUtil {

    private const val TAG = "WifiUtil"
    private const val REQUEST_LOCATION_SETTINGS = 123 // Mã request duy nhất


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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Ứng dụng thiếu quyền CHANGE_WIFI_STATE")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Gọi hàm này ở nơi bạn muốn kiểm tra quyền
            val hasChangeWifiStatePermission = checkChangeWifiStatePermission(context)
            Log.d(TAG, "Quyền CHANGE_WIFI_STATE được cấp: $hasChangeWifiStatePermission")

                val hasWriteSettingsPermission = checkWriteSettingsPermission(context)
            Log.d(TAG, "Quyền WRITE_SETTINGS được cấp: $hasWriteSettingsPermission")

            // Sử dụng WifiNetworkSpecifier cho Android 10 trở lên
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password!!) // hoặc setOpenNetwork(true), setWpa3Passphrase(), etc.
                .build()

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            try {

                val networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "Đã kết nối thành công với SSID: $ssid (Android 10+)")
                        connectivityManager.unregisterNetworkCallback(this)
                        continuation.resume(true)
                    }

                    override fun onUnavailable() {
                        Log.w(TAG, "Không thể kết nối với SSID: $ssid (Android 10+)")
                        connectivityManager.unregisterNetworkCallback(this)
                        continuation.resume(false)
                    }

                    override fun onLost(network: Network) {
                        Log.w(TAG, "Kết nối với SSID: $ssid bị mất (Android 10+)")
                        connectivityManager.unregisterNetworkCallback(this)
                        continuation.resume(false)
                    }
                }


                connectivityManager.requestNetwork(networkRequest, networkCallback)
                continuation.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                }
            }
            catch (e: Exception)
            {
                Log.e("WifiUtil", "Lỗi khi requestNetwork: ${e.message}", e) // In cả stack trace
            }



        } else {
            // Sử dụng phương pháp addNetwork cho Android 9 trở xuống
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

            val netId = wifiManager.configuredNetworks?.firstOrNull { it.SSID == wifiConfig.SSID }?.networkId ?: -1
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

                val networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val activeNetwork = connectivityManager.getNetworkCapabilities(network)
                        if (activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                            val currentWifiInfo = wifiManager.connectionInfo
                            if (currentWifiInfo != null && currentWifiInfo.ssid.equals("\"${ssid}\"", ignoreCase = true)) {
                                Log.d(TAG, "Đã kết nối thành công với SSID: ${currentWifiInfo.ssid} (Android 9-)")
                                connectivityManager.unregisterNetworkCallback(this)
                                continuation.resume(true)
                            } else {
                                Log.w(TAG, "Đã kết nối Wi-Fi nhưng không phải SSID mong muốn (Android 9-)")
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
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
                continuation.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                }

            } else {
                Log.e(TAG, "Không thể thêm hoặc cập nhật mạng (Android 9-)")
                continuation.resume(false)
            }
        }
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



}