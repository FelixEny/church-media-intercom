package com.churchmedia.intercom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.churchmedia.intercom.ui.CommsRoot
import com.churchmedia.intercom.ui.theme.CommsColors
import com.churchmedia.intercom.ui.theme.CommsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val controller = (application as CommsApp).controller

        setContent {
            CommsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CommsColors.Base,
                ) {
                    CommsRoot(controller)
                }
            }
        }
    }
}
