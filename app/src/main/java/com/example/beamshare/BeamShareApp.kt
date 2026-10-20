package com.example.beamshare

import android.app.Application
import com.facebook.shimmer.BuildConfig
import timber.log.Timber

class BeamShareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}