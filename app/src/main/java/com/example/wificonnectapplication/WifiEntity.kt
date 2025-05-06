package com.example.wificonnectapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class WifiEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ssid: String,
    val password: String,
    val used: Boolean = false
)