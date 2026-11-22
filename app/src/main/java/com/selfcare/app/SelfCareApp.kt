package com.selfcare.app

import android.app.Application
import com.selfcare.app.data.local.SelfCareDatabase
import com.selfcare.app.di.AppContainer

class SelfCareApp : Application() {

    @Volatile
    private var _container: AppContainer? = null

    var container: AppContainer
        get() = _container ?: synchronized(this) {
            _container ?: AppContainer(this).also { _container = it }
        }
        internal set(value) {
            synchronized(this) {
                _container = value
            }
        }

    val database: SelfCareDatabase
        get() = container.database

    override fun onCreate() {
        super.onCreate()
        container.notificationHelper.createNotificationChannels()
    }

    fun setTestContainer(testContainer: AppContainer) {
        container = testContainer
    }

    fun resetContainer() {
        synchronized(this) {
            _container = null
        }
    }
}
