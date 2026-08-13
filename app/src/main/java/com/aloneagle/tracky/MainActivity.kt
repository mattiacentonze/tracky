package com.aloneagle.tracky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aloneagle.tracky.ui.navigation.TrackyNavHost
import com.aloneagle.tracky.ui.theme.TrackyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrackyNavHost()
                }
            }
        }
    }
}
