package com.dualframe.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dualframe.camera.navigation.DualFrameNavHost
import com.dualframe.camera.ui.theme.DualFrameTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DualFrameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DualFrameNavHost()
                }
            }
        }
    }
}
