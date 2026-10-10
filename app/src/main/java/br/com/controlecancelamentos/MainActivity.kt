package br.com.controlecancelamentos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import br.com.controlecancelamentos.ui.AppViewModel
import br.com.controlecancelamentos.ui.CancelamentosApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CancelamentosApp(viewModel) }
    }
}
