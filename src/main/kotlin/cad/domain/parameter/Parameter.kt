package cad.domain.parameter

import kotlinx.serialization.Serializable

@Serializable
data class Parameter(
    val name: String,
    val value: Double,
    /**
     * Если задано — значение параметра вычисляется через [Evaluator] от
     * других параметров (ссылки — простые имена). Парсинг ленив через [parsedFormula].
     */
    val formula: String? = null,
) {
    fun withValue(newValue: Double): Parameter = copy(value = newValue)

    /** Парсит [formula] один раз; кидает [ExprParseException] при синтаксической ошибке. */
    fun parsedFormula(): Expr? = formula?.let { ExprParser.parse(it) }
}
