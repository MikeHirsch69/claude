package com.vibecoded.radioplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromProxyType(value: ProxyType): String = value.name

    @TypeConverter
    fun toProxyType(value: String): ProxyType = ProxyType.valueOf(value)
}

@Database(entities = [Station::class, Folder::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "radio_stations.db"
                )
                    // We're actively developing the schema; this resets local data on schema
                    // changes instead of crashing. Once the app is stable you'd replace this
                    // with real Migration objects to preserve user data across updates.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
