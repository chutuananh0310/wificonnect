package com.example.wificonnectapplication

import android.app.AlertDialog
import android.content.Context
import android.os.Process

object AlertDialogUtil {
    fun showNoRootPermissionAlertAndCloseApp(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Thông báo")
            .setMessage("Thiết bị chưa có quyền Root để thực hiện các chức năng này.")
            .setPositiveButton("OK") { _, _ ->
                Process.killProcess(Process.myPid())
            }
            .setCancelable(false)
            .show()
    }

    fun showNoRootPermissionAlert(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Thông báo")
            .setMessage("Thiết bị chưa có quyền Root để thực hiện các chức năng này.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}