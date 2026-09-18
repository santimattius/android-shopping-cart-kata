package com.pedidosya.kata.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CartItemDao {

    /** Emits the current cached cart rows; re-emits automatically after [replaceAll]. */
    @Query("SELECT * FROM cart_items")
    fun observeAll(): Flow<List<CartItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CartItemEntity>)

    @Query("DELETE FROM cart_items")
    suspend fun deleteAll()

    /** Atomically replaces the cached cart contents with [items]. */
    @Transaction
    suspend fun replaceAll(items: List<CartItemEntity>) {
        deleteAll()
        insertAll(items)
    }
}
