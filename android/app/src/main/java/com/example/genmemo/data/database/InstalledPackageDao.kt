package com.example.genmemo.data.database

import androidx.room.*
import com.example.genmemo.data.model.InstalledPackage
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledPackageDao {

    @Query("SELECT * FROM installed_packages ORDER BY lastPlayedAt DESC, installedAt DESC")
    fun getAllPackages(): Flow<List<InstalledPackage>>

    @Query("SELECT * FROM installed_packages ORDER BY lastPlayedAt DESC, installedAt DESC")
    suspend fun getAllPackagesOnce(): List<InstalledPackage>

    @Query("SELECT * FROM installed_packages WHERE packageId = :packageId")
    suspend fun getPackageById(packageId: String): InstalledPackage?

    @Query("SELECT packageId FROM installed_packages")
    suspend fun getAllInstalledIds(): List<String>

    @Query("SELECT packageId FROM installed_packages")
    fun getAllInstalledIdsFlow(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM installed_packages WHERE packageId = :packageId)")
    suspend fun isInstalled(packageId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM installed_packages WHERE packageId = :packageId)")
    fun isInstalledFlow(packageId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(package_: InstalledPackage)

    @Update
    suspend fun update(package_: InstalledPackage)

    @Query("UPDATE installed_packages SET lastPlayedAt = :timestamp WHERE packageId = :packageId")
    suspend fun updateLastPlayed(packageId: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(package_: InstalledPackage)

    @Query("DELETE FROM installed_packages WHERE packageId = :packageId")
    suspend fun deleteById(packageId: String)

    @Query("SELECT COUNT(*) FROM installed_packages")
    fun getCount(): Flow<Int>
}
