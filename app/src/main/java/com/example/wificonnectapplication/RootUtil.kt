package com.example.wificonnectapplication

import java.io.File
import java.io.IOException


object RootUtil {
    val isDeviceRooted: Boolean
        get() {
            if (checkRootMethod1()) {
                return true
            }
            return checkRootMethod2()
        }

    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf<String?>(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }

    private fun checkRootMethod2(): Boolean {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf<String>("/system/xbin/which", "su"))
            return process.waitFor() == 0
        } catch (e: IOException) {
            return false
        } catch (e: InterruptedException) {
            return false
        } finally {
            if (process != null) {
                process.destroy()
            }
        }
    }
}