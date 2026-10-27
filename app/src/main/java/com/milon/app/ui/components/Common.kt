package com.milon.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milon.app.data.EntrySummary
import com.milon.app.data.Lang
import com.milon.app.data.TextNorm
import com.milon.app.ui.theme.hebrewStyle

@Composable
fun langColors(lang: Lang): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (lang) {
        Lang.BIBLICAL -> c.primaryContainer to c.onPrimaryContainer
        Lang.RABBINIC -> c.tertiaryContainer to c.onTertiaryContainer
        Lang.ARAMAIC -> c.secondaryContainer to c.onSecondaryContainer
    }
}

@Composable
fun LangBadge(lang: Lang, long: Boolean = false, extra: String? = null) {
    val (bg, fg) = langColors(lang)
    Pill(text = (if (long) lang.label else lang.short) + (extra?.let { " · $it" } ?: ""), bg = bg, fg = fg)
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Surface(color = bg, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun UnverifiedMark() {
    Icon(
        Icons.Outlined.ErrorOutline,
        contentDescription = "Non relu",
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(16.dp),
    )
}

/** Ligne de résultat : mot hébreu à droite, traduction en dessous. */
@Composable
fun EntryRow(e: EntrySummary, onClick: () -> Unit, showDivider: Boolean = true) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LangBadge(e.lang)
            e.local?.let {
                Spacer(Modifier.width(6.dp))
                Pill(it.label, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!e.verified && e.local == null) {
                Spacer(Modifier.width(6.dp))
                UnverifiedMark()
            }
            Spacer(Modifier.weight(1f))
            Text(
                e.lemma,
                style = hebrewStyle(26f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            e.gloss,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        val meta = listOfNotNull(
            e.root?.let { "racine ${TextNorm.displayRoot(it)}" },
            e.pos,
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    if (showDivider) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}
