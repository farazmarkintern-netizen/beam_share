package com.example.beamshare

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.shimmer.BuildConfig
import timber.log.Timber

class BeamShareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}