package cad.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

fun main() {
    startKoin {
        modules(appModule)
    }
    application {
        val windowState = rememberWindowState(size = DpSize(1400.dp, 900.dp))
        Window(
            onCloseRequest = {
                stopKoin()
                exitApplication()
            },
            state = windowState,
            title = "pet-CAD",
        ) {
            LaunchedEffect(Unit) {
                // Принудительная подгрузка viewmodel (Koin lazy для синглтонов нам тут не нужен —
                // он создаётся при первом обращении в App()).
                GlobalContext.get().get<AppViewModel>()
            }
            App()
        }
    }
}
