package com.pedidosya.kata.data.repository

import com.pedidosya.kata.data.local.CartItemDao
import com.pedidosya.kata.data.mapper.toDomain
import com.pedidosya.kata.data.mapper.toEntity
import com.pedidosya.kata.data.remote.CartApi
import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.repository.CartRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first [CartRepository]: [dao] (Room) is the single source of truth rendered by
 * [observeCart]; [refresh] fetches from [api] and replaces the cache, never emitting network
 * data directly to callers.
 */
class CartRepositoryImpl(
    private val api: CartApi,
    private val dao: CartItemDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CartRepository {

    override fun observeCart(): Flow<List<CartItem>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refresh(): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val entities = api.getCart().cart.items.map { it.toEntity() }
            dao.replaceAll(entities)
        }
    }
}
