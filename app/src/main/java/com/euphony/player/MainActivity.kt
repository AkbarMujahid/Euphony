package com.euphony.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.euphony.player.ui.EuphonyRoot
import com.euphony.player.ui.FolderSetupScreen
import com.euphony.player.ui.Screen
import com.euphony.player.ui.UiViewModel
import com.euphony.player.ui.theme.Background
import com.euphony.player.ui.theme.EuphonyTheme
import com.euphony.player.ui.theme.TextPrimary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: UiViewModel by viewModels()

    /** Android's folder picker; the chosen folder is the only storage the app can read. */
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@registerForActivityResult
        if (AppGraph.library.setFolder(tree.toString())) {
            vm.lastScanAt = SystemClock.elapsedRealtime()
            vm.backStack.retainAll { it == Screen.Library }
        } else {
            Toast.makeText(this, "Couldn't get access to that folder. Try another one.", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so system bar icons stay light whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // Needed for the playback notification and its lock-screen controls.
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            handleIntent(intent)
        }
        setContent {
            EuphonyTheme {
                Surface(color = Background, contentColor = TextPrimary) {
                    val folder by AppGraph.library.folder.collectAsStateWithLifecycle()
                    val current = folder
                    if (current == null) {
                        FolderSetupScreen(onChooseFolder = ::chooseFolder)
                    } else {
                        EuphonyRoot(vm, current.name, onChangeFolder = ::chooseFolder, onRescan = { rescan(force = true) })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.player.connect()
        // Pick up songs added to the folder while the app was away; unchanged files come from the cache.
        rescan(force = false)
    }

    override fun onStop() {
        AppGraph.player.disconnect()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Plays an audio file handed over by another app, e.g. a file manager. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        AppGraph.player.playUri(uri.toString())
        vm.open(Screen.NowPlaying)
    }

    private fun chooseFolder() {
        // Start the picker in the current folder, or in the phone's Music folder the first time.
        val start = AppGraph.library.folder.value?.uri?.let(Uri::parse)
            ?: DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:Music")
        folderPicker.launch(start)
    }

    private fun rescan(force: Boolean) {
        if (AppGraph.library.folder.value == null) return
        val now = SystemClock.elapsedRealtime()
        if (!force && vm.lastScanAt != 0L && now - vm.lastScanAt < RESCAN_INTERVAL_MS) return
        vm.lastScanAt = now
        vm.viewModelScope.launch { AppGraph.library.refresh() }
    }

    private companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val RESCAN_INTERVAL_MS = 5 * 60_000L
    }
}
