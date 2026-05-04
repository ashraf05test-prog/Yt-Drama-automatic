package com.genspark.videotransform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.genspark.videotransform.ui.VideoTransformScreen
import com.genspark.videotransform.ui.VideoTransformViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: VideoTransformViewModel = viewModel()
            MaterialTheme {
                LaunchedEffect(Unit) { vm.refreshSettings() }
                VideoTransformScreen(vm = vm)
            }
        }
    }
}
