package ru.dedmos.dromprofit.presentation.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteKind
import ru.dedmos.dromprofit.presentation.components.BadgeTone
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.OutlineAction
import ru.dedmos.dromprofit.presentation.components.ProfitBadge
import ru.dedmos.dromprofit.presentation.components.formatRubles
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.viewmodels.DetailDialog

internal val ExpenseKind.detailTitle: String
    get() = when (this) {
        ExpenseKind.REPAIR -> "Ремонт"
        ExpenseKind.MAINTENANCE -> "Техническое обслуживание"
        ExpenseKind.MISSING_EQUIPMENT -> "Отсутствующее оснащение"
        ExpenseKind.OTHER -> "Другое"
    }

internal val NoteKind.detailTitle: String
    get() = when (this) {
        NoteKind.REPORT -> "Сведения из отчёта"
        NoteKind.INSPECTION -> "Результаты осмотра"
        NoteKind.SELLER -> "Информация от продавца"
        NoteKind.ACCEPTED_DEFECT -> "Недостаток без необходимости устранения"
        NoteKind.OTHER -> "Прочее"
    }

@Composable
internal fun ExpensesSection(
    expenses: List<Expense>,
    knownTotal: Long?,
    reviewed: Boolean,
    enabled: Boolean,
    reviewAvailable: Boolean,
    reviewError: String?,
    onReviewed: (Boolean) -> Unit,
    onDialog: (DetailDialog) -> Unit,
    headingModifier: Modifier = Modifier,
) {
    MoneyFactorsSection(
        expense = true,
        isEmpty = expenses.isEmpty(),
        knownTotal = knownTotal,
        reviewed = reviewed,
        enabled = enabled,
        reviewAvailable = reviewAvailable,
        reviewError = reviewError,
        headingModifier = headingModifier,
        onReviewed = onReviewed,
        onAdd = { onDialog(DetailDialog.EditExpense()) },
    ) {
        expenses.forEachIndexed { index, expense ->
            if (index > 0) HorizontalDivider()
            MoneyRecord(
                title = expense.title,
                category = expense.kind.detailTitle,
                comment = expense.comment,
                amount = expense.amount,
                unknown = "Стоимость неизвестна",
                enabled = enabled,
                onEdit = { onDialog(DetailDialog.EditExpense(expense)) },
                onDelete = { onDialog(DetailDialog.DeleteExpense(expense)) },
            )
        }
    }
}

@Composable
internal fun BenefitsSection(
    benefits: List<Benefit>,
    knownTotal: Long?,
    reviewed: Boolean,
    enabled: Boolean,
    reviewAvailable: Boolean,
    reviewError: String?,
    onReviewed: (Boolean) -> Unit,
    onDialog: (DetailDialog) -> Unit,
    headingModifier: Modifier = Modifier,
) {
    MoneyFactorsSection(
        expense = false,
        isEmpty = benefits.isEmpty(),
        knownTotal = knownTotal,
        reviewed = reviewed,
        enabled = enabled,
        reviewAvailable = reviewAvailable,
        reviewError = reviewError,
        headingModifier = headingModifier,
        onReviewed = onReviewed,
        onAdd = { onDialog(DetailDialog.EditBenefit()) },
    ) {
        benefits.forEachIndexed { index, benefit ->
            if (index > 0) HorizontalDivider()
            MoneyRecord(
                title = benefit.title,
                category = null,
                comment = benefit.comment,
                amount = benefit.amount,
                unknown = "Ценность неизвестна",
                enabled = enabled,
                onEdit = { onDialog(DetailDialog.EditBenefit(benefit)) },
                onDelete = { onDialog(DetailDialog.DeleteBenefit(benefit)) },
            )
        }
    }
}

@Composable
private fun MoneyFactorsSection(
    expense: Boolean,
    isEmpty: Boolean,
    knownTotal: Long?,
    reviewed: Boolean,
    enabled: Boolean,
    reviewAvailable: Boolean,
    reviewError: String?,
    headingModifier: Modifier,
    onReviewed: (Boolean) -> Unit,
    onAdd: () -> Unit,
    records: @Composable ColumnScope.() -> Unit,
) {
    DetailSectionLayout(
        title = if (expense) "Необходимые вложения" else "Дополнительные преимущества",
        addLabel = if (expense) "Добавить расход" else "Добавить преимущество",
        enabled = enabled,
        onAdd = onAdd,
        headingModifier = headingModifier,
    ) {
        val absenceConfirmed = reviewAvailable && reviewed
        DetailHint(
            if (expense) {
                "Запишите, во сколько обойдутся ремонт, обслуживание и недостающее оснащение."
            } else {
                "Укажите, сколько дополнительное оснащение стоит лично для вас. Учитывайте его один раз — как вложение или преимущество."
            },
        )
        if (isEmpty) {
            DetailHint(
                when {
                    expense && absenceConfirmed -> "Необходимых расходов нет."
                    absenceConfirmed -> "Дополнительных преимуществ нет."
                    expense -> "Расходы пока не добавлены."
                    else -> "Преимущества пока не добавлены."
                },
            )
        } else {
            Column(content = records)
        }
        ReviewPanel(
            expense = expense,
            knownTotal = knownTotal,
            isEmpty = isEmpty,
            reviewed = reviewed,
            enabled = enabled,
            reviewAvailable = reviewAvailable,
            reviewError = reviewError,
            onReviewed = onReviewed,
        )
    }
}

