package com.example.wificonnectapplication

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Process
import android.util.Log
import android.provider.Settings
import android.text.TextUtils
import androidx.room.withTransaction
import com.example.wificonnectapplication.WifiUtil.connectWifi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlin.jvm.java

private const val PREF_NAME = "ImportSettings"
private const val KEY_IMPORT_LINK = "importLink"


class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var db: WifiDatabase
    private lateinit var adapter: WifiAdapter
    private var wifiList: MutableList<WifiEntity> = mutableListOf() // Khởi tạo với một danh sách rỗng

    private val accessibilityServiceId by lazy {
        ComponentName(this, WifiAccessibilityService::class.java).flattenToString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        if (!RootUtil.isDeviceRooted) {
            showNoRootPermissionAlert(this)
        }

        WifiUtil.setWifiEnabled(this, true) // Bật Wi-Fi

//        // Khi app khởi động, ép khởi động lại AccessibilityService (nếu đã được bật)
//        if (isAccessibilityServiceEnabled(this, "com.example.wificonnectapplication/.WifiAccessibilityService")) {
//            Log.d("WiFiConnect", "✔ Trợ năng đã bật, thử gọi lại một hành động nhỏ để khởi động Service.")
//            // Có thể thử kích hoạt lại UI logic hoặc bắn 1 broadcast giả để kích service
//        }
//
//        checkAndRequestAccessibility()


        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        db = WifiDatabase.getInstance(this)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val btnImport = findViewById<Button>(R.id.btnImport)
        val btnRandomConnect = findViewById<Button>(R.id.btnRandomConnect)

        recyclerView.layoutManager = LinearLayoutManager(this)

//        adapter = WifiAdapter(mutableListOf(), { wifi -> connectToWifi(wifi as WifiEntity) }, { wifi -> showEditDialog(wifi as WifiEntity) }, { wifi -> deleteWifi(wifi as WifiEntity) })
        adapter = WifiAdapter(this, wifiList , { loadWifiList() })

        recyclerView.adapter = adapter

        loadWifiList()

        val googleDriveDownloadUrl = "https://drive.google.com/file/d/1_H9we2RmX_HAXXvLX2Qlgpo3l35Dlm0w/view?usp=drive_link"
//
//         GoogleDriveDirectDownloader.downloadAndProcessFile(this, googleDriveDownloadUrl,db) { newData ->
//             // Xử lý danh sách newData (ví dụ, cập nhật RecyclerView)
//             Log.d("MainActivity", "Dữ liệu đã tải: $newData")
//         }

        val importLink = getImportLinkFromPrefs().trim()
        if (importLink.isEmpty()) {
            saveImportLinkToPrefs(googleDriveDownloadUrl) // Lưu lại link
        }

        btnAdd.setOnClickListener {
            showAddDialog()
        }

        btnRefresh.setOnClickListener {
            refreshList()
        }

        btnImport.setOnClickListener {
            showImportDialog()
        }

        btnRandomConnect.setOnClickListener {
            if (wifiList.isEmpty()) {
                Toast.makeText(this, "📭 Danh sách Wi-Fi rỗng", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Chọn ngẫu nhiên một mạng Wi-Fi
            val randomNetwork = wifiList.random()
            val context = this // <- lưu lại context đúng

            Toast.makeText(this, "🔌 Đang kết nối tới: ${randomNetwork.ssid}", Toast.LENGTH_SHORT).show()

            // Gọi hàm kết nối
//            connectToWifiRoot(this, randomNetwork.ssid, randomNetwork.password)
            GlobalScope.launch {
                val isConnected = connectWifi(context, randomNetwork.ssid, randomNetwork.password)
                if (isConnected) {
                    Log.d("WifiUtil", "Kết nối thành công với " + randomNetwork.ssid)
                    // Thực hiện các hành động sau khi kết nối thành công

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context.applicationContext, "Kết nối thành công với " + randomNetwork.ssid, Toast.LENGTH_SHORT).show()
                    }


                    try {
                        val db = WifiDatabase.getInstance(context)
//                        db.wifiDao().markAsUsed(randomNetwork.id)
                        db.withTransaction {
                            db.wifiDao().markAsUsed(randomNetwork.id)
                        }

                        delay(500) // Chờ 500ms
                        loadWifiList()
                    }
                    catch (e: Exception) {
                        Log.e("db", "Lỗi data", e)
                    }
                } else {
                    Log.e("WifiUtil", "Không thể kết nối với " + randomNetwork.ssid)
                    // Xử lý lỗi kết nối
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context.applicationContext, "Không thể kết nối với " + randomNetwork.ssid, Toast.LENGTH_SHORT).show()
                    }


                }
            }
        }
    }

    fun loadWifiList() {
        lifecycleScope.launch {
            wifiList = db.wifiDao().getAllNotUse().toMutableList()
            adapter.updateList(wifiList.filter { !it.used }.toMutableList())
        }
    }

    private fun refreshList() {
        lifecycleScope.launch {
            db.wifiDao().resetUsed()
            loadWifiList()
            Toast.makeText(this@MainActivity, "Danh sách đã được làm mới", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToWifi(wifi: WifiEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = WifiHelper.connect(this@MainActivity, wifi.ssid, wifi.password)
            if (result) {
                db.wifiDao().markAsUsed(wifi.id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Đã kết nối ${wifi.ssid}", Toast.LENGTH_SHORT).show()
                    loadWifiList()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Kết nối thất bại", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteWifi(wifi: WifiEntity) {
        lifecycleScope.launch {
            db.wifiDao().delete(wifi)
            loadWifiList()
            Toast.makeText(this@MainActivity, "Đã xóa", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddDialog() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_wifi, null)
        builder.setView(view)

        val edtSsid = view.findViewById<EditText>(R.id.edtSsid)
        val edtPassword = view.findViewById<EditText>(R.id.edtPassword)

        builder.setTitle("Thêm Wi-Fi")
            .setPositiveButton("Lưu") { _, _ ->
                val ssid = edtSsid.text.toString()
                val pass = edtPassword.text.toString()
                lifecycleScope.launch {
                    db.wifiDao().insert(WifiEntity(0, ssid, pass, false))
                    loadWifiList()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    fun showEditDialog(wifi: WifiEntity) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_wifi, null)
        builder.setView(view)

        val edtSsid = view.findViewById<EditText>(R.id.edtSsid)
        val edtPassword = view.findViewById<EditText>(R.id.edtPassword)

        edtSsid.setText(wifi.ssid)
        edtPassword.setText(wifi.password)

        builder.setTitle("Sửa Wi-Fi")
            .setPositiveButton("Cập nhật") { _, _ ->
                val ssid = edtSsid.text.toString()
                val pass = edtPassword.text.toString()
                lifecycleScope.launch {
                    db.wifiDao().update(wifi.copy(ssid = ssid, password = pass))
                    loadWifiList()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showImportDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Import danh sách SSID|PASS")

        val importLinkFromPrefs = getImportLinkFromPrefs()

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.hint = "Nhập đường dẫn link"
        input.setText(importLinkFromPrefs) // Hiển thị link đã lưu (nếu có)
        builder.setView(input)

        builder.setPositiveButton("Import") { _, _ ->
            val importLink = input.text.toString().trim()
            if (importLink.isNotEmpty()) {
                saveImportLinkToPrefs(importLink) // Lưu lại link

                // **Thêm logic để tải và xử lý dữ liệu từ link ở đây**
                // Ví dụ (cần implement hàm downloadAndProcessFromLink):
                lifecycleScope.launch {
                    GoogleDriveDirectDownloader.downloadAndProcessFile(this@MainActivity, importLink,db) { newData ->
                        // Xử lý danh sách newData (ví dụ, cập nhật RecyclerView)
                        Log.d("MainActivity", "Dữ liệu đã tải: $newData")
                    }
                }
            }
        }
        builder.setNegativeButton("Hủy", null)

        builder.show()
    }

    fun showNoRootPermissionAlert(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Thông báo")
            .setMessage("Thiết bị chưa có quyền Root để thực hiện các chức năng này.")
            .setPositiveButton("OK") { _, _ ->
                // Đóng ứng dụng khi người dùng nhấn OK
                Process.killProcess(Process.myPid())
            }
            .setCancelable(false) // Ngăn người dùng đóng bằng cách chạm ra ngoài
            .show()
    }

    private fun getImportLinkFromPrefs(): String {
        val prefs: SharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_IMPORT_LINK, "") ?: ""
    }

    private fun saveImportLinkToPrefs(link: String) {
        val prefs: SharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_IMPORT_LINK, link)
        editor.apply() // Hoặc editor.commit() để lưu đồng bộ (nên dùng apply cho background)
    }

    override fun onResume() {
        super.onResume()
//        checkAndRequestAccessibility()
    }




}