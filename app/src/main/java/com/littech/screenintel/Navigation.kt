package com.littech.screenintel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.littech.screenintel.ui.cleanup.CleanupScreen
import com.littech.screenintel.ui.main.MainScreen
import com.littech.screenintel.ui.main.MainScreenViewModel
import com.littech.screenintel.ui.viewer.ViewerScreen

@Composable
fun MainNavigation(initialKey: NavKey = Main) {
  val initialStack = if (initialKey != Main) listOf(Main, initialKey) else listOf(Main)
  val backStack = rememberNavBackStack(*initialStack.toTypedArray())
  val mainViewModel: MainScreenViewModel = viewModel(factory = MainScreenViewModel.Factory)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onNavigate = { navKey -> backStack.add(navKey) },
            viewModel = mainViewModel
          )
        }
        entry<Viewer> { key ->
          ViewerScreen(
            screenshotId = key.screenshotId,
            onBack = { backStack.removeLastOrNull() },
            onDeleted = { deletedItem -> mainViewModel.onScreenshotDeleted(deletedItem) }
          )
        }
        entry<Cleanup> {
          CleanupScreen(
            onBack = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}

