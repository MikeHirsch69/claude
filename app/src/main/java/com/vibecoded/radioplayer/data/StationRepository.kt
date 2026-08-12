package com.vibecoded.radioplayer.data

import kotlinx.coroutines.flow.Flow

class StationRepository(private val dao: StationDao) {
    fun observeStations(): Flow<List<Station>> = dao.observeAll()
    suspend fun getAll(): List<Station> = dao.getAll()
    suspend fun getById(id: Long): Station? = dao.getById(id)
    suspend fun add(station: Station): Long = dao.insert(station)
    suspend fun update(station: Station) = dao.update(station)
    suspend fun delete(station: Station) = dao.delete(station)
}
