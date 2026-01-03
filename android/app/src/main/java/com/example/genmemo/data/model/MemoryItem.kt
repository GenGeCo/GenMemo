package com.example.genmemo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ItemType {
    IMAGE,
    QUESTION
}

@Entity(
    tableName = "memory_items",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId")]
)
data class MemoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: ItemType,
    val question: String, // Image path or question text
    val answer: String,   // Correct answer
    val categoryId: Long?,
    val prompt: String = "Chi/Cosa è?", // Custom prompt for images (e.g., "Come si chiama?")

    // Spaced Repetition fields
    val score: Int = 0,           // 0-100, how well you know it
    val interval: Float = 1f,     // Days between reviews
    val nextReviewDate: Long = System.currentTimeMillis(), // When to review next
    val streak: Int = 0,          // Consecutive correct answers
    val correctDays: Int = 0,     // Different days with correct answers (need 10 for mastery)
    val lastCorrectDate: Long = 0, // Last date answered correctly
    val createdAt: Long = System.currentTimeMillis()
)
