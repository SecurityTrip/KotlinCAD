package cad.domain.parameter

import kotlinx.serialization.Serializable

@Serializable
data class Parameter(
    val name: String,
    val value: Double,
    // TODO(formulas): пока только числовые значения. Когда добавим выражения,
    // здесь будет AST + ссылки на другие параметры через граф зависимостей.
    val formula: String? = null,
) {
    fun withValue(newValue: Double): Parameter = copy(value = newValue)
}
