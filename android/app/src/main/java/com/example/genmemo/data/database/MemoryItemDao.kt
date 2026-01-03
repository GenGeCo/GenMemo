package com.example.genmemo.data.database

import androidx.room.*
import com.example.genmemo.data.model.ItemType
import com.example.genmemo.data.model.MemoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryItemDao {

    @Query("SELECT * FROM memory_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE type = :type ORDER BY createdAt DESC")
    fun getItemsByType(type: ItemType): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getItemsByCategory(categoryId: Long): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE type = :type AND categoryId = :categoryId ORDER BY createdAt DESC")
    fun getItemsByTypeAndCategory(type: ItemType, categoryId: Long): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE id = :id")
    suspend fun getItemById(id: Long): MemoryItem?

    // Get items due for review (nextReviewDate <= today)
    @Query("SELECT * FROM memory_items WHERE nextReviewDate <= :today ORDER BY score ASC, nextReviewDate ASC")
    suspend fun getItemsDueForReview(today: Long): List<MemoryItem>

    // Get items due for review by type
    @Query("SELECT * FROM memory_items WHERE nextReviewDate <= :today AND type = :type ORDER BY score ASC, nextReviewDate ASC")
    suspend fun getItemsDueForReviewByType(today: Long, type: ItemType): List<MemoryItem>

    // Get items due for review by category
    @Query("SELECT * FROM memory_items WHERE nextReviewDate <= :today AND categoryId = :categoryId ORDER BY score ASC, nextReviewDate ASC")
    suspend fun getItemsDueForReviewByCategory(today: Long, categoryId: Long): List<MemoryItem>

    // Get items due for review by type and category
    @Query("SELECT * FROM memory_items WHERE nextReviewDate <= :today AND type = :type AND categoryId = :categoryId ORDER BY score ASC, nextReviewDate ASC")
    suspend fun getItemsDueForReviewByTypeAndCategory(today: Long, type: ItemType, categoryId: Long): List<MemoryItem>

    // Get all items for review selection (weighted random)
    @Query("SELECT * FROM memory_items ORDER BY score ASC, nextReviewDate ASC")
    suspend fun getAllItemsForReview(): List<MemoryItem>

    @Query("SELECT * FROM memory_items WHERE type = :type ORDER BY score ASC, nextReviewDate ASC")
    suspend fun getAllItemsForReviewByType(type: ItemType): List<MemoryItem>

    @Query("SELECT * FROM memory_items WHERE categoryId = :categoryId ORDER BY score ASC, nextReviewDate ASC")
    suspend fun getAllItemsForReviewByCategory(categoryId: Long): List<MemoryItem>

    @Query("SELECT * FROM memory_items WHERE type = :type AND categoryId = :categoryId ORDER BY score ASC, nextReviewDate ASC")
    suspend fun getAllItemsForReviewByTypeAndCategory(type: ItemType, categoryId: Long): List<MemoryItem>

    // Count items due for review
    @Query("SELECT COUNT(*) FROM memory_items WHERE nextReviewDate <= :today")
    fun countItemsDueForReview(today: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM memory_items")
    fun countAllItems(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MemoryItem): Long

    @Update
    suspend fun update(item: MemoryItem)

    @Delete
    suspend fun delete(item: MemoryItem)

    // Update all items for decay calculation
    @Query("SELECT * FROM memory_items WHERE nextReviewDate < :today")
    suspend fun getOverdueItems(today: Long): List<MemoryItem>

    // Move items from deleted category to null
    @Query("UPDATE memory_items SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategoryFromItems(categoryId: Long)

    // For export: get items by category (not Flow)
    @Query("SELECT * FROM memory_items WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    suspend fun getItemsByCategoryOnce(categoryId: Long): List<MemoryItem>

    // For export: get all items (not Flow)
    @Query("SELECT * FROM memory_items ORDER BY createdAt DESC")
    suspend fun getAllItemsOnce(): List<MemoryItem>
}
