package io.github.taehunkim.ondolsaem

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Visible now, so Android allows the keep-alive service if it was refused in the background. */
    override fun onStart() {
        super.onStart()
        (application as OndolSaemApp).container.retryKeepAlive(force = true)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Warm, secondary = Spring)
            else lightColorScheme(primary = Warm, secondary = Spring)
            MaterialTheme(colorScheme = scheme) {
                val vm: HostViewModel = viewModel()
                var tab by rememberSaveable { mutableIntStateOf(0) }
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
}
