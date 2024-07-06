package xyz.untan.mstdnp

import android.app.Application

import com.os.operando.garum.Configuration
import com.os.operando.garum.Garum

class Application: Application() {
    override fun onCreate() {
        super.onCreate()

        // initialize Garum
        val builder = Configuration.Builder(applicationContext)
        builder.setModelClasses(AppStatus::class.java)
        Garum.initialize(builder.create(), true)
    }
}