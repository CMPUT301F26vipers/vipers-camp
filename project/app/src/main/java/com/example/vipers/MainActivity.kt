/*
 * MainActivity.kt
 * The app's only Activity. It shows Welcome on first launch, or the app's navigation (bottom
 * bar and screens) once this device has a profile.
 * Known issues: none.
 */
package com.example.vipers

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.vipers.data.seed.SeedLoader
import com.example.vipers.ui.navigation.AppRoot
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
                AppRoot(container.deviceIdentityManager, onLoadDemoData = { loadDemoData() })
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
