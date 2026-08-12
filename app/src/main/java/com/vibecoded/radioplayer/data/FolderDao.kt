package com.vibecoded.radioplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Folder>

    @Insert
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)
}
