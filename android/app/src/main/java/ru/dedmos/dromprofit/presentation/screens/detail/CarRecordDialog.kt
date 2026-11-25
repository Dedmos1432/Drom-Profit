package ru.dedmos.dromprofit.presentation.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.NoteKind
import ru.dedmos.dromprofit.presentation.viewmodels.DetailDialog

@Composable
internal fun CarRecordDialog(
    dialog: DetailDialog,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSaveExpense: (ExpenseInput, String?) -> Unit,
    onSaveBenefit: (BenefitInput, String?) -> Unit,
    onSaveNote: (NoteInput, String?) -> Unit,
    onDelete: () -> Unit,
) {
    key(dialog) {
        when (dialog) {
            is DetailDialog.EditExpense -> {
                val expense = dialog.expense
                MoneyEditor(
                    title = if (expense == null) "Добавить расход" else "Редактировать расход",
                    initial = MoneyDraft(expense?.title.orEmpty(), expense?.amount, expense?.factorKey, expense?.comment),
                    initialKind = expense?.kind ?: dialog.kind,
                    busy = busy,
                    error = error,
                    onDismiss = onDismiss,
                ) { draft, kind ->
                    if (kind != null) onSaveExpense(ExpenseInput(draft.title, kind, draft.amount, draft.factorKey, draft.comment), expense?.id)
                }
            }
            is DetailDialog.EditBenefit -> {
                val benefit = dialog.benefit
                MoneyEditor(
                    title = if (benefit == null) "Добавить преимущество" else "Редактировать преимущество",
                    initial = MoneyDraft(benefit?.title.orEmpty(), benefit?.amount, benefit?.factorKey, benefit?.comment),
                    initialKind = null,
                    busy = busy,
                    error = error,
                    onDismiss = onDismiss,
                ) { draft, _ ->
                    onSaveBenefit(BenefitInput(draft.title, draft.amount, draft.factorKey, draft.comment), benefit?.id)
                }
            }
            is DetailDialog.EditNote -> NoteEditor(dialog, busy, error, onDismiss, onSaveNote)
            DetailDialog.DeleteCar -> ConfirmationDialog(
                "Удалить автомобиль?", "Карточка, расходы, преимущества и заметки будут удалены.", busy, error, onDismiss, onDelete,
            )
            is DetailDialog.DeleteExpense -> ConfirmationDialog(
                "Удалить расход?", dialog.expense.title, busy, error, onDismiss, onDelete,
            )
            is DetailDialog.DeleteBenefit -> ConfirmationDialog(
                "Удалить преимущество?", dialog.benefit.title, busy, error, onDismiss, onDelete,
            )
            is DetailDialog.DeleteNote -> ConfirmationDialog(
                "Удалить запись?", dialog.note.kind.detailTitle, busy, error, onDismiss, onDelete,
            )
        }
    }
}

private data class MoneyDraft(val title: String, val amount: Long?, val factorKey: String?, val comment: String?)

@Composable
private fun MoneyEditor(
    title: String,
    initial: MoneyDraft,
    initialKind: ExpenseKind?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (MoneyDraft, ExpenseKind?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.title) }
    var amount by rememberSaveable { mutableStateOf(initial.amount?.toString().orEmpty()) }
    var amountUnknown by rememberSaveable { mutableStateOf(initial.amount == null && initial.title.isNotEmpty()) }
    var factorKey by rememberSaveable { mutableStateOf(initial.factorKey.orEmpty()) }
    var comment by rememberSaveable { mutableStateOf(initial.comment.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val parsedAmount = amount.trim().toLongOrNull()?.takeIf { it >= 0 }
    val invalidName = name.isBlank()
    val invalidAmount = !amountUnknown && parsedAmount == null

    RecordEditorDialog(title, busy, error, onDismiss, onConfirm = {
        submitted = true
        if (!invalidName && !invalidAmount) {
            onSave(
                MoneyDraft(name.trim(), if (amountUnknown) null else parsedAmount, factorKey.optionalText(), comment.optionalText()),
                kind,
            )
        }
    }) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Название") },
            enabled = !busy,
            isError = submitted && invalidName,
            supportingText = if (submitted && invalidName) ({ Text("Введите название.") }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        kind?.let { currentKind ->
            CategoryMenu("Категория", currentKind, ExpenseKind.entries, { it.detailTitle }, !busy) { kind = it }
        }
        OutlinedTextField(
            value = if (amountUnknown) "" else amount,
            onValueChange = { amount = it },
            label = { Text(if (kind == null) "Персональная ценность, ₽" else "Сумма, ₽") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = submitted && invalidAmount,
            supportingText = if (submitted && invalidAmount) ({ Text("Укажите целое число от нуля или отметьте неизвестную сумму.") }) else null,
            enabled = !busy && !amountUnknown,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ReviewedRow(amountUnknown, !busy, "Сумма неизвестна") { amountUnknown = it }
        OutlinedTextField(
            value = factorKey,
            onValueChange = { factorKey = it },
            label = { Text("Код оснащения") },
            supportingText = { Text("Необязательно. Например, winter_tires.") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Комментарий") },
            supportingText = { Text("Необязательно") },
            enabled = !busy,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NoteEditor(
    dialog: DetailDialog.EditNote,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (NoteInput, String?) -> Unit,
) {
    val initialKind = dialog.note?.kind ?: dialog.kind
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var text by rememberSaveable { mutableStateOf(dialog.note?.text.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val fromReport = initialKind == NoteKind.REPORT
    RecordEditorDialog(
        title = when {
            dialog.note != null -> "Редактировать запись"
            fromReport -> "Добавить сведения из отчёта"
            else -> "Добавить заметку"
        },
        busy = busy,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            submitted = true
            if (text.isNotBlank()) onSave(NoteInput(kind, text.trim()), dialog.note?.id)
        },
    ) {
        if (!fromReport) {
            CategoryMenu("Категория", kind, NoteKind.entries.filter { it != NoteKind.REPORT }, { it.detailTitle }, !busy) { kind = it }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (fromReport) "Что указано в отчёте" else "Текст заметки") },
            isError = submitted && text.isBlank(),
            supportingText = if (submitted && text.isBlank()) ({ Text("Введите текст.") }) else null,
            enabled = !busy,
            minLines = 3,
            maxLines = 7,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun <T> CategoryMenu(label: String, selected: T, options: List<T>, title: (T) -> String, enabled: Boolean, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        DetailHint(label)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(title(selected))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(title(option)) }, onClick = {
                        onSelect(option)
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun RecordEditorDialog(
    title: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    fields: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                fields()
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Сохраняем…" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } },
    )
}

@Composable
private fun ConfirmationDialog(title: String, message: String, busy: Boolean, error: String?, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = onDelete, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(if (busy) "Удаляем…" else "Удалить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } },
    )
}

private fun String.optionalText(): String? = trim().takeIf(String::isNotEmpty)
