package com.meraj.niceexcel

/**
 * Language-neutral conditional-format rule evaluator.
 * Keep JSON field names aligned with backend/niceexcel_api/conditional_formatting.py.
 */
internal data class ConditionalFormatRule(
    val id: String = "",
    val type: String,
    val range: String,
    val operator: String = "greaterThan",
    val value: String? = null,
    val value2: String? = null,
    val text: String = "",
    val fillColor: String? = null,
    val fontColor: String? = null,
    val stopIfTrue: Boolean = false
)

internal object ConditionalFormatting {
    private val rangePattern = Regex("^[A-Z]{1,3}[1-9][0-9]*:[A-Z]{1,3}[1-9][0-9]*$")
    private val operators = setOf("greaterThan", "greaterThanOrEqual", "lessThan", "lessThanOrEqual", "equal", "notEqual", "between", "notBetween")

    fun validate(rule: ConditionalFormatRule): String? {
        if (rule.type !in setOf("cellValue", "textContains", "duplicateValues", "blanks")) return "Unsupported rule type"
        if (!rangePattern.matches(rule.range.uppercase())) return "Rule range must be bounded, e.g. A1:D20"
        if (rule.type == "cellValue" && rule.operator !in operators) return "Unsupported comparison operator"
        if (rule.type == "textContains" && rule.text.isBlank()) return "Text rule requires a value"
        listOf(rule.fillColor, rule.fontColor).filterNotNull().forEach {
            if (!Regex("^#?[0-9A-Fa-f]{6}$").matches(it)) return "Colours must be six-digit hex values"
        }
        return null
    }

    fun matches(rule: ConditionalFormatRule, value: String?, duplicateCount: Int = 0): Boolean {
        when (rule.type) {
            "blanks" -> return value.isNullOrBlank()
            "duplicateValues" -> return duplicateCount > 1
            "textContains" -> return !value.isNullOrEmpty() && value.contains(rule.text, ignoreCase = true)
        }
        val left = value?.toDoubleOrNull() ?: return false
        val right = rule.value?.toDoubleOrNull() ?: return false
        return when (rule.operator) {
            "greaterThan" -> left > right
            "greaterThanOrEqual" -> left >= right
            "lessThan" -> left < right
            "lessThanOrEqual" -> left <= right
            "equal" -> left == right
            "notEqual" -> left != right
            "between" -> rule.value2?.toDoubleOrNull()?.let { left in minOf(right, it)..maxOf(right, it) } ?: false
            "notBetween" -> rule.value2?.toDoubleOrNull()?.let { left !in minOf(right, it)..maxOf(right, it) } ?: false
            else -> false
        }
    }

    fun duplicateCounts(values: List<String?>): Map<String, Int> =
        values.filterNotNull().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
}
