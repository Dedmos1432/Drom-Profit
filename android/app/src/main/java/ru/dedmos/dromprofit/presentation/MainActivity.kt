package ru.dedmos.dromprofit.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.dedmos.dromprofit.presentation.navigation.ProfitApp
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DromProfitTheme { ProfitApp() }
        }
    }
}
