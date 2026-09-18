package com.pedidosya.kata

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.pedidosya.kata.ui.navigation.KataNavHost
import com.pedidosya.kata.ui.theme.KataTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KataTheme {
                KataNavHost()
            }
        }
    }

}