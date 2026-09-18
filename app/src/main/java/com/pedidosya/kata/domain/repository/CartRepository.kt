package com.pedidosya.kata.domain.repository

import com.pedidosya.kata.domain.model.CartItem
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to the cart. [observeCart] is the single source of truth for rendering
 * and is backed by the local cache — it never throws and never blocks on network. [refresh]
 * pulls the latest cart from the remote source and writes it into the cache; UI failures are
 * signalled through its returned [Result], not by [observeCart].
 */
interface CartRepository {

    /** Emits the current cached cart contents; re-emits whenever [refresh] updates the cache. */
    fun observeCart(): Flow<List<CartItem>>

    /** Fetches the cart from the remote source and persists it. */
    suspend fun refresh(): Result<Unit>
}
