package com.pedidosya.kata.data.mapper

import com.pedidosya.kata.data.local.CartItemEntity
import com.pedidosya.kata.data.remote.dto.CartItemDto
import com.pedidosya.kata.domain.model.CartItem

/** Wire → cache. A missing `title` in the mock response falls back to an empty string. */
fun CartItemDto.toEntity(): CartItemEntity = CartItemEntity(
    id = id,
    title = title.orEmpty(),
    category = category,
    quantity = quantity,
    price = price,
    imageUrl = imageUrl
)

/** Cache → domain, the shape [com.pedidosya.kata.domain.repository.CartRepository] exposes. */
fun CartItemEntity.toDomain(): CartItem = CartItem(
    id = id,
    title = title,
    category = category,
    quantity = quantity,
    price = price,
    imageUrl = imageUrl
)
