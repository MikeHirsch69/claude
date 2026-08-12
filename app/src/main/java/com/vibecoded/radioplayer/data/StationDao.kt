package com.vibecoded.radioplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Station>>

    @Query("SELECT * FROM stations ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Station>

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: Long): Station?

    @Insert
    suspend fun insert(station: Station): Long

    @Update
    suspend fun update(station: Station)

    @Delete
    suspend fun delete(station: Station)
}
