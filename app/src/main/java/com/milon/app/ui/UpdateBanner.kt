package com.milon.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milon.app.LocalApp
import com.milon.app.data.UpdateManager.State
import kotlinx.coroutines.launch

/** Bandeau « mise à jour disponible » affiché en haut de la recherche. */
@Composable
fun UpdateBanner(showUpToDate: Boolean = false) {
    val app = LocalApp.current
    val state by app.updates.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val visible = when (state) {
        is State.Available, is State.Downloading, is State.Done, is State.Error -> true
        State.UpToDate -> showUpToDate
        else -> false
    }
    AnimatedVisibility(visible) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
                when (val s = state) {
                    is State.Available -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                buildString {
                                    if (s.newData) append("Mise à jour du dictionnaire : ${s.manifest.entries} mots.")
                                    if (s.newData && s.newApp) append("\n")
                                    if (s.newApp) append("Nouvelle version de l'application (${s.manifest.appVersionName ?: ""}).")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                            TextButton(onClick = { app.updates.dismiss() }) { Text("Plus tard") }
                            if (s.newApp) {
                                TextButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.manifest.apkUrl)))
                                }) { Text("Télécharger l'appli") }
                            }
                            if (s.newData) {
                                FilledTonalButton(onClick = { scope.launch { app.updates.downloadData(s.manifest) } }) {
                                    Text("Mettre à jour")
                                }
                            }
                        }
                    }
                    is State.Downloading -> {
                        Text("Téléchargement du dictionnaire…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 10.dp, end = 8.dp),
                        )
                    }
                    is State.Done -> BannerMessage("Dictionnaire à jour : ${s.entries} mots.") { app.updates.dismiss() }
                    is State.Error -> BannerMessage(s.message) { app.updates.dismiss() }
                    State.UpToDate -> BannerMessage("Le dictionnaire est déjà à jour.") { app.updates.dismiss() }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun BannerMessage(text: String, onOk: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onOk) { Text("OK") }
    }
}
