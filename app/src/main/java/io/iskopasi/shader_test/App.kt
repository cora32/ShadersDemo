package io.iskopasi.shader_test

import android.app.Application
import androidx.lifecycle.LifecycleObserver

class App : Application(), LifecycleObserver {
    override fun onCreate() {
        super.onCreate()
//        ProcessLifecycleOwner.get().getLifecycle().addObserver(this)
    }
}