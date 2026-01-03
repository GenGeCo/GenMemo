package com.example.genmemo.util

import com.example.genmemo.data.model.MemoryItem
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object SpacedRepetitionEngine {

    private const val MIN_SCORE = 0
    private const val MAX_SCORE = 100
    private const val MIN_DECAY_SCORE = 30
    private const val DAYS_FOR_MASTERY = 10
    private const val BASE_CORRECT_POINTS = 5
    private const val WRONG_PENALTY = 15
    private const val INTERVAL_MULTIPLIER = 2.5f

    /**
     * Process a correct answer
     */
    fun processCorrectAnswer(item: MemoryItem): MemoryItem {
        val today = getTodayStartMillis()
        val lastCorrectDay = getDayStartMillis(item.lastCorrectDate)

        // Check if this is a different day than last correct
        val isNewDay = today != lastCorrectDay
        val newCorrectDays = if (isNewDay) item.correctDays + 1 else item.correctDays

        // Calculate new score with streak bonus
        val streakBonus = item.streak * 2
        val pointsEarned = BASE_CORRECT_POINTS + streakBonus
        val newScore = min(MAX_SCORE, item.score + pointsEarned)

        // Check if mastered (10 different days with correct answers and score 100)
        val isMastered = newCorrectDays >= DAYS_FOR_MASTERY && newScore >= MAX_SCORE

        // Calculate new interval
        val newInterval = if (isMastered) {
            // Mastered items: very long interval (30+ days)
            max(30f, item.interval * INTERVAL_MULTIPLIER)
        } else {
            min(60f, item.interval * INTERVAL_MULTIPLIER)
        }

        val newNextReviewDate = today + (newInterval * 24 * 60 * 60 * 1000).toLong()

        return item.copy(
            score = newScore,
            interval = newInterval,
            nextReviewDate = newNextReviewDate,
            streak = item.streak + 1,
            correctDays = newCorrectDays,
            lastCorrectDate = today
        )
    }

    /**
     * Process a wrong answer
     */
    fun processWrongAnswer(item: MemoryItem): MemoryItem {
        val today = getTodayStartMillis()
        val tomorrow = today + 24 * 60 * 60 * 1000

        val newScore = max(MIN_SCORE, item.score - WRONG_PENALTY)

        return item.copy(
            score = newScore,
            interval = 1f, // Reset to 1 day
            nextReviewDate = tomorrow,
            streak = 0 // Reset streak
            // correctDays stays the same - we don't punish past achievements
        )
    }

    /**
     * Apply decay to an overdue item
     * Called when the app opens and item wasn't reviewed on time
     */
    fun applyDecay(item: MemoryItem): MemoryItem {
        val today = getTodayStartMillis()
        val daysOverdue = ((today - item.nextReviewDate) / (24 * 60 * 60 * 1000)).toInt()

        if (daysOverdue <= 0) {
            return item // Not overdue, no decay
        }

        // Accelerating decay: more days missed = faster decay
        // decay = daysOverdue * (1 + daysOverdue/10)
        val decayAmount = (daysOverdue * (1 + daysOverdue / 10.0)).toInt()
        val newScore = max(MIN_DECAY_SCORE, item.score - decayAmount)

        return item.copy(score = newScore)
    }

    /**
     * Select items for a review session using weighted random selection
     * Priority:
     * 1. URGENT: nextReviewDate <= today (due for review)
     * 2. WEAK: score < 50
     * 3. REST: weighted random based on score
     */
    fun selectItemsForReview(
        allItems: List<MemoryItem>,
        count: Int
    ): List<MemoryItem> {
        if (allItems.isEmpty()) return emptyList()
        if (allItems.size <= count) return allItems.shuffled()

        val today = getTodayStartMillis()
        val selected = mutableListOf<MemoryItem>()

        // Priority 1: Urgent items (due today or overdue)
        val urgentItems = allItems.filter { it.nextReviewDate <= today }
            .sortedBy { it.score } // Worst first
            .toMutableList()

        while (selected.size < count && urgentItems.isNotEmpty()) {
            selected.add(urgentItems.removeAt(0))
        }

        if (selected.size >= count) {
            return selected.shuffled()
        }

        // Priority 2: Weak items (score < 50)
        val weakItems = allItems.filter { it !in selected && it.score < 50 }
            .sortedBy { it.score }
            .toMutableList()

        while (selected.size < count && weakItems.isNotEmpty()) {
            selected.add(weakItems.removeAt(0))
        }

        if (selected.size >= count) {
            return selected.shuffled()
        }

        // Priority 3: Rest - weighted random (lower score = higher chance)
        val remainingItems = allItems.filter { it !in selected }.toMutableList()

        while (selected.size < count && remainingItems.isNotEmpty()) {
            val item = selectWeightedRandom(remainingItems)
            selected.add(item)
            remainingItems.remove(item)
        }

        return selected.shuffled()
    }

    /**
     * Weighted random selection - items with lower scores have higher probability
     */
    private fun selectWeightedRandom(items: List<MemoryItem>): MemoryItem {
        if (items.size == 1) return items[0]

        // Weight = 100 - score + 10 (so even score 100 has some chance)
        val weights = items.map { (100 - it.score + 10).toDouble() }
        val totalWeight = weights.sum()

        var random = Random.nextDouble() * totalWeight
        for (i in items.indices) {
            random -= weights[i]
            if (random <= 0) {
                return items[i]
            }
        }

        return items.last()
    }

    /**
     * Check if answer is similar enough (case insensitive, trimmed, some typo tolerance)
     */
    fun checkAnswer(userAnswer: String, correctAnswer: String): Boolean {
        val normalizedUser = userAnswer.trim().lowercase()
        val normalizedCorrect = correctAnswer.trim().lowercase()

        // Exact match (case insensitive)
        if (normalizedUser == normalizedCorrect) return true

        // Allow small typos for longer answers (Levenshtein distance)
        if (normalizedCorrect.length >= 4) {
            val maxDistance = when {
                normalizedCorrect.length >= 10 -> 2
                normalizedCorrect.length >= 6 -> 1
                else -> 0
            }
            if (levenshteinDistance(normalizedUser, normalizedCorrect) <= maxDistance) {
                return true
            }
        }

        return false
    }

    /**
     * Levenshtein distance for typo tolerance
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[m][n]
    }

    /**
     * Get start of today in milliseconds
     */
    private fun getTodayStartMillis(): Long {
        val now = System.currentTimeMillis()
        return getDayStartMillis(now)
    }

    /**
     * Get start of a day in milliseconds
     */
    private fun getDayStartMillis(timestamp: Long): Long {
        return (timestamp / (24 * 60 * 60 * 1000)) * (24 * 60 * 60 * 1000)
    }

    /**
     * Get statistics for display
     */
    fun getItemStats(item: MemoryItem): ItemStats {
        val today = getTodayStartMillis()
        val isDue = item.nextReviewDate <= today
        val daysUntilReview = if (isDue) 0 else
            ((item.nextReviewDate - today) / (24 * 60 * 60 * 1000)).toInt()

        return ItemStats(
            score = item.score,
            streak = item.streak,
            correctDays = item.correctDays,
            daysUntilReview = daysUntilReview,
            isDue = isDue,
            isMastered = item.score >= 100 && item.correctDays >= DAYS_FOR_MASTERY
        )
    }

    data class ItemStats(
        val score: Int,
        val streak: Int,
        val correctDays: Int,
        val daysUntilReview: Int,
        val isDue: Boolean,
        val isMastered: Boolean
    )
}
