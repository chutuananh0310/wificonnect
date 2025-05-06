package com.example.wificonnectapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.util.Log

private const val TAG = "PermissionChecker"

object PermissionChecker {
    fun checkChangeWifiStatePermission(context: Context): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE)
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }



    // Tương tự cho WRITE_SETTINGS (nếu bạn nghi ngờ):
    fun checkWriteSettingsPermission(context: Context): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SETTINGS)
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }


}

