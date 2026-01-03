package com.example.genmemo

import android.app.Application
import com.example.genmemo.data.database.AppDatabase
import com.example.genmemo.data.repository.InstalledPackageRepository
import com.example.genmemo.data.repository.MemoryRepository

class GenMemoApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }

    val repository by lazy {
        MemoryRepository(
            categoryDao = database.categoryDao(),
            memoryItemDao = database.memoryItemDao()
        )
    }

    val installedPackageRepository by lazy {
        InstalledPackageRepository(
            installedPackageDao = database.installedPackageDao()
        )
    }
}
