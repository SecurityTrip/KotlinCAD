package cad.domain.parameter

/**
 * Простой Pratt-парсер арифметических выражений с переменными и вызовами функций.
 * Ошибки бросаются как [ExprParseException]; caller — Inspector — ловит и
 * показывает текст ошибки рядом с полем.
 */
class ExprParseException(msg: String, val position: Int) : RuntimeException(msg)

object ExprParser {

    fun parse(source: String): Expr {
        val tokens = Lexer(source).tokenize()
        val parser = PrattParser(tokens)
        val result = parser.parseExpr(0)
        if (!parser.atEnd()) {
            val t = parser.peek()
            throw ExprParseException("Unexpected token '${t.lexeme}'", t.start)
        }
        return result
    }

    // === Tokens ===========================================================

    internal enum class TokType { NUM, IDENT, PLUS, MINUS, STAR, SLASH, CARET, LPAREN, RPAREN, COMMA, EOF }
    internal data class Tok(val type: TokType, val lexeme: String, val start: Int, val value: Double = 0.0)

    private class Lexer(private val s: String) {
        private var i = 0
        fun tokenize(): List<Tok> {
            val out = ArrayList<Tok>()
            while (i < s.length) {
                val c = s[i]
                when {
                    c.isWhitespace() -> i++
                    c.isDigit() || (c == '.' && i + 1 < s.length && s[i + 1].isDigit()) -> out += readNumber()
                    c.isLetter() || c == '_' -> out += readIdent()
                    c == '+' -> {
                        out += Tok(TokType.PLUS, "+", i); i++
                    }

                    c == '-' -> {
                        out += Tok(TokType.MINUS, "-", i); i++
                    }

                    c == '*' -> {
                        out += Tok(TokType.STAR, "*", i); i++
                    }

                    c == '/' -> {
                        out += Tok(TokType.SLASH, "/", i); i++
                    }

                    c == '^' -> {
                        out += Tok(TokType.CARET, "^", i); i++
                    }

                    c == '(' -> {
                        out += Tok(TokType.LPAREN, "(", i); i++
                    }

                    c == ')' -> {
                        out += Tok(TokType.RPAREN, ")", i); i++
                    }

                    c == ',' -> {
                        out += Tok(TokType.COMMA, ",", i); i++
                    }

                    else -> throw ExprParseException("Unexpected character '$c'", i)
                }
            }
            out += Tok(TokType.EOF, "", i)
            return out
        }

        private fun readNumber(): Tok {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            // Опциональная экспонента: 1e3, 1.5e-2
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val lex = s.substring(start, i)
            val v = lex.toDoubleOrNull() ?: throw ExprParseException("Bad number '$lex'", start)
            return Tok(TokType.NUM, lex, start, v)
        }

        private fun readIdent(): Tok {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
            return Tok(TokType.IDENT, s.substring(start, i), start)
        }
    }

    private class PrattParser(private val toks: List<Tok>) {
        private var pos = 0

        fun peek(): Tok = toks[pos]
        fun atEnd(): Boolean = peek().type == TokType.EOF
        private fun advance(): Tok = toks[pos++]
        private fun expect(t: TokType): Tok {
            if (peek().type != t) throw ExprParseException("Expected $t, got '${peek().lexeme}'", peek().start)
            return advance()
        }

        // Pratt: bp = binding power. Возвращаем left-bp.
        private fun lbp(t: TokType): Int = when (t) {
            TokType.PLUS, TokType.MINUS -> 10
            TokType.STAR, TokType.SLASH -> 20
            TokType.CARET -> 30
            else -> 0
        }

        fun parseExpr(minBp: Int): Expr {
            var left = nud()
            while (true) {
                val op = peek().type
                val bp = lbp(op)
                if (bp <= minBp) break
                advance()
                val rightBp = if (op == TokType.CARET) bp - 1 else bp // ^ право-ассоц
                val right = parseExpr(rightBp)
                val bo = when (op) {
                    TokType.PLUS -> Expr.BinOp.ADD
                    TokType.MINUS -> Expr.BinOp.SUB
                    TokType.STAR -> Expr.BinOp.MUL
                    TokType.SLASH -> Expr.BinOp.DIV
                    TokType.CARET -> Expr.BinOp.POW
                    else -> error("unreachable")
                }
                left = Expr.Bin(bo, left, right)
            }
            return left
        }

        private fun nud(): Expr {
            val t = advance()
            return when (t.type) {
                TokType.NUM -> Expr.NumLit(t.value)
                TokType.MINUS -> Expr.Neg(parseExpr(25))
                TokType.PLUS -> parseExpr(25) // унарный плюс — no-op
                TokType.LPAREN -> {
                    val e = parseExpr(0)
                    expect(TokType.RPAREN)
                    e
                }

                TokType.IDENT -> {
                    if (peek().type == TokType.LPAREN) {
                        advance() // '('
                        val args = ArrayList<Expr>()
                        if (peek().type != TokType.RPAREN) {
                            args += parseExpr(0)
                            while (peek().type == TokType.COMMA) {
                                advance()
                                args += parseExpr(0)
                            }
                        }
                        expect(TokType.RPAREN)
                        Expr.Call(t.lexeme, args)
                    } else {
                        Expr.Ref(t.lexeme)
                    }
                }

                else -> throw ExprParseException("Unexpected token '${t.lexeme}'", t.start)
            }
        }
    }
}
