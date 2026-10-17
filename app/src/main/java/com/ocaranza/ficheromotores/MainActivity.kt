package com.ocaranza.ficheromotores

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ocaranza.ficheromotores.ui.AppTheme
import com.ocaranza.ficheromotores.ui.AppVM
import com.ocaranza.ficheromotores.ui.FicheroApp

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                FicheroApp(vm)
            }
        }
    }
}
