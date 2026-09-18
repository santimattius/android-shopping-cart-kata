package com.pedidosya.kata.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pedidosya.kata.ui.cart.CartScreen

/**
 * App-wide navigation graph. Cart is the start destination; the Summary destination is added in
 * a later phase per `sdd/shopping-cart/design`.
 */
@Composable
fun KataNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CART) {
        composable(Routes.CART) { CartScreen() }
    }
}
