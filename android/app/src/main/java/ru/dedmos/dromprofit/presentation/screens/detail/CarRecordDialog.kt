package ru.dedmos.dromprofit.presentation.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.NoteKind
import ru.dedmos.dromprofit.presentation.components.ErrorState
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
                    initialKind = expense?.kind ?: ExpenseKind.REPAIR,
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
            is DetailDialog.DeleteExpense -> ConfirmationDialog(
                "Удалить расход?", dialog.expense.title, busy, error, onDismiss, onDelete,
            )
            is DetailDialog.DeleteBenefit -> ConfirmationDialog(
                "Удалить преимущество?", dialog.benefit.title, busy, error, onDismiss, onDelete,
            )
            is DetailDialog.DeleteNote -> ConfirmationDialog(
                "Удалить заметку?", dialog.note.kind.detailTitle, busy, error, onDismiss, onDelete,
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
    var showEquipment by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val parsedAmount = amount.trim().takeIf { it.all(Char::isDigit) }?.toLongOrNull()
    val invalidName = name.isBlank()
    val invalidAmount = !amountUnknown && parsedAmount == null

    RecordEditorDialog(title, "Сохранить запись", busy, error, onDismiss, onConfirm = {
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
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        kind?.let { currentKind ->
            CategoryMenu("Категория расхода", currentKind, ExpenseKind.entries, { it.detailTitle }, !busy) { kind = it }
        }
        if (!amountUnknown) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(if (kind == null) "Персональная ценность, ₽" else "Стоимость, ₽") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = submitted && invalidAmount,
                supportingText = if (submitted && invalidAmount) {
                    { Text("Укажите целое число рублей или отметьте неизвестную сумму.") }
                } else null,
                enabled = !busy,
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = amountUnknown,
                enabled = !busy,
                role = Role.Checkbox,
                onValueChange = { amountUnknown = it },
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(amountUnknown, onCheckedChange = null, enabled = !busy)
            Text(if (kind == null) "Ценность пока неизвестна" else "Стоимость пока неизвестна")
        }
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Комментарий (необязательно)") },
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { showEquipment = !showEquipment }, enabled = !busy) {
            Text(if (showEquipment) "Свернуть обозначение оснащения" else "Связать одинаковое оснащение")
        }
        if (showEquipment) {
            OutlinedTextField(
                value = factorKey,
                onValueChange = { factorKey = it },
                label = { Text("Обозначение оснащения (необязательно)") },
                supportingText = { Text("Используйте одно и то же обозначение для одинакового оснащения.") },
                enabled = !busy,
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
    var kind by rememberSaveable { mutableStateOf(dialog.note?.kind ?: NoteKind.INSPECTION) }
    var text by rememberSaveable { mutableStateOf(dialog.note?.text.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    RecordEditorDialog(
        title = if (dialog.note != null) "Редактировать заметку" else "Добавить заметку",
        confirmLabel = "Сохранить заметку",
        busy = busy,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            submitted = true
            if (text.isNotBlank()) onSave(NoteInput(kind, text.trim()), dialog.note?.id)
        },
    ) {
        CategoryMenu("Вид заметки", kind, NoteKind.entries, { it.detailTitle }, !busy) { kind = it }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Текст заметки") },
            isError = submitted && text.isBlank(),
            supportingText = if (submitted && text.isBlank()) ({ Text("Введите текст.") }) else null,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
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
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title(selected), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
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
    confirmLabel: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    fields: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                fields()
                error?.let { ErrorState(it) }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !busy, shape = MaterialTheme.shapes.medium) {
                Text(if (busy) "Сохраняем…" else confirmLabel)
            }
        },
        dismissButton = { RecordDialogCancel(busy, onDismiss) },
    )
}

@Composable
private fun ConfirmationDialog(title: String, message: String, busy: Boolean, error: String?, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                enabled = !busy,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(if (busy) "Удаляем…" else "Удалить")
            }
        },
        dismissButton = { RecordDialogCancel(busy, onDismiss) },
    )
}

@Composable
private fun RecordDialogCancel(busy: Boolean, onDismiss: () -> Unit) {
    Button(
        onClick = onDismiss,
        enabled = !busy,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text("Отмена")
    }
}

private fun String.optionalText(): String? = trim().takeIf(String::isNotEmpty)
