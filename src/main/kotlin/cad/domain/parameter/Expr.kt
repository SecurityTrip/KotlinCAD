package cad.domain.parameter

/**
 * AST для пользовательских выражений в [Parameter.formula].
 *
 * Минимальная грамматика (Pratt-парсер):
 *   expr     := term  (('+' | '-') term)*
 *   term     := unary (('*' | '/') unary)*
 *   unary    := '-'? power
 *   power    := atom  ('^' unary)?     // правоассоциативно
 *   atom     := number | identifier | identifier '(' args? ')' | '(' expr ')'
 *   args     := expr (',' expr)*
 *
 * Сейчас identifier — ссылка на параметр в той же фиче. Cross-feature и
 * project-globals — отдельный подэтап Phase 2.
 */
sealed interface Expr {
    data class NumLit(val v: Double) : Expr
    data class Ref(val name: String) : Expr
    data class Bin(val op: BinOp, val a: Expr, val b: Expr) : Expr
    data class Neg(val a: Expr) : Expr
    data class Call(val name: String, val args: List<Expr>) : Expr

    enum class BinOp { ADD, SUB, MUL, DIV, POW }
}

/** Собирает имена параметров, на которые ссылается выражение. */
fun Expr.references(): Set<String> {
    val out = HashSet<String>()
    fun walk(e: Expr) {
        when (e) {
            is Expr.Ref -> out += e.name
            is Expr.Bin -> {
                walk(e.a); walk(e.b)
            }

            is Expr.Neg -> walk(e.a)
            is Expr.Call -> e.args.forEach(::walk)
            is Expr.NumLit -> {}
        }
    }
    walk(this)
    return out
}
