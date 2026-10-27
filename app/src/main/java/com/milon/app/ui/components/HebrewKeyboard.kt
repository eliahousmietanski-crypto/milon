package com.milon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milon.app.ui.theme.HebrewFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection

private val rows = listOf(
    "קראטוןםפ",
    "שדגכעיחלךף",
    "זסבהנמצתץ",
)

/** Voyelles et signes, affichés sur un cercle pointillé. */
private val niqqud = listOf(
    "ַ" to "patah", "ָ" to "qamats", "ֶ" to "segol", "ֵ" to "tsere",
    "ִ" to "hiriq", "ֹ" to "holam", "ֻ" to "qubbuts", "ְ" to "shva",
    "ּ" to "daguesh", "ׁ" to "shin", "ׂ" to "sin", "ֲ" to "hataf patah",
    "ֱ" to "hataf segol", "ֳ" to "hataf qamats", "־" to "maqaf",
)

/** Insère [s] à la position du curseur. */
fun TextFieldValue.insert(s: String): TextFieldValue {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    val t = text.substring(0, start) + s + text.substring(end)
    return TextFieldValue(t, TextRange(start + s.length))
}

/** Efface le caractère avant le curseur (ou la sélection). */
fun TextFieldValue.backspace(): TextFieldValue {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    if (start != end) return TextFieldValue(text.substring(0, start) + text.substring(end), TextRange(start))
    if (start == 0) return this
    return TextFieldValue(text.substring(0, start - 1) + text.substring(start), TextRange(start - 1))
}

/**
 * Clavier hébreu intégré (disposition israélienne standard).
 * [withNiqqud] ajoute une rangée de voyelles, utile pour saisir un mot vocalisé.
 */
@Composable
fun HebrewKeyboard(
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    onSystemKeyboard: (() -> Unit)? = null,
    withNiqqud: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val type: (String) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onText(it)
    }
    var showNiqqud by remember { mutableStateOf(false) }

    // Disposition gauche → droite, comme les touches physiques d'un clavier israélien
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 3.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (withNiqqud && showNiqqud) {
                    niqqud.chunked(8).forEach { chunk ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            chunk.forEach { (mark, _) ->
                                Key(
                                    label = if (mark == "־") "־" else "◌$mark",
                                    onClick = { type(mark) },
                                    tonal = true,
                                )
                            }
                            repeat(8 - chunk.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                rows.forEachIndexed { i, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (i == 2) Box(Modifier.weight(0.5f))
                        row.forEach { c -> Key(label = c.toString(), onClick = { type(c.toString()) }) }
                        if (i == 0) {
                            RepeatKey(icon = Icons.AutoMirrored.Outlined.Backspace, onPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBackspace()
                            }, weight = 2f)
                        }
                        if (i == 2) Box(Modifier.weight(0.5f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconKey(Icons.Outlined.KeyboardHide, onClose, weight = 1.4f)
                    if (onSystemKeyboard != null) IconKey(Icons.Outlined.Language, onSystemKeyboard, weight = 1.4f)
                    if (withNiqqud) {
                        Key(label = "◌ָ", onClick = { showNiqqud = !showNiqqud }, weight = 1.4f, tonal = !showNiqqud, selected = showNiqqud)
                    }
                    Key(label = ",", onClick = { type(",") }, weight = 1f, tonal = true)
                    Key(label = "רווח", onClick = { type(" ") }, weight = 4f, small = true)
                    Key(label = "״", onClick = { type("״") }, weight = 1f, tonal = true)
                    Key(label = "׳", onClick = { type("׳") }, weight = 1f, tonal = true)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    onClick: () -> Unit,
    weight: Float = 1f,
    tonal: Boolean = false,
    small: Boolean = false,
    selected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = when {
            selected -> colors.primaryContainer
            tonal -> colors.surfaceVariant
            else -> colors.surfaceContainerLowest
        },
        shadowElevation = 1.dp,
        modifier = Modifier
            .weight(weight)
            .height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontFamily = HebrewFamily,
                fontSize = if (small) 15.sp else 22.sp,
                fontWeight = FontWeight.Normal,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun RowScope.IconKey(icon: ImageVector, onClick: () -> Unit, weight: Float) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 1.dp,
        modifier = Modifier
            .weight(weight)
            .height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Touche qui se répète tant qu'on appuie (effacement). */
@Composable
private fun RowScope.RepeatKey(icon: ImageVector, onPress: () -> Unit, weight: Float) {
    val currentOnPress by rememberUpdatedState(onPress)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(weight)
            .height(48.dp)
            .background(
                if (pressed) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp),
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    currentOnPress()
                    val job = scope.launch {
                        delay(400)
                        while (true) {
                            currentOnPress()
                            delay(60)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    pressed = false
                }
            },
    ) {
        Icon(icon, contentDescription = "Effacer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
