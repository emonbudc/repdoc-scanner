package com.repdoc.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.repdoc.scanner.ui.RepdocApp
import com.repdoc.scanner.ui.theme.RepdocTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepdocTheme {
                RepdocApp()
            }
        }
    }
}
