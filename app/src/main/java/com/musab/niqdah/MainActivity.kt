package com.musab.niqdah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.musab.niqdah.ui.NiqdahApp
import com.musab.niqdah.ui.theme.NiqdahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NiqdahTheme {
                NiqdahApp()
            }
        }
    }
}
