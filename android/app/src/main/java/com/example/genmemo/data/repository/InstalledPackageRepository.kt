package com.example.genmemo.data.repository

import com.example.genmemo.data.database.InstalledPackageDao
import com.example.genmemo.data.model.InstalledPackage
import com.example.genmemo.data.model.OnlinePackage
import com.example.genmemo.data.model.OnlineQuestion
import com.example.genmemo.data.model.PackageSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for managing installed packages.
 * Handles conversion between OnlinePackage and InstalledPackage.
 */
class InstalledPackageRepository(
    private val installedPackageDao: InstalledPackageDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val allPackages: Flow<List<InstalledPackage>> = installedPackageDao.getAllPackages()
    val installedPackageIds: Flow<List<String>> = installedPackageDao.getAllInstalledIdsFlow()
    val packageCount: Flow<Int> = installedPackageDao.getCount()

    suspend fun getAllPackagesOnce(): List<InstalledPackage> {
        return installedPackageDao.getAllPackagesOnce()
    }

    suspend fun getPackageById(packageId: String): InstalledPackage? {
        return installedPackageDao.getPackageById(packageId)
    }

    suspend fun isInstalled(packageId: String): Boolean {
        return installedPackageDao.isInstalled(packageId)
    }

    fun isInstalledFlow(packageId: String): Flow<Boolean> {
        return installedPackageDao.isInstalledFlow(packageId)
    }

    suspend fun getAllInstalledIds(): List<String> {
        return installedPackageDao.getAllInstalledIds()
    }

    /**
     * Install an online package locally.
     * Serializes questions and settings to JSON for storage.
     * @param overrideId Optional ID to use instead of onlinePackage.id (for ensuring store list matching)
     */
    suspend fun installPackage(onlinePackage: OnlinePackage, overrideId: String? = null) {
        val packageId = overrideId ?: onlinePackage.id
        val questionsJson = json.encodeToString(onlinePackage.questions)
        val settingsJson = json.encodeToString(onlinePackage.settings)

        val installedPackage = InstalledPackage(
            packageId = packageId,
            name = onlinePackage.name,
            description = onlinePackage.description,
            author = onlinePackage.author,
            questionsJson = questionsJson,
            settingsJson = settingsJson,
            totalQuestions = onlinePackage.questions.size,
            installedAt = System.currentTimeMillis()
        )

        installedPackageDao.insert(installedPackage)
    }

    /**
     * Uninstall a package by its ID.
     */
    suspend fun uninstallPackage(packageId: String) {
        installedPackageDao.deleteById(packageId)
    }

    /**
     * Update the last played timestamp.
     */
    suspend fun updateLastPlayed(packageId: String) {
        installedPackageDao.updateLastPlayed(packageId)
    }

    /**
     * Convert an InstalledPackage back to OnlinePackage for quiz use.
     */
    fun toOnlinePackage(installedPackage: InstalledPackage): OnlinePackage {
        val questions: List<OnlineQuestion> = json.decodeFromString(installedPackage.questionsJson)
        val settings: PackageSettings = json.decodeFromString(installedPackage.settingsJson)

        return OnlinePackage(
            id = installedPackage.packageId,
            name = installedPackage.name,
            description = installedPackage.description,
            author = installedPackage.author,
            settings = settings,
            questions = questions
        )
    }
}
