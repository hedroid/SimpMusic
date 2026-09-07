package com.maxrave.simpmusic.expect.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun filePickerResult(
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                onResultUri(uri.toString())
            }
        }
    return object : FilePickerLauncher {
        override fun launch() {
            launcher.launch(arrayOf(mimeType))
        }
    }
}

@Composable
actual fun fileSaverResult(
    fileName: String,
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
            if (uri != null) {
                onResultUri(uri.toString())
            }
        }
    return object : FilePickerLauncher {
        override fun launch() {
            launcher.launch(fileName)
        }
    }
}
@Composable
actual fun directoryPickerResult(onResultUri: (String?) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Keep the grant across reboots so scheduled backups can keep writing here.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                onResultUri(uri.toString())
            }
        }
    return object : FilePickerLauncher {
        override fun launch() {
            launcher.launch(null)
        }
    }
}
