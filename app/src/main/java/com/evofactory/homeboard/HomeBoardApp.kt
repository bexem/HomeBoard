package com.evofactory.homeboard

import android.app.Application

/** Starts the log first, for the launcher and the Home button helper alike */
class HomeBoardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLog.start(this)
        ScreenState.watch(this)
    }

    companion object {
        /** The one application object: the notification listener and the log both read it */
        lateinit var instance: HomeBoardApp
            private set
    }
}
