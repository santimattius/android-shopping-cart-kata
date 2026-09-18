package com.pedidosya.kata

import android.app.Application
import com.pedidosya.kata.di.AppContainer
import com.pedidosya.kata.di.DefaultAppContainer

class App : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
