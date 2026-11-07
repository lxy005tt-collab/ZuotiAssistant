package `fun`.kirari.hanako.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import `fun`.kirari.hanako.app.navigation.HanakoApp
import `fun`.kirari.hanako.app.navigation.AppViewModel
import `fun`.kirari.hanako.core.ui.theme.HanakoTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("HanakoAI", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        setContent {
            HanakoTheme {
                HanakoApp(viewModel)
            }
        }
    }

}
