package com.pedidosya.kata

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pedidosya.kata.ui.navigation.KataNavHost
import com.pedidosya.kata.ui.navigation.Routes
import com.pedidosya.kata.ui.theme.KataTheme

class MainActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KataTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                Scaffold(
                    topBar = {
                        KataTopAppBar(
                            title = Routes.titleFor(currentRoute),
                            onBack = navController.previousBackStackEntry?.let {
                                { navController.popBackStack() }
                            },
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        KataNavHost(navController)
                    }
                }
            }
        }
    }

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun KataTopAppBar(
    title: String = "",
    onBack: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        }
    )
}