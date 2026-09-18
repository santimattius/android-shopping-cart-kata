package com.pedidosya.kata.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room cache row for a single cart line item; the offline-first single source of truth. */
@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val quantity: Int,
    val price: Double,
    val imageUrl: String?
)
