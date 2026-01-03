package com.example.genmemo.data.api

import com.example.genmemo.data.auth.User
import com.example.genmemo.data.model.OnlinePackage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * API Service for GenMemo web platform.
 * Handles packages, authentication, and progress sync.
 */
object GenMemoApiService {

    private const val BASE_URL = "https://www.gruppogea.net/genmemo"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    // ==================== PACKAGE STORE ====================

    /**
     * Get list of public packages (store).
     */
    suspend fun getPublicPackages(search: String? = null): ApiResult<PackageListResponse> {
        return try {
            val response: PackageListResponse = client.get("$BASE_URL/api/list-packages.php") {
                search?.let { parameter("search", it) }
            }.body()
            ApiResult.Success(response)
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Get user's own packages.
     */
    suspend fun getMyPackages(token: String): ApiResult<PackageListResponse> {
        return try {
            val response: PackageListResponse = client.get("$BASE_URL/api/list-packages.php") {
                parameter("mine", "1")
                parameter("token", token)
            }.body()
            ApiResult.Success(response)
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Download/fetch a package by its UUID code.
     */
    suspend fun downloadPackage(code: String): ApiResult<OnlinePackage> {
        return try {
            val response: OnlinePackage = client.get("$BASE_URL/api/download.php") {
                parameter("id", code)
            }.body()
            ApiResult.Success(response)
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Fetch a package by its UUID code (legacy).
     */
    suspend fun getPackage(code: String): OnlinePackage {
        return client.get("$BASE_URL/api/get-package.php") {
            parameter("code", code)
        }.body()
    }

    suspend fun getPackageSafe(code: String): ApiResult<OnlinePackage> {
        return try {
            ApiResult.Success(getPackage(code))
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    // ==================== AUTHENTICATION ====================

    /**
     * Login with email and password.
     */
    suspend fun login(email: String, password: String): ApiResult<LoginResponse> {
        return try {
            val response: LoginResponse = client.submitForm(
                url = "$BASE_URL/api/auth.php",
                formParameters = parameters {
                    append("action", "login")
                    append("email", email)
                    append("password", password)
                }
            ).body()

            if (response.success == true && response.token != null && response.user != null) {
                ApiResult.Success(response)
            } else {
                ApiResult.Error(response.error ?: "Errore sconosciuto")
            }
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Register a new user.
     */
    suspend fun register(username: String, email: String, password: String): ApiResult<LoginResponse> {
        return try {
            val response: LoginResponse = client.submitForm(
                url = "$BASE_URL/api/auth.php",
                formParameters = parameters {
                    append("action", "register")
                    append("username", username)
                    append("email", email)
                    append("password", password)
                }
            ).body()

            if (response.success == true && response.token != null && response.user != null) {
                ApiResult.Success(response)
            } else {
                ApiResult.Error(response.error ?: "Errore sconosciuto")
            }
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Verify if token is still valid.
     */
    suspend fun verifyToken(token: String): ApiResult<VerifyResponse> {
        return try {
            val response: VerifyResponse = client.submitForm(
                url = "$BASE_URL/api/auth.php",
                formParameters = parameters {
                    append("action", "verify")
                    append("token", token)
                }
            ).body()

            if (response.valid == true) {
                ApiResult.Success(response)
            } else {
                ApiResult.Error(response.error ?: "Token non valido")
            }
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    // ==================== PROGRESS SYNC ====================

    /**
     * Upload question progress to server.
     */
    suspend fun syncQuestionProgress(
        token: String,
        packageUuid: String,
        progress: List<QuestionProgressData>
    ): ApiResult<SyncResponse> {
        return try {
            val progressJson = json.encodeToString(progress)

            val response: SyncResponse = client.submitForm(
                url = "$BASE_URL/api/auth.php",
                formParameters = parameters {
                    append("action", "sync-question-progress")
                    append("token", token)
                    append("package_uuid", packageUuid)
                    append("progress", progressJson)
                }
            ).body()

            if (response.success == true) {
                ApiResult.Success(response)
            } else {
                ApiResult.Error(response.error ?: "Errore sync")
            }
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Download question progress from server.
     */
    suspend fun getQuestionProgress(token: String, packageUuid: String): ApiResult<QuestionProgressResponse> {
        return try {
            val response: QuestionProgressResponse = client.get("$BASE_URL/api/auth.php") {
                parameter("action", "get-question-progress")
                parameter("package_uuid", packageUuid)
                parameter("token", token)
            }.body()

            ApiResult.Success(response)
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Get all question progress for all packages.
     */
    suspend fun getAllQuestionProgress(token: String): ApiResult<AllProgressResponse> {
        return try {
            val response: AllProgressResponse = client.get("$BASE_URL/api/auth.php") {
                parameter("action", "get-all-question-progress")
                parameter("token", token)
            }.body()

            ApiResult.Success(response)
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Save quiz progress (score, attempts, time) to server.
     */
    suspend fun saveProgress(
        token: String,
        packageUuid: String,
        score: Int,
        totalQuestions: Int,
        timeSpent: Int
    ): ApiResult<SyncResponse> {
        return try {
            val response: SyncResponse = client.submitForm(
                url = "$BASE_URL/api/auth.php",
                formParameters = parameters {
                    append("action", "save-progress")
                    append("token", token)
                    append("package_uuid", packageUuid)
                    append("score", score.toString())
                    append("total_questions", totalQuestions.toString())
                    append("time_spent", timeSpent.toString())
                }
            ).body()

            if (response.success == true) {
                ApiResult.Success(response)
            } else {
                ApiResult.Error(response.error ?: "Errore salvataggio progressi")
            }
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    // ==================== STUDY SESSIONS ====================

    /**
     * Start a study session.
     */
    suspend fun startSession(
        token: String,
        packageUuid: String?,
        deviceInfo: String? = null,
        appVersion: String? = null
    ): ApiResult<SessionResponse> {
        return try {
            val response: SessionResponse = client.submitForm(
                url = "$BASE_URL/api/auth.php",
                formParameters = parameters {
                    append("action", "start-session")
                    append("token", token)
                    packageUuid?.let { append("package_uuid", it) }
                    deviceInfo?.let { append("device_info", it) }
                    appVersion?.let { append("app_version", it) }
                }
            ).body()

            if (response.success == true) {
                ApiResult.Success(response)
            } else {
                ApiResult.Error(response.error ?: "Errore avvio sessione")
            }
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * End a study session.
     */
    suspend fun endSession(
        token: String,
        sessionId: Int,
        questionsAnswered: Int,
        correctAnswers: Int
    ): ApiResult<SessionResponse> {
        return try {
            val response: SessionResponse = client.submitForm(
                url = "$BASE_URL/api/auth.php",
                formParameters = parameters {
                    append("action", "end-session")
                    append("token", token)
                    append("session_id", sessionId.toString())
                    append("questions_answered", questionsAnswered.toString())
                    append("correct_answers", correctAnswers.toString())
                }
            ).body()

            if (response.success == true) {
                ApiResult.Success(response)
            } else {
                ApiResult.Error(response.error ?: "Errore chiusura sessione")
            }
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    /**
     * Update session activity (ping).
     */
    suspend fun updateSession(
        token: String,
        sessionId: Int,
        questionsAnswered: Int,
        correctAnswers: Int
    ): ApiResult<SyncResponse> {
        return try {
            val response: SyncResponse = client.submitForm(
                url = "$BASE_URL/api/auth.php",
                formParameters = parameters {
                    append("action", "update-session")
                    append("token", token)
                    append("session_id", sessionId.toString())
                    append("questions_answered", questionsAnswered.toString())
                    append("correct_answers", correctAnswers.toString())
                }
            ).body()

            if (response.success == true) {
                ApiResult.Success(response)
            } else {
                ApiResult.Error(response.error ?: "Errore aggiornamento sessione")
            }
        } catch (e: Exception) {
            ApiResult.Error(parseError(e))
        }
    }

    // ==================== HELPERS ====================

    private fun parseError(e: Exception): String {
        return when {
            e.message?.contains("404") == true -> "Risorsa non trovata"
            e.message?.contains("401") == true -> "Non autorizzato"
            e.message?.contains("timeout", ignoreCase = true) == true -> "Timeout di connessione"
            e.message?.contains("Unable to resolve host") == true -> "Nessuna connessione internet"
            e.message?.contains("Illegal input") == true ||
            e.message?.contains("Expected start of") == true ||
            e.message?.contains("JsonDecodingException") == true -> "Errore nella risposta del server"
            else -> e.message ?: "Errore sconosciuto"
        }
    }

    fun close() {
        client.close()
    }

    // ==================== RESPONSE MODELS ====================

    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String) : ApiResult<Nothing>()
    }
}

// Response DTOs

@Serializable
data class LoginResponse(
    val success: Boolean? = null,
    val token: String? = null,
    val user: User? = null,
    val expires_at: String? = null,
    val error: String? = null
)

@Serializable
data class VerifyResponse(
    val valid: Boolean? = null,
    val user: User? = null,
    val expires_at: String? = null,
    val error: String? = null
)

@Serializable
data class SyncResponse(
    val success: Boolean? = null,
    val synced_count: Int? = null,
    val error: String? = null
)

@Serializable
data class SessionResponse(
    val success: Boolean? = null,
    val session_id: Int? = null,
    val started_at: String? = null,
    val ended_at: String? = null,
    val duration_seconds: Int? = null,
    val error: String? = null
)

@Serializable
data class QuestionProgressData(
    val question_index: Int,
    val score: Int,
    val interval_days: Float,
    val next_review_date: String,
    val streak: Int,
    val correct_days: Int,
    val last_correct_date: String?
)

@Serializable
data class QuestionProgressResponse(
    val package_uuid: String? = null,
    val progress: List<QuestionProgressData> = emptyList(),
    val last_sync: String? = null,
    val error: String? = null
)

@Serializable
data class PackageProgressData(
    val package_uuid: String,
    val package_name: String,
    val questions_count: Int,
    val progress: List<QuestionProgressData>
)

@Serializable
data class AllProgressResponse(
    val packages: List<PackageProgressData> = emptyList(),
    val error: String? = null
)

// Package Store DTOs

@Serializable
data class PackageListResponse(
    val success: Boolean = false,
    val data: PackageListData? = null,
    val error: String? = null
)

@Serializable
data class PackageListData(
    val packages: List<PackageListItem> = emptyList(),
    val pagination: PaginationData? = null
)

@Serializable
data class PaginationData(
    val current_page: Int = 1,
    val total_pages: Int = 1,
    val total_items: Int = 0,
    val items_per_page: Int = 20
)

@Serializable
data class PackageListItem(
    val code: String,
    val name: String,
    val description: String? = null,
    val topic: String? = null,
    val author: String,
    val total_questions: Int = 0,
    val download_count: Int = 0,
    val created_at: String? = null
) {
    // Alias for compatibility
    val uuid: String get() = code
    val questions_count: Int get() = total_questions
}
