package com.example.genmemo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for locally installed packages from the online store.
 * Stores the full package data as JSON for offline access.
 */
@Entity(tableName = "installed_packages")
data class InstalledPackage(
    @PrimaryKey
    val packageId: String, // Same as OnlinePackage.id / code
    val name: String,
    val description: String,
    val author: String,
    val questionsJson: String, // Serialized List<OnlineQuestion>
    val settingsJson: String, // Serialized PackageSettings
    val totalQuestions: Int,
    val installedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null
)
