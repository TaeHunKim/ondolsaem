package io.github.taehunkim.ondolsaem

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.taehunkim.ondolsaem.ui.AppsScreen
import io.github.taehunkim.ondolsaem.ui.DashboardScreen
import io.github.taehunkim.ondolsaem.ui.HostViewModel
import io.github.taehunkim.ondolsaem.ui.ModelsScreen
import io.github.taehunkim.ondolsaem.ui.SettingsScreen

private val Warm = Color(0xFFC8643B)
private val Spring = Color(0xFF2F7FB6)

class MainActivity : AppCompatActivity() {
    /** Tab requested by the launching intent (the approval list for [OndolSaem.ACTION_APPROVE]). */
    private val requestedTab = androidx.compose.runtime.mutableStateOf<Int?>(null)

    private fun route(intent: android.content.Intent?) {
        if (intent?.action == io.github.taehunkim.ondolsaem.sdk.OndolSaem.ACTION_APPROVE) requestedTab.value = TAB_APPS
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    /** Visible now, so Android allows the keep-alive service if it was refused in the background. */
    override fun onStart() {
        super.onStart()
        (application as OndolSaemApp).container.retryKeepAlive(force = true)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        route(intent)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Warm, secondary = Spring)
            else lightColorScheme(primary = Warm, secondary = Spring)
            MaterialTheme(colorScheme = scheme) {
                val vm: HostViewModel = viewModel()
                val settings = (application as OndolSaemApp).container.settings
                val onboarded by settings.onboardingDone.collectAsState(initial = null)
                if (onboarded == null) return@MaterialTheme
                if (onboarded == false) {
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    io.github.taehunkim.ondolsaem.ui.OnboardingScreen(vm) { scope.launch { settings.setOnboardingDone(true) } }
                    return@MaterialTheme
                }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val wanted by requestedTab
                androidx.compose.runtime.LaunchedEffect(wanted) { wanted?.let { tab = it; requestedTab.value = null } }
                val tabs: List<Pair<Int, ImageVector>> = listOf(
                    R.string.tab_dashboard to Icons.Filled.Memory,
                    R.string.tab_models to Icons.Filled.Storage,
                    R.string.tab_apps to Icons.Filled.Apps,
                    R.string.tab_settings to Icons.Filled.Settings,
                )
                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i, onClick = { tab = i },
                                    icon = { Icon(icon, null) }, label = { Text(stringResource(label)) },
                                    modifier = Modifier.testTag("tab_$i"),
                                )
                            }
                        }
                    },
                ) { pad ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> DashboardScreen(vm)
                            1 -> ModelsScreen(vm)
                            2 -> AppsScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TAB_APPS = 2
    }
}
