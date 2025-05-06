package com.example.wificonnectapplication

//import android.R.attr.delay
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
//import androidx.room.withTransaction
//import com.example.wificonnectapplication.MainActivity
//import com.example.wificonnectapplication.WifiShellHelper.connectWifiWithShell
import com.example.wificonnectapplication.WifiUtil.connectToWifiAndWaitForConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.app.ProgressDialog



class WifiAdapter(
    private val context: Context,
    private var list: MutableList<WifiEntity>, // Sử dụng 'var' để có thể gán danh sách mới
    private val onRefresh: () -> Unit,
) : RecyclerView.Adapter<WifiAdapter.ViewHolder>() {

    private var progressDialog: ProgressDialog? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtSsid: TextView = view.findViewById(R.id.txtSsid)
        val txtPass: TextView = view.findViewById(R.id.txtPass)
        val btnConnect: Button = view.findViewById(R.id.btnConnect)
        val btnEdit: Button = view.findViewById(R.id.btnEdit)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wifi, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wifi = list[position]
        holder.txtSsid.text = wifi.ssid
        holder.txtPass.text = wifi.password

        holder.btnConnect.setOnClickListener {

//            val result: String = connectWifiWithShell(wifi.ssid, wifi.password)
//
//            // Xử lý kết quả
//            if (result.contains("OK") || result.contains("SUCCESS")) {
//                // Kết nối có vẻ thành công (tùy thuộc vào output thực tế)
//                // Hiển thị thông báo cho người dùng
//                GlobalScope.launch {
//                    val db = WifiDatabase.getInstance(context)
//                    db.wifiDao().markAsUsed(wifi.id)
//                    onRefresh()
//                }
//            } else {
//                // Kết nối thất bại
//                // Hiển thị thông báo lỗi cho người dùng
//            }

            WifiUtil.scanWifiNetworks(context) { scanResults ->
                // Xử lý kết quả quét
                for (result in scanResults) {
                    // ...
                }
            }

            // Kết nối đến mạng Wi-Fi khi cần
            // WifiUtil.connectToWifi(this, "Tên_WiFi", "Mật_Khẩu")
//            val success = WifiHelper.connect(context, wifi.ssid, wifi.password)
//            val success = WifiUtil.connectToWifi2(context, wifi.ssid, wifi.password)

            progressDialog = ProgressDialog(context).apply { // 'this' là Activity context
                setMessage("Đang kết nối với ${wifi.ssid}...")
                setCancelable(false) // Không cho phép người dùng hủy bằng cách chạm ra ngoài
                show()
            }

            Toast.makeText(context, "Đang kết nối với " + wifi.ssid, Toast.LENGTH_SHORT).show()
            GlobalScope.launch {
                val isConnected = connectToWifiAndWaitForConnection(context, wifi.ssid, wifi.password)
                progressDialog?.dismiss() // Đóng dialog khi kết nối xong
                if (isConnected) {
                    Log.d("WifiUtil", "Kết nối thành công với " + wifi.ssid)
                    // Thực hiện các hành động sau khi kết nối thành công

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context.applicationContext, "Kết nối thành công với " + wifi.ssid, Toast.LENGTH_SHORT).show()
                    }

                    try {
                        val db = WifiDatabase.getInstance(context)
                        db.wifiDao().markAsUsed(wifi.id)
//                        db.withTransaction {
//                            db.wifiDao().markAsUsed(wifi.id)
//                        }

                        delay(500) // Chờ 500ms
                        onRefresh()
                    }
                    catch (e: Exception) {
                        Log.e("db", "Lỗi data", e)
                    }
                } else {
                    Log.e("WifiUtil", "Không thể kết nối với " + wifi.ssid)
                    // Xử lý lỗi kết nối
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context.applicationContext, "Không thể kết nối với " + wifi.ssid, Toast.LENGTH_SHORT).show()
                    }

                }
            }


//            // Lấy thông tin mạng đã kết nối
            val connectedInfo = WifiUtil.getConnectedWifiInfo(context)


//            if (success) {
//                GlobalScope.launch {
//                    try {
//                        val db = WifiDatabase.getInstance(context)
////                        db.wifiDao().markAsUsed(wifi.id)
//                        db.withTransaction {
//                            db.wifiDao().markAsUsed(wifi.id)
//                        }
//
//                        delay(500) // Chờ 500ms
//                        onRefresh()
//                    }
//                    catch (e: Exception) {
//                        Log.e("db", "Lỗi data", e)
//                    }
//
//                }
//            }
        }

        holder.btnEdit.setOnClickListener {
            (context as MainActivity).showEditDialog(wifi)
        }

        holder.btnDelete.setOnClickListener {
            GlobalScope.launch {
                val db = WifiDatabase.getInstance(context)
                db.wifiDao().delete(wifi)
                onRefresh()
            }
        }
    }

    fun updateList(newList: MutableList<WifiEntity>) {
        list = newList // Cập nhật danh sách dữ liệu của adapter
        notifyDataSetChanged() // Thông báo cho RecyclerView rằng toàn bộ dataset đã thay đổi
    }

}


