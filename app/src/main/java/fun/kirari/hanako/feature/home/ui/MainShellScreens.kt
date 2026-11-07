package `fun`.kirari.hanako.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    Hanako("首页", Icons.Default.Home),
    Settings("设置", Icons.Default.Settings)
}

@Composable
internal fun MainShellScreen(
    pagerState: PagerState,
    hanakoContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        // There are only two main pages. Keep the neighbor composed so switching
        // does not recreate the list and its Shizuku listeners during the animation.
        beyondViewportPageCount = 1,
        key = { Screen.entries[it].name },
        userScrollEnabled = true
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (Screen.entries[page]) {
                Screen.Hanako -> hanakoContent()
                Screen.Settings -> settingsContent()
            }
        }
    }
}
