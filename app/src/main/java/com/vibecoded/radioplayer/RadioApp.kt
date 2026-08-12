package com.vibecoded.radioplayer

import android.app.Application
import com.vibecoded.radioplayer.data.AppDatabase

class RadioApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
