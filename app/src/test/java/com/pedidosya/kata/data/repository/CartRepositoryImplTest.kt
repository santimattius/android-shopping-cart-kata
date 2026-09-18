package com.pedidosya.kata.data.repository

import app.cash.turbine.test
import com.pedidosya.kata.data.local.CartItemDao
import com.pedidosya.kata.data.local.CartItemEntity
import com.pedidosya.kata.data.remote.CartApi
import com.pedidosya.kata.data.remote.dto.CartDto
import com.pedidosya.kata.data.remote.dto.CartItemDto
import com.pedidosya.kata.data.remote.dto.CartResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CartRepositoryImplTest {

    private val api: CartApi = mockk()
    private val dao = FakeCartItemDao()

    @Test
    fun `refresh maps the DTO response and writes it into the cache`() = runTest {
        coEvery { api.getCart() } returns cartResponse(
            CartItemDto(id = "prod_001", title = "iPhone 15 Pro", category = "technology", quantity = 1, price = 999.0)
        )

        val result = repository(dispatcher()).refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf("prod_001"), dao.snapshot().map { it.id })
    }

    @Test
    fun `observeCart re-emits the newly cached items right after refresh writes them`() = runTest {
        coEvery { api.getCart() } returns cartResponse(
            CartItemDto(id = "prod_002", title = "Case", category = "technology", quantity = 2, price = 49.0)
        )
        val repo = repository(dispatcher())

        repo.observeCart().test {
            assertEquals(emptyList<Any>(), awaitItem())
            repo.refresh()
            assertEquals(listOf("prod_002"), awaitItem().map { it.id })
        }
    }

    @Test
    fun `refresh failure returns Result failure and leaves the existing cache untouched`() = runTest {
        dao.seed(CartItemEntity(id = "prod_003", title = "Bread", category = "grocery", quantity = 1, price = 12.0, imageUrl = null))
        coEvery { api.getCart() } throws IOException("network down")

        val result = repository(dispatcher()).refresh()

        assertTrue(result.isFailure)
        assertEquals(listOf("prod_003"), dao.snapshot().map { it.id })
    }

    private fun TestScope.dispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    private fun repository(dispatcher: CoroutineDispatcher) =
        CartRepositoryImpl(api = api, dao = dao, dispatcher = dispatcher)

    private fun cartResponse(vararg items: CartItemDto) =
        CartResponseDto(cart = CartDto(id = "cart_1", currency = "USD", items = items.toList()))
}

/** In-memory fake so this suite verifies repository logic, not Room itself. */
private class FakeCartItemDao : CartItemDao {
    private val state = MutableStateFlow<List<CartItemEntity>>(emptyList())

    override fun observeAll(): Flow<List<CartItemEntity>> = state

    override suspend fun insertAll(items: List<CartItemEntity>) {
        state.value = state.value + items
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }

    fun snapshot(): List<CartItemEntity> = state.value

    fun seed(vararg items: CartItemEntity) {
        state.value = items.toList()
    }
}
