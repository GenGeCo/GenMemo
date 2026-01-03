package com.example.genmemo.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.genmemo.data.api.GenMemoApiService
import com.example.genmemo.data.api.QuestionProgressData
import com.example.genmemo.data.auth.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Service for syncing question progress with the server.
 * Manages local storage of progress and synchronization with the backend.
 */
class ProgressSyncService(context: Context, private val authManager: AuthManager) {

    companion object {
        private const val PREFS_NAME = "genmemo_progress"
        private const val KEY_PROGRESS_PREFIX = "progress_"
        private const val KEY_PENDING_SYNC_PREFIX = "pending_sync_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Get progress for a specific question in a package.
     */
    fun getQuestionProgress(packageUuid: String, questionIndex: Int): LocalQuestionProgress {
        val allProgress = getPackageProgress(packageUuid)
        return allProgress[questionIndex] ?: LocalQuestionProgress.default()
    }

    /**
     * Get all progress for a package.
     */
    fun getPackageProgress(packageUuid: String): Map<Int, LocalQuestionProgress> {
        val key = KEY_PROGRESS_PREFIX + packageUuid
        val jsonString = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val list: List<LocalQuestionProgress> = json.decodeFromString(jsonString)
            list.associateBy { it.questionIndex }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Update progress for a question after answering.
     */
    fun updateQuestionProgress(
        packageUuid: String,
        questionIndex: Int,
        wasCorrect: Boolean
    ): LocalQuestionProgress {
        val currentProgress = getQuestionProgress(packageUuid, questionIndex)
        val today = LocalDate.now()
        val todayString = today.format(dateFormatter)

        val newProgress = if (wasCorrect) {
            // Increase score and interval
            val newScore = minOf(currentProgress.score + 1, 5)
            val newStreak = currentProgress.streak + 1
            val newInterval = calculateNextInterval(newScore, currentProgress.intervalDays)
            val nextReviewDate = today.plusDays(newInterval.toLong())

            // Check if this is a new correct day
            val isNewCorrectDay = currentProgress.lastCorrectDate != todayString
            val newCorrectDays = if (isNewCorrectDay) currentProgress.correctDays + 1 else currentProgress.correctDays

            currentProgress.copy(
                score = newScore,
                intervalDays = newInterval,
                nextReviewDate = nextReviewDate.format(dateFormatter),
                streak = newStreak,
                correctDays = newCorrectDays,
                lastCorrectDate = todayString
            )
        } else {
            // Reset or decrease score
            val newScore = maxOf(currentProgress.score - 1, 0)
            val newInterval = 1f // Reset to 1 day
            val nextReviewDate = today.plusDays(1)

            currentProgress.copy(
                score = newScore,
                intervalDays = newInterval,
                nextReviewDate = nextReviewDate.format(dateFormatter),
                streak = 0 // Reset streak on wrong answer
            )
        }

        saveQuestionProgress(packageUuid, questionIndex, newProgress)
        markForSync(packageUuid, questionIndex)

        return newProgress
    }

    /**
     * Calculate next review interval based on score.
     */
    private fun calculateNextInterval(score: Int, currentInterval: Float): Float {
        return when (score) {
            0 -> 1f
            1 -> 1f
            2 -> 3f
            3 -> 7f
            4 -> 14f
            5 -> 30f
            else -> currentInterval * 2f
        }
    }

    /**
     * Save progress for a single question.
     */
    private fun saveQuestionProgress(packageUuid: String, questionIndex: Int, progress: LocalQuestionProgress) {
        val allProgress = getPackageProgress(packageUuid).toMutableMap()
        allProgress[questionIndex] = progress.copy(questionIndex = questionIndex)

        val key = KEY_PROGRESS_PREFIX + packageUuid
        val list = allProgress.values.toList()
        prefs.edit().putString(key, json.encodeToString(list)).apply()
    }

    /**
     * Mark a question as needing sync with server.
     */
    private fun markForSync(packageUuid: String, questionIndex: Int) {
        val key = KEY_PENDING_SYNC_PREFIX + packageUuid
        val pendingSet = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        pendingSet.add(questionIndex.toString())
        prefs.edit().putStringSet(key, pendingSet).apply()
    }

    /**
     * Get questions that need syncing for a package.
     */
    private fun getPendingSyncQuestions(packageUuid: String): Set<Int> {
        val key = KEY_PENDING_SYNC_PREFIX + packageUuid
        return prefs.getStringSet(key, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    /**
     * Clear pending sync markers for a package.
     */
    private fun clearPendingSync(packageUuid: String) {
        val key = KEY_PENDING_SYNC_PREFIX + packageUuid
        prefs.edit().remove(key).apply()
    }

    /**
     * Sync progress with server for a specific package.
     */
    suspend fun syncPackageProgress(packageUuid: String): SyncResult {
        val token = authManager.token ?: return SyncResult.NotLoggedIn

        _syncState.value = SyncState.Syncing

        // Get pending questions
        val pendingQuestions = getPendingSyncQuestions(packageUuid)
        if (pendingQuestions.isEmpty()) {
            _syncState.value = SyncState.Idle
            return SyncResult.NothingToSync
        }

        // Get progress data for pending questions
        val allProgress = getPackageProgress(packageUuid)
        val progressToSync = pendingQuestions.mapNotNull { index ->
            allProgress[index]?.toApiData()
        }

        if (progressToSync.isEmpty()) {
            _syncState.value = SyncState.Idle
            return SyncResult.NothingToSync
        }

        // Send to server
        return when (val result = GenMemoApiService.syncQuestionProgress(token, packageUuid, progressToSync)) {
            is GenMemoApiService.ApiResult.Success -> {
                clearPendingSync(packageUuid)
                _syncState.value = SyncState.Idle
                SyncResult.Success(result.data.synced_count ?: 0)
            }
            is GenMemoApiService.ApiResult.Error -> {
                _syncState.value = SyncState.Error(result.message)
                SyncResult.Error(result.message)
            }
        }
    }

    /**
     * Download progress from server for a specific package.
     */
    suspend fun downloadPackageProgress(packageUuid: String): SyncResult {
        val token = authManager.token ?: return SyncResult.NotLoggedIn

        _syncState.value = SyncState.Syncing

        return when (val result = GenMemoApiService.getQuestionProgress(token, packageUuid)) {
            is GenMemoApiService.ApiResult.Success -> {
                val serverProgress = result.data.progress
                // Merge server progress with local (server wins for conflicts)
                val localProgress = getPackageProgress(packageUuid).toMutableMap()

                for (serverItem in serverProgress) {
                    localProgress[serverItem.question_index] = LocalQuestionProgress(
                        questionIndex = serverItem.question_index,
                        score = serverItem.score,
                        intervalDays = serverItem.interval_days,
                        nextReviewDate = serverItem.next_review_date,
                        streak = serverItem.streak,
                        correctDays = serverItem.correct_days,
                        lastCorrectDate = serverItem.last_correct_date
                    )
                }

                // Save merged progress
                val key = KEY_PROGRESS_PREFIX + packageUuid
                prefs.edit().putString(key, json.encodeToString(localProgress.values.toList())).apply()

                _syncState.value = SyncState.Idle
                SyncResult.Success(serverProgress.size)
            }
            is GenMemoApiService.ApiResult.Error -> {
                _syncState.value = SyncState.Error(result.message)
                SyncResult.Error(result.message)
            }
        }
    }

    /**
     * Get questions due for review in a package.
     */
    fun getQuestionsDueForReview(packageUuid: String, totalQuestions: Int): List<Int> {
        val today = LocalDate.now()
        val progress = getPackageProgress(packageUuid)

        return (0 until totalQuestions).filter { index ->
            val questionProgress = progress[index]
            if (questionProgress == null) {
                true // Never reviewed, so it's due
            } else {
                try {
                    val nextReview = LocalDate.parse(questionProgress.nextReviewDate, dateFormatter)
                    !nextReview.isAfter(today)
                } catch (e: Exception) {
                    true
                }
            }
        }
    }

    /**
     * Get count of questions due for review.
     */
    fun countQuestionsDue(packageUuid: String, totalQuestions: Int): Int {
        return getQuestionsDueForReview(packageUuid, totalQuestions).size
    }

    /**
     * Check if a specific question is due for review.
     */
    fun isQuestionDue(packageUuid: String, questionIndex: Int): Boolean {
        val progress = getQuestionProgress(packageUuid, questionIndex)
        val today = LocalDate.now()

        return try {
            val nextReview = LocalDate.parse(progress.nextReviewDate, dateFormatter)
            !nextReview.isAfter(today)
        } catch (e: Exception) {
            true // If parsing fails, consider it due
        }
    }
}

/**
 * Local storage model for question progress.
 */
@Serializable
data class LocalQuestionProgress(
    val questionIndex: Int = 0,
    val score: Int = 0,
    val intervalDays: Float = 1f,
    val nextReviewDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val streak: Int = 0,
    val correctDays: Int = 0,
    val lastCorrectDate: String? = null
) {
    companion object {
        fun default() = LocalQuestionProgress()
    }

    fun toApiData(): QuestionProgressData {
        return QuestionProgressData(
            question_index = questionIndex,
            score = score,
            interval_days = intervalDays,
            next_review_date = nextReviewDate,
            streak = streak,
            correct_days = correctDays,
            last_correct_date = lastCorrectDate
        )
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Error(val message: String) : SyncState()
}

sealed class SyncResult {
    data class Success(val count: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
    object NotLoggedIn : SyncResult()
    object NothingToSync : SyncResult()
}