@Composable
private fun MoneyRecord(
    title: String,
    category: String?,
    comment: String?,
    amount: Long?,
    unknown: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                category?.let { DetailHint(it) }
                comment?.let { Text(it) }
            }
            Text(
                text = if (amount == null) unknown else formatRubles(amount),
                modifier = Modifier.weight(0.55f),
                color = if (amount == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                fontWeight = if (amount == null) FontWeight.Normal else FontWeight.SemiBold,
            )
        }
        RecordActions(enabled, onEdit, onDelete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewPanel(
    expense: Boolean,
    knownTotal: Long?,
    isEmpty: Boolean,
    reviewed: Boolean,
    enabled: Boolean,
    reviewAvailable: Boolean,
    reviewError: String?,
    onReviewed: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (knownTotal != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (expense) "Учтённые вложения" else "Учтённая ценность преимуществ",
                        Modifier.weight(1f),
                    )
                    Text(
                        text = formatRubles(knownTotal),
                        modifier = Modifier.weight(0.55f),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                    )
                }
            }
            if (reviewAvailable && !reviewed) {
                DetailHint(
                    if (isEmpty) "Если добавлять нечего, отметьте это для оценки."
                    else "Отметьте, что рассмотрели весь список. Неизвестные суммы можно уточнить позже.",
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (!reviewAvailable) {
                    DetailHint("Подтверждение уточняется…")
                } else if (reviewed) {
                    ProfitBadge(
                        text = if (expense) "Расходы рассмотрены" else "Преимущества рассмотрены",
                        tone = BadgeTone.SUCCESS,
                    )
                }
                Button(
                    onClick = { onReviewed(!reviewed) },
                    enabled = enabled && reviewAvailable,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        when {
                            reviewed && expense -> "Пересмотреть расходы"
                            reviewed -> "Пересмотреть преимущества"
                            isEmpty && expense -> "Расходов нет"
                            isEmpty -> "Преимуществ нет"
                            expense -> "Все расходы учтены"
                            else -> "Все преимущества учтены"
                        },
                    )
                }
            }
            reviewError?.let {
                ErrorState("Не удалось изменить подтверждение. $it")
            }
        }
    }
}

@Composable
internal fun NotesSection(
    notes: List<Note>,
    enabled: Boolean,
    onDialog: (DetailDialog) -> Unit,
    headingModifier: Modifier = Modifier,
) {
    DetailSectionLayout(
        title = "Заметки об автомобиле",
        addLabel = "Добавить заметку",
        enabled = enabled,
        onAdd = { onDialog(DetailDialog.EditNote()) },
        headingModifier = headingModifier,
    ) {
        if (notes.isEmpty()) DetailHint("Заметок пока нет.")
        Column {
            notes.forEachIndexed { index, note ->
                if (index > 0) HorizontalDivider()
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(note.kind.detailTitle, fontWeight = FontWeight.SemiBold)
                    Text(note.text)
                    RecordActions(
                        enabled = enabled,
                        onEdit = { onDialog(DetailDialog.EditNote(note)) },
                        onDelete = { onDialog(DetailDialog.DeleteNote(note)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailSectionLayout(
    title: String,
    addLabel: String,
    enabled: Boolean,
    onAdd: () -> Unit,
    headingModifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeading(title, modifier = headingModifier)
            OutlineAction(label = addLabel, enabled = enabled, onClick = onAdd)
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordActions(enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onEdit, enabled = enabled) { Text("Редактировать") }
        TextButton(onClick = onDelete, enabled = enabled) { Text("Удалить") }
    }
}

@Preview(name = "Расходы", showBackground = true, widthDp = 390)
@Composable
private fun ExpensesPreview() {
    DromProfitTheme {
        Column(Modifier.padding(16.dp)) {
            ExpensesSection(
                expenses = listOf(
                    Expense(
                        "preview", "Замена тормозных колодок", ExpenseKind.REPAIR, null,
                        comment = "Уточнить после осмотра",
                    ),
                ),
                knownTotal = 0L,
                reviewed = false,
                enabled = true,
                reviewAvailable = true,
                reviewError = null,
                onReviewed = {},
                onDialog = {},
            )
        }
    }
}
