package com.pedidosya.kata.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun `titleFor returns Carrito for the cart route`() {
        assertEquals("Carrito", Routes.titleFor(Routes.CART))
    }

    @Test
    fun `titleFor returns Resumen de compra for the summary route pattern`() {
        assertEquals("Resumen de compra", Routes.titleFor(Routes.SUMMARY))
    }

    @Test
    fun `titleFor returns empty string for an unknown or null route`() {
        assertEquals("", Routes.titleFor(null))
        assertEquals("", Routes.titleFor("unknown"))
    }
}
