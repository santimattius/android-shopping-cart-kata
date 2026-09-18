package com.pedidosya.kata.data.mapper

import com.pedidosya.kata.data.remote.dto.CouponDto
import com.pedidosya.kata.domain.model.Coupon

/** Wire → domain. No entity step: coupons are never cached (Room), unlike cart items. */
fun CouponDto.toDomain(): Coupon = Coupon(
    code = code,
    discountPercentage = discountPercentage,
    applicableCategory = applicableCategory,
    isActive = isActive
)
