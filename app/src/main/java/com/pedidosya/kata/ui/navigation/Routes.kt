package com.pedidosya.kata.ui.navigation

import android.net.Uri

/**
 * Route identifiers for [KataNavHost], per `sdd/shopping-cart/design`.
 *
 * [SUMMARY] carries only primitives (code, nominal %, applicable category) as query-style
 * arguments; [summary] builds a concrete destination string from them.
 */
object Routes {
    const val CART = "cart"

    const val SUMMARY_ARG_CODE = "code"
    const val SUMMARY_ARG_PCT = "pct"
    const val SUMMARY_ARG_CATEGORY = "category"

    const val SUMMARY = "summary?code={$SUMMARY_ARG_CODE}&pct={$SUMMARY_ARG_PCT}&category={$SUMMARY_ARG_CATEGORY}"

    /** Builds a navigable Summary destination from [CartEvent.NavigateToSummary][com.pedidosya.kata.ui.cart.CartEvent.NavigateToSummary]'s payload. */
    fun summary(code: String, discountPercentage: Double, applicableCategory: String): String =
        "summary?code=${Uri.encode(code)}&pct=$discountPercentage&category=${Uri.encode(applicableCategory)}"

    /** Screen title for the TopAppBar, keyed by the registered route pattern (not the built destination). */
    fun titleFor(route: String?): String = when (route) {
        CART -> "Carrito"
        SUMMARY -> "Resumen de compra"
        else -> ""
    }
}
