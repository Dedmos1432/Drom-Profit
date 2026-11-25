package ru.dedmos.dromprofit.presentation.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteKind
import ru.dedmos.dromprofit.presentation.components.EmptyState
import ru.dedmos.dromprofit.presentation.components.formatRubles
import ru.dedmos.dromprofit.presentation.components.Panel
import ru.dedmos.dromprofit.presentation.components.PrimaryAction
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.viewmodels.DetailDialog

internal const val EQUIPMENT_HINT = "Учитывайте одно и то же оснащение одним способом. Выберите преимущество за наличие или расход за отсутствие. Для одинакового оснащения укажите один код."

internal val ExpenseKind.detailTitle: String
    get() = when (this) {
        ExpenseKind.REPAIR -> "Ремонт"
        ExpenseKind.MAINTENANCE -> "Техническое обслуживание"
        ExpenseKind.MISSING_EQUIPMENT -> "Недостающее оснащение"
        ExpenseKind.OTHER -> "Прочие расходы"
    }

internal val NoteKind.detailTitle: String
    get() = when (this) {
        NoteKind.REPORT -> "Сведения из отчёта"
        NoteKind.INSPECTION -> "Результаты осмотра"
        NoteKind.SELLER -> "Со слов продавца"
        NoteKind.ACCEPTED_DEFECT -> "Недостатки без устранения"
        NoteKind.OTHER -> "Прочие заметки"
    }

@Composable
internal fun ExpensesSection(
    expenses: List<Expense>,
    reviewed: Boolean,
    enabled: Boolean,
    onReviewed: (Boolean) -> Unit,
    onDialog: (DetailDialog) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<ExpenseKind?>(null) }
    Panel {
        SectionHeading("Необходимые вложения")
        ReviewedRow(reviewed, enabled, "Я проверил расходы", onReviewed)
        DetailHint(EQUIPMENT_HINT)
    }
    RecordFilters(ExpenseKind.entries, category, { it.detailTitle }, { category = it })
    val visibleExpenses = expenses.filter { category == null || it.kind == category }
    if (visibleExpenses.isEmpty()) {
        EmptyState("Расходы ${if (category == null) "не добавлены" else "в этой категории не добавлены"}", "Запишите необходимые работы и суммы, которые уже известны.")
    }
    visibleExpenses.forEach { expense ->
        Panel {
            Text(expense.title, style = MaterialTheme.typography.titleMedium)
            DetailHint(expense.kind.detailTitle)
            DetailAttribute("Сумма", expense.amount?.let(::formatRubles) ?: "Неизвестна")
            expense.comment?.let { DetailHint(it) }
            expense.factorKey?.let { DetailAttribute("Код оснащения", it) }
            RecordActions(
                enabled = enabled,
                onEdit = { onDialog(DetailDialog.EditExpense(expense)) },
                onDelete = { onDialog(DetailDialog.DeleteExpense(expense)) },
            )
        }
    }
    PrimaryAction(label = "Добавить расход", enabled = enabled) {
        onDialog(DetailDialog.EditExpense(kind = category ?: ExpenseKind.REPAIR))
    }
}

@Composable
internal fun BenefitsSection(
    benefits: List<Benefit>,
    reviewed: Boolean,
    enabled: Boolean,
    onReviewed: (Boolean) -> Unit,
    onDialog: (DetailDialog) -> Unit,
) {
    Panel {
        SectionHeading("Дополнительное оснащение")
        ReviewedRow(reviewed, enabled, "Я проверил преимущества", onReviewed)
        DetailHint(EQUIPMENT_HINT)
    }
    if (benefits.isEmpty()) {
        EmptyState("Преимущества не добавлены", "Укажите оснащение и его ценность лично для вас.")
    }
    benefits.forEach { benefit ->
        Panel {
            Text(benefit.title, style = MaterialTheme.typography.titleMedium)
            DetailAttribute("Персональная ценность", benefit.amount?.let(::formatRubles) ?: "Неизвестна")
            benefit.comment?.let { DetailHint(it) }
            benefit.factorKey?.let { DetailAttribute("Код оснащения", it) }
            RecordActions(
                enabled = enabled,
                onEdit = { onDialog(DetailDialog.EditBenefit(benefit)) },
                onDelete = { onDialog(DetailDialog.DeleteBenefit(benefit)) },
            )
        }
    }
    PrimaryAction(label = "Добавить преимущество", enabled = enabled) {
        onDialog(DetailDialog.EditBenefit())
    }
}

@Composable
internal fun NotesSection(notes: List<Note>, history: Boolean, enabled: Boolean, onDialog: (DetailDialog) -> Unit) {
    var category by rememberSaveable { mutableStateOf<NoteKind?>(null) }
    val categories = NoteKind.entries.filter { it != NoteKind.REPORT }
    val visibleNotes = notes.filter { note ->
        if (history) note.kind == NoteKind.REPORT else note.kind != NoteKind.REPORT && (category == null || note.kind == category)
    }
    if (history) {
        DetailHint("Сведения из отчёта добавляются вручную.")
    } else {
        RecordFilters(categories, category, { it.detailTitle }, { category = it })
    }
    if (visibleNotes.isEmpty()) {
        if (history) {
            EmptyState("Сведения об истории не внесены", "Добавьте важное из имеющегося отчёта.")
        } else {
            EmptyState("Заметки не добавлены", "Запишите результаты осмотра, сведения от продавца и недостатки без устранения.")
        }
    }
    visibleNotes.forEach { note ->
        Panel {
            Text(note.kind.detailTitle, style = MaterialTheme.typography.titleMedium)
            Text(note.text)
            RecordActions(
                enabled = enabled,
                onEdit = { onDialog(DetailDialog.EditNote(note)) },
                onDelete = { onDialog(DetailDialog.DeleteNote(note)) },
            )
        }
    }
    PrimaryAction(label = if (history) "Добавить сведения из отчёта" else "Добавить заметку", enabled = enabled) {
        onDialog(DetailDialog.EditNote(kind = if (history) NoteKind.REPORT else category ?: NoteKind.OTHER))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> RecordFilters(options: List<T>, selected: T?, title: (T) -> String, onSelect: (T?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected == null, onClick = { onSelect(null) }, label = { Text("Все") })
        options.forEach { item ->
            FilterChip(selected == item, onClick = { onSelect(item) }, label = { Text(title(item)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordActions(enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onEdit, enabled = enabled) { Text("Изменить") }
        TextButton(onClick = onDelete, enabled = enabled) { Text("Удалить") }
    }
}

@Preview(name = "Расходы", showBackground = true)
@Composable
private fun ExpensesPreview() {
    DromProfitTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ExpensesSection(
                listOf(Expense("preview", "Замена тормозных колодок", ExpenseKind.REPAIR, null, null, "Уточнить после осмотра")),
                reviewed = false,
                enabled = true,
                onReviewed = {},
                onDialog = {},
            )
        }
    }
}
