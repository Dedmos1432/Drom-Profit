package ru.dedmos.dromprofit.domain

enum class ExpenseKind { REPAIR, MAINTENANCE, MISSING_EQUIPMENT, OTHER }

enum class NoteKind { REPORT, INSPECTION, SELLER, ACCEPTED_DEFECT, OTHER }

data class Expense(
    val id: String,
    val title: String,
    val kind: ExpenseKind,
    val amount: Long?,
    val factorKey: String? = null,
    val comment: String? = null
)

data class ExpenseInput(
    val title: String,
    val kind: ExpenseKind,
    val amount: Long?,
    val factorKey: String? = null,
    val comment: String? = null
)

data class Benefit(
    val id: String,
    val title: String,
    val amount: Long?,
    val factorKey: String? = null,
    val comment: String? = null
)

data class BenefitInput(
    val title: String,
    val amount: Long?,
    val factorKey: String? = null,
    val comment: String? = null
)

data class Note(val id: String, val kind: NoteKind, val text: String)

data class NoteInput(val kind: NoteKind, val text: String)

internal fun Expense.toInput() = ExpenseInput(title, kind, amount, factorKey, comment)
internal fun Benefit.toInput() = BenefitInput(title, amount, factorKey, comment)
internal fun Note.toInput() = NoteInput(kind, text)
