package com.secondproject.datasortgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.secondproject.datasortgallery.core.designsystem.theme.DataSortGalleryTheme
import com.secondproject.datasortgallery.core.navigation.NavGraph
import com.secondproject.datasortgallery.core.navigation.Screen
import com.secondproject.datasortgallery.core.permissions.PermissionUtils
import com.secondproject.datasortgallery.feature.onboarding.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isOnboardingCompleted by onboardingViewModel.isOnboardingCompleted.collectAsState()
            val hasPermissions = PermissionUtils.hasMediaPermissions(this)

            val startDestination = when {
                !isOnboardingCompleted -> Screen.Onboarding.route
                !hasPermissions -> Screen.Permissions.route
                else -> Screen.Main.route
            }

            val navController = rememberNavController()

            DataSortGalleryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        onCompleteOnboarding = {
                            onboardingViewModel.completeOnboarding {}
                        }
                    )
                }
            }
        }
    }
}