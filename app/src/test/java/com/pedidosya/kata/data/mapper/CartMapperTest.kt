package com.pedidosya.kata.data.mapper

import com.pedidosya.kata.data.local.CartItemEntity
import com.pedidosya.kata.data.remote.dto.CartItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CartMapperTest {

    @Test
    fun `CartItemDto maps every field into CartItemEntity verbatim`() {
        val dto = CartItemDto(
            id = "prod_001",
            title = "iPhone 15 Pro",
            category = "technology",
            quantity = 1,
            price = 999.0,
            imageUrl = "https://example.com/iphone.png"
        )

        val entity = dto.toEntity()

        assertEquals("prod_001", entity.id)
        assertEquals("iPhone 15 Pro", entity.title)
        assertEquals("technology", entity.category)
        assertEquals(1, entity.quantity)
        assertEquals(999.0, entity.price, 0.0)
        assertEquals("https://example.com/iphone.png", entity.imageUrl)
    }

    @Test
    fun `CartItemDto without image_url maps to a null entity imageUrl`() {
        val dto = CartItemDto(id = "prod_002", title = "Case", category = "technology", quantity = 2, price = 49.0)

        val entity = dto.toEntity()

        assertNull(entity.imageUrl)
    }

    @Test
    fun `CartItemDto without a title falls back to an empty string instead of crashing the mapping`() {
        val dto = CartItemDto(id = "prod_003", category = "grocery", quantity = 2, price = 24.5)

        val entity = dto.toEntity()

        assertEquals("", entity.title)
        assertEquals(24.5, entity.price, 0.0)
    }

    @Test
    fun `CartItemEntity maps every field into the domain CartItem verbatim`() {
        val entity = CartItemEntity(
            id = "prod_004",
            title = "Bread",
            category = "grocery",
            quantity = 1,
            price = 12.0,
            imageUrl = null
        )

        val item = entity.toDomain()

        assertEquals("prod_004", item.id)
        assertEquals("Bread", item.title)
        assertEquals("grocery", item.category)
        assertEquals(1, item.quantity)
        assertEquals(12.0, item.price, 0.0)
        assertNull(item.imageUrl)
    }
}
