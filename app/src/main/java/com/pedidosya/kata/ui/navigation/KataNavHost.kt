package com.pedidosya.kata.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pedidosya.kata.ui.cart.CartScreen
import com.pedidosya.kata.ui.summary.SummaryScreen
import com.pedidosya.kata.ui.summary.SummaryViewModel

/**
 * App-wide navigation graph. Cart is the start destination; Confirm Purchase navigates to
 * Summary carrying only primitives (code/nominal %/applicable category), per
 * `sdd/shopping-cart/design`.
 */
@Composable
fun KataNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.CART) {
        composable(Routes.CART) {
            CartScreen(
                onNavigateToSummary = { code, discountPercentage, applicableCategory ->
                    navController.navigate(Routes.summary(code, discountPercentage, applicableCategory))
                },
            )
        }
        composable(
            route = Routes.SUMMARY,
            arguments = listOf(
                navArgument(Routes.SUMMARY_ARG_CODE) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.SUMMARY_ARG_PCT) { type = NavType.FloatType; defaultValue = 0f },
                navArgument(Routes.SUMMARY_ARG_CATEGORY) { type = NavType.StringType; defaultValue = "all" },
            ),
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString(Routes.SUMMARY_ARG_CODE).orEmpty()
            val pct = backStackEntry.arguments?.getFloat(Routes.SUMMARY_ARG_PCT) ?: 0f
            val category = backStackEntry.arguments?.getString(Routes.SUMMARY_ARG_CATEGORY) ?: "all"
            SummaryScreen(
                viewModel = viewModel(
                    factory = SummaryViewModel.factory(code, pct.toDouble(), category),
                ),
            )
        }
    }
}
