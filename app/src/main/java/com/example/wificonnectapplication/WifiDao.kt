package com.example.wificonnectapplication

import androidx.room.*

@Dao
interface WifiDao {
    @Query("SELECT * FROM WifiEntity")
    suspend fun getAll(): List<WifiEntity>

    @Insert
    suspend fun insert(wifi: WifiEntity)

    @Update
    suspend fun update(wifi: WifiEntity)

    @Delete
    suspend fun delete(wifi: WifiEntity)

    @Query("UPDATE WifiEntity SET used = 0")
    suspend fun resetUsed()

    @Query("UPDATE WifiEntity SET used = 1 WHERE id = :id")
    suspend fun markAsUsed(id: Int)

    @Query("SELECT * FROM WifiEntity WHERE ssid = :ssid")
    fun findBySsid(ssid: String): WifiEntity?

    @Query("SELECT * FROM WifiEntity WHERE used = 0")
    suspend fun getAllNotUse(): List<WifiEntity>
}