package cad.domain.parameter

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class ExprEvalException(msg: String) : RuntimeException(msg)

object Evaluator {

    private val constants = mapOf("pi" to PI, "e" to kotlin.math.E)

    /**
     * Считает значение [expr] в среде [resolve] (имя параметра → значение).
     * Возвращает null, если имя не разрешено или встретилась неизвестная функция;
     * caller (FeatureTree) трактует это как «формула невалидна → сохранить
     * предыдущее значение».
     */
    fun eval(expr: Expr, resolve: (String) -> Double?): Double = when (expr) {
        is Expr.NumLit -> expr.v
        is Expr.Ref -> resolve(expr.name) ?: constants[expr.name]
        ?: throw ExprEvalException("Unknown identifier '${expr.name}'")

        is Expr.Neg -> -eval(expr.a, resolve)
        is Expr.Bin -> {
            val a = eval(expr.a, resolve)
            val b = eval(expr.b, resolve)
            when (expr.op) {
                Expr.BinOp.ADD -> a + b
                Expr.BinOp.SUB -> a - b
                Expr.BinOp.MUL -> a * b
                Expr.BinOp.DIV -> if (b == 0.0) throw ExprEvalException("Division by zero") else a / b
                Expr.BinOp.POW -> a.pow(b)
            }
        }

        is Expr.Call -> applyFunction(expr.name, expr.args.map { eval(it, resolve) })
    }

    private fun applyFunction(name: String, args: List<Double>): Double {
        fun arity(n: Int) {
            if (args.size != n) throw ExprEvalException("Function '$name' expects $n arg(s), got ${args.size}")
        }
        return when (name) {
            "sin" -> {
                arity(1); sin(args[0])
            }

            "cos" -> {
                arity(1); cos(args[0])
            }

            "tan" -> {
                arity(1); tan(args[0])
            }

            "sqrt" -> {
                arity(1); sqrt(args[0])
            }

            "abs" -> {
                arity(1); abs(args[0])
            }

            "ln" -> {
                arity(1); ln(args[0])
            }

            "exp" -> {
                arity(1); exp(args[0])
            }

            "min" -> {
                if (args.isEmpty()) throw ExprEvalException("min needs args"); args.reduce(::min)
            }

            "max" -> {
                if (args.isEmpty()) throw ExprEvalException("max needs args"); args.reduce(::max)
            }

            else -> throw ExprEvalException("Unknown function '$name'")
        }
    }
}
