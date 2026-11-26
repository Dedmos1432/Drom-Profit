package ru.dedmos.dromprofit.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.dedmos.dromprofit.presentation.ui.theme.ActionBackground
import ru.dedmos.dromprofit.presentation.ui.theme.ActionText
import ru.dedmos.dromprofit.presentation.ui.theme.BrandRed
import ru.dedmos.dromprofit.presentation.ui.theme.LinkBlue

internal enum class ProfitDestination(val label: String) {
    CARS("Моя выборка"),
    ADD("Добавить авто"),
    COMPARE("Сравнение");

    companion object {
        fun fromTitle(title: String): ProfitDestination? = when {
            title == "Моя выборка" -> CARS
            title.startsWith("Добавить") -> ADD
            title.startsWith("Сравнение") -> COMPARE
            else -> null
        }
    }
}

internal data class ProfitNavigation(
    val current: ProfitDestination?,
    val onNavigate: (ProfitDestination) -> Unit,
)

internal val LocalProfitNavigation = staticCompositionLocalOf<ProfitNavigation?> { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfitSiteHeader(
    navigation: ProfitNavigation?,
    previewDestination: ProfitDestination?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = BrandRed)) { append("Дром") }
                        append(" Профит")
                    },
                    modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight()
                        .clickable(enabled = navigation != null || onBack != null) {
                            navigation?.onNavigate?.invoke(ProfitDestination.CARS) ?: onBack?.invoke()
                        },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                actions()
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (destination in ProfitDestination.entries) {
                    val selected = (navigation?.current ?: previewDestination) == destination
                    Surface(
                        color = if (selected) ActionBackground else Color.Transparent,
                        contentColor = if (selected) ActionText else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            destination.label,
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = selected,
                                    enabled = navigation != null,
                                    role = Role.Tab,
                                    onClick = { navigation?.onNavigate?.invoke(destination) },
                                )
                                .heightIn(min = 44.dp).wrapContentHeight().padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun ProfitBreadcrumb(current: String, onHome: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Моя выборка",
            modifier = Modifier.clickable(role = Role.Button, onClick = onHome).heightIn(min = 36.dp).wrapContentHeight(),
            color = LinkBlue,
            textDecoration = TextDecoration.Underline,
            style = MaterialTheme.typography.bodySmall,
        )
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(current, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall)
    }
}
