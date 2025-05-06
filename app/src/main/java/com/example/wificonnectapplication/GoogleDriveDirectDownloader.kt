package com.example.wificonnectapplication

import android.app.AlertDialog
import android.content.Context
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.example.wificonnectapplication.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object GoogleDriveDirectDownloader {
    private const val TAG = "GoogleDriveDirectDownloader"

    fun downloadAndProcessFile(
        context: Context, // Nhận Context của Activity
        downloadUrl: String,
        db: WifiDatabase,
        updateDataList: (List<String>) -> Unit
    ) {
        var newdownloadUrl = convertGoogleDriveViewLinkToDownloadLink(downloadUrl);

        GlobalScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(newdownloadUrl.toString())
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Lỗi tải file: ${response.code} ${response.message}")
                    withContext(Dispatchers.Main) {
                        // Xử lý lỗi trên UI thread (ví dụ: hiển thị thông báo)
                        AlertDialog.Builder(context)
                            .setTitle("Thông báo")
                            .setMessage("Lỗi tải file: ${response.code} ${response.message}")
                            .setCancelable(true)
                            .show()
                    }
                    return@launch
                }

                val inputStream = response.body?.byteStream()
                if (inputStream == null) {
                    Log.e(TAG, "Không có nội dung trả về từ server")
                    withContext(Dispatchers.Main) {
                        // Xử lý lỗi trên UI thread
                    }
                    return@launch
                }

                val reader = BufferedReader(InputStreamReader(inputStream))
                val dataList = mutableListOf<WifiEntity>()
                var line: String?
//                var line: String? = reader.readLine()
//                while (line != null) {
//                    dataList.add(line)
//                    line = reader.readLine()
//                }
                try {
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line?.split("|")
                        parts?.size?.let {
                            if (it >= 1) { // Đảm bảo có ít nhất SSID
                                val ssid = parts[0].trim()
                                val password = parts.getOrNull(1)?.trim() // Lấy mật khẩu nếu có
//                                dataList.add(WifiEntity(ssid, password))

//                                db.wifiDao().insert(WifiEntity(0, ssid,
//                                    password.toString(), false))

                                // Kiểm tra xem SSID đã tồn tại trong database chưa
                                val existingWifi = db.wifiDao().findBySsid(ssid)

                                if (existingWifi == null) {
                                    // Nếu không tồn tại, thêm mới
                                    db.wifiDao().insert(WifiEntity(0, ssid, password.toString(), false))
                                    Log.d("ImportWifi", "Đã thêm SSID: $ssid")
                                } else {
                                    Log.w("ImportWifi", "SSID đã tồn tại, bỏ qua: $ssid")
                                }


                            } else {
                                Log.w("InputStreamResult", "Dòng không đúng định dạng: $line")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("InputStreamResult", "Lỗi khi đọc InputStream", e)
                } finally {
                    try {
                        inputStream.close()
                        reader.close()
                    } catch (e: Exception) {
                        Log.e("InputStreamResult", "Lỗi khi đóng InputStream hoặc Reader", e)
                    }
                }
                reader.close()

                withContext(Dispatchers.Main) {
                    if (context is MainActivity) { // Kiểm tra xem context có phải là MainActivity không
                        context.loadWifiList() // Gọi loadWifiList
                    } else {
                        Log.w(TAG, "Context không phải là MainActivity, không thể gọi loadWifiList()")
                    }
                }

//                withContext(Dispatchers.Main) {
//                    updateDataList(dataList)
//                }

            } catch (e: IOException) {
                Log.e(TAG, "Lỗi khi tải và đọc file", e)
                withContext(Dispatchers.Main) {
                    // Xử lý lỗi trên UI thread
                }
            }
        }
    }



    fun convertGoogleDriveViewLinkToDownloadLink(viewLink: String): String? {
        val fileIdRegex = Regex("/d/([a-zA-Z0-9_-]+)/")
        val matchResult = fileIdRegex.find(viewLink)

        return if (matchResult != null) {
            val fileId = matchResult.groupValues[1]
            "https://drive.usercontent.google.com/u/0/uc?id=$fileId&export=download"
        } else {
            null // Trả về null nếu không tìm thấy file ID
        }
    }

}

// Cách sử dụng (ví dụ, trong MainActivity):
// val googleDriveDownloadUrl = "https://drive.usercontent.google.com/u/0/uc?id=1_H9we2RmX_HAXXvLX2Qlgpo3l35Dlm0w&export=download"

// GoogleDriveDirectDownloader.downloadAndProcessFile(googleDriveDownloadUrl) { newData ->
//     // Xử lý danh sách newData (ví dụ, cập nhật RecyclerView)
//     Log.d("MainActivity", "Dữ liệu đã tải: $newData")
// }