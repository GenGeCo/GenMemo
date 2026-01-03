package com.example.genmemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.genmemo.data.auth.AuthManager
import com.example.genmemo.data.sync.ProgressSyncService
import com.example.genmemo.ui.navigation.NavGraph
import com.example.genmemo.ui.theme.GenMemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GenMemoApplication
        val repository = app.repository
        val installedPackageRepository = app.installedPackageRepository

        setContent {
            GenMemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authManager = remember { AuthManager(this) }
                    val progressSyncService = remember { ProgressSyncService(this, authManager) }

                    NavGraph(
                        navController = navController,
                        repository = repository,
                        installedPackageRepository = installedPackageRepository,
                        authManager = authManager,
                        progressSyncService = progressSyncService
                    )
                }
            }
        }
    }
}
