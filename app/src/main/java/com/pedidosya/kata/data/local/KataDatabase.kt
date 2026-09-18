package com.pedidosya.kata.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** Cache-only database (dropping and recreating on a version bump is lossless). */
@Database(entities = [CartItemEntity::class], version = 1, exportSchema = true)
abstract class KataDatabase : RoomDatabase() {
    abstract fun cartItemDao(): CartItemDao
}
