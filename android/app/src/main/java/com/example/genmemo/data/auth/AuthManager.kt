package com.example.genmemo.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages user authentication state and token storage.
 */
class AuthManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _authState = MutableStateFlow(loadAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isLoggedIn: Boolean
        get() = _authState.value is AuthState.LoggedIn

    val currentUser: User?
        get() = (_authState.value as? AuthState.LoggedIn)?.user

    val token: String?
        get() = (_authState.value as? AuthState.LoggedIn)?.token

    private fun loadAuthState(): AuthState {
        val token = prefs.getString(KEY_TOKEN, null)
        val userJson = prefs.getString(KEY_USER, null)
        val expiresAt = prefs.getString(KEY_EXPIRES_AT, null)

        return if (token != null && userJson != null) {
            try {
                val user = json.decodeFromString<User>(userJson)
                AuthState.LoggedIn(user, token, expiresAt)
            } catch (e: Exception) {
                AuthState.LoggedOut
            }
        } else {
            AuthState.LoggedOut
        }
    }

    fun login(user: User, token: String, expiresAt: String?) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, json.encodeToString(user))
            .putString(KEY_EXPIRES_AT, expiresAt)
            .apply()

        _authState.value = AuthState.LoggedIn(user, token, expiresAt)
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER)
            .remove(KEY_EXPIRES_AT)
            .apply()

        _authState.value = AuthState.LoggedOut
    }

    fun updateUser(user: User) {
        val currentState = _authState.value
        if (currentState is AuthState.LoggedIn) {
            prefs.edit()
                .putString(KEY_USER, json.encodeToString(user))
                .apply()
            _authState.value = currentState.copy(user = user)
        }
    }

    companion object {
        private const val PREFS_NAME = "genmemo_auth"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_EXPIRES_AT = "expires_at"

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

@Serializable
data class User(
    val id: Int,
    val username: String,
    val email: String
)

sealed class AuthState {
    object LoggedOut : AuthState()
    data class LoggedIn(
        val user: User,
        val token: String,
        val expiresAt: String?
    ) : AuthState()
}
