package com.example.genmemo.data.repository

import com.example.genmemo.data.database.CategoryDao
import com.example.genmemo.data.database.MemoryItemDao
import com.example.genmemo.data.model.Category
import com.example.genmemo.data.model.ItemType
import com.example.genmemo.data.model.MemoryItem
import com.example.genmemo.util.SpacedRepetitionEngine
import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val categoryDao: CategoryDao,
    private val memoryItemDao: MemoryItemDao
) {
    // Categories
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun insertCategory(category: Category): Long = categoryDao.insert(category)

    suspend fun updateCategory(category: Category) = categoryDao.update(category)

    suspend fun deleteCategory(category: Category) {
        // Move items to null category before deleting
        memoryItemDao.clearCategoryFromItems(category.id)
        categoryDao.delete(category)
    }

    suspend fun getCategoryById(id: Long): Category? = categoryDao.getCategoryById(id)

    suspend fun getCategoryByName(name: String): Category? = categoryDao.getCategoryByName(name)

    suspend fun ensureDefaultCategory(): Long {
        val existing = categoryDao.getCategoryByName("Generale")
        return existing?.id ?: categoryDao.insert(Category(name = "Generale", color = 0xFF6200EE))
    }

    // Memory Items
    val allItems: Flow<List<MemoryItem>> = memoryItemDao.getAllItems()

    fun getItemsByType(type: ItemType): Flow<List<MemoryItem>> = memoryItemDao.getItemsByType(type)

    fun getItemsByCategory(categoryId: Long): Flow<List<MemoryItem>> =
        memoryItemDao.getItemsByCategory(categoryId)

    suspend fun insertItem(item: MemoryItem): Long = memoryItemDao.insert(item)

    suspend fun updateItem(item: MemoryItem) = memoryItemDao.update(item)

    suspend fun deleteItem(item: MemoryItem) = memoryItemDao.delete(item)

    suspend fun getItemById(id: Long): MemoryItem? = memoryItemDao.getItemById(id)

    // Review statistics
    fun countItemsDueForReview(): Flow<Int> {
        val today = System.currentTimeMillis()
        return memoryItemDao.countItemsDueForReview(today)
    }

    fun countAllItems(): Flow<Int> = memoryItemDao.countAllItems()

    // Get items for review session
    suspend fun getItemsForReview(
        count: Int,
        type: ItemType? = null,
        categoryId: Long? = null
    ): List<MemoryItem> {
        val allItems = when {
            type != null && categoryId != null ->
                memoryItemDao.getAllItemsForReviewByTypeAndCategory(type, categoryId)
            type != null ->
                memoryItemDao.getAllItemsForReviewByType(type)
            categoryId != null ->
                memoryItemDao.getAllItemsForReviewByCategory(categoryId)
            else ->
                memoryItemDao.getAllItemsForReview()
        }

        return SpacedRepetitionEngine.selectItemsForReview(allItems, count)
    }

    // Apply decay to all overdue items (call on app start)
    suspend fun applyDecayToOverdueItems() {
        val today = System.currentTimeMillis()
        val overdueItems = memoryItemDao.getOverdueItems(today)

        for (item in overdueItems) {
            val decayedItem = SpacedRepetitionEngine.applyDecay(item)
            if (decayedItem.score != item.score) {
                memoryItemDao.update(decayedItem)
            }
        }
    }

    // Process answer and update item
    suspend fun processAnswer(item: MemoryItem, isCorrect: Boolean): MemoryItem {
        val updatedItem = if (isCorrect) {
            SpacedRepetitionEngine.processCorrectAnswer(item)
        } else {
            SpacedRepetitionEngine.processWrongAnswer(item)
        }
        memoryItemDao.update(updatedItem)
        return updatedItem
    }

    // Import/Export functions
    suspend fun importCategoryWithItems(category: Category, items: List<MemoryItem>): Int {
        // Check if category exists, or create it
        val existingCategory = categoryDao.getCategoryByName(category.name)
        val categoryId = existingCategory?.id ?: categoryDao.insert(category)

        // Insert all items with the category ID
        var importedCount = 0
        items.forEach { item ->
            val itemWithCategory = item.copy(categoryId = categoryId)
            memoryItemDao.insert(itemWithCategory)
            importedCount++
        }

        return importedCount
    }

    suspend fun getAllCategoriesOnce(): List<Category> = categoryDao.getAllCategoriesOnce()

    suspend fun getItemsByCategoryOnce(categoryId: Long): List<MemoryItem> =
        memoryItemDao.getItemsByCategoryOnce(categoryId)

    suspend fun getAllItemsOnce(): List<MemoryItem> = memoryItemDao.getAllItemsOnce()
}
