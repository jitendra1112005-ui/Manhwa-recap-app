package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.security.configureAppCheck
import com.example.ui.ManhwaRecapScreen
import com.example.ui.theme.ManhwaRecapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ManhwaRecapTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ManhwaRecapScreen()
                }
            }
        }
    }
}
