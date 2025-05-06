package com.example.wificonnectapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WifiEntity::class], version = 1)
abstract class WifiDatabase : RoomDatabase() {
    abstract fun wifiDao(): WifiDao

    companion object {
        private var INSTANCE: WifiDatabase? = null

        fun getInstance(context: Context): WifiDatabase {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(
                    context.applicationContext,
                    WifiDatabase::class.java,
                    "wifi_db"
                ).build()
            }
            return INSTANCE!!
        }
    }
}