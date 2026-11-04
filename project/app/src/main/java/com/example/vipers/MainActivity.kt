/*
 * MainActivity.kt
 * The app's only Activity. It checks whether the current user is an admin and shows the
 * app's navigation (bottom bar and screens).
 * Known issues: the Welcome screen for first launch is added in T-01.
 */
package com.example.vipers

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.vipers.data.seed.SeedLoader
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as CampApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // white clock and battery icons, since every top bar is dark green
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            VipersTheme {
                var currentUserId by remember { mutableStateOf("") }
                var isAdmin by remember { mutableStateOf(false) }

                // The Admin tab only shows for admins (isAdmin is set in the Firebase console).
                LaunchedEffect(Unit) {
                    try {
                        isAdmin = container.deviceIdentityManager.currentProfile()?.isAdmin?: false
                    } catch (e: Exception) {
                        isAdmin = false // offline: just hide the Admin tab
                    }
                }

                AppNavigation(
                    isAdmin = isAdmin,
                    onLoadDemoData = { loadDemoData() },
                )
            }
        }
    }

    private fun loadDemoData() {
        lifecycleScope.launch {
            try {
                SeedLoader(container).seed()
                Toast.makeText(this@MainActivity, "Demo data loaded", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Couldn't load demo data", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
