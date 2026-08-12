package com.vibecoded.radioplayer.data

import kotlinx.coroutines.flow.Flow

class FolderRepository(private val dao: FolderDao) {
    fun observeFolders(): Flow<List<Folder>> = dao.observeAll()
    suspend fun getAll(): List<Folder> = dao.getAll()
    suspend fun add(folder: Folder): Long = dao.insert(folder)
    suspend fun update(folder: Folder) = dao.update(folder)
    suspend fun delete(folder: Folder) = dao.delete(folder)
}
