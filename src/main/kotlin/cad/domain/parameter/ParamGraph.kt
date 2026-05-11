package cad.domain.parameter

/**
 * Топологический пересчёт значений параметров с формулами.
 *
 * Контракт:
 *   - Параметры без формулы — литералы, их значения берутся как есть.
 *   - Параметры с формулой — вычисляются через [Evaluator] от других параметров
 *     в той же мапе.
 *   - Циклы зависимостей → [ParamCycleException], исходная мапа не меняется.
 *   - Невалидная формула (parse/eval error) → значение этого параметра остаётся
 *     прежним, ошибка логируется через [warn] callback.
 */
class ParamCycleException(val cycle: List<String>) :
    RuntimeException("Parameter formula cycle: ${cycle.joinToString(" -> ")}")

object ParamGraph {

    /**
     * Принимает текущую мапу параметров, возвращает мапу с пересчитанными
     * значениями всех формульных параметров.
     */
    fun recompute(
        params: Map<String, Parameter>,
        warn: (paramName: String, msg: String) -> Unit = { _, _ -> },
    ): Map<String, Parameter> {
        // 1. Парсим формулы; невалидные — отмечаем для warn, остаются с прежним value.
        val parsed = HashMap<String, Expr?>(params.size)
        for ((name, p) in params) {
            parsed[name] = try {
                p.parsedFormula()
            } catch (e: ExprParseException) {
                warn(name, "parse: ${e.message}")
                null
            }
        }

        // 2. Граф зависимостей: name -> {ссылается на}
        val deps = HashMap<String, Set<String>>(params.size)
        for ((name, e) in parsed) {
            deps[name] = e?.references()?.filter { it in params }?.toSet().orEmpty()
        }

        // 3. Топологическая сортировка (Kahn). Циклы → исключение.
        val order = topoSort(params.keys, deps)

        // 4. Пересчёт в найденном порядке.
        val out = HashMap<String, Parameter>(params.size).apply { putAll(params) }
        for (name in order) {
            val expr = parsed[name] ?: continue
            val value = try {
                Evaluator.eval(expr) { ref -> out[ref]?.value }
            } catch (e: ExprEvalException) {
                warn(name, "eval: ${e.message}")
                continue
            }
            val cur = out.getValue(name)
            if (cur.value != value) out[name] = cur.copy(value = value)
        }
        return out
    }

    private fun topoSort(nodes: Set<String>, deps: Map<String, Set<String>>): List<String> {
        // Kahn's algorithm. edge dep -> dependent.
        val indeg = HashMap<String, Int>(nodes.size).apply { nodes.forEach { put(it, 0) } }
        val outEdges = HashMap<String, MutableList<String>>(nodes.size).apply {
            nodes.forEach { put(it, ArrayList()) }
        }
        for ((dependent, refs) in deps) {
            for (dep in refs) {
                if (dep == dependent) {
                    throw ParamCycleException(listOf(dependent, dependent))
                }
                outEdges.getValue(dep) += dependent
                indeg[dependent] = (indeg[dependent] ?: 0) + 1
            }
        }
        val queue = ArrayDeque<String>()
        for ((n, d) in indeg) if (d == 0) queue.addLast(n)
        val order = ArrayList<String>(nodes.size)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            order += n
            for (m in outEdges.getValue(n)) {
                val nd = (indeg[m] ?: 0) - 1
                indeg[m] = nd
                if (nd == 0) queue.addLast(m)
            }
        }
        if (order.size != nodes.size) {
            // Есть цикл — соберём цикл из оставшихся узлов через DFS для красивого сообщения.
            val remaining = nodes - order.toSet()
            val cycle = findCycle(remaining, deps) ?: remaining.toList()
            throw ParamCycleException(cycle)
        }
        return order
    }

    private fun findCycle(nodes: Set<String>, deps: Map<String, Set<String>>): List<String>? {
        val state = HashMap<String, Int>() // 0=unvisited, 1=onStack, 2=done
        val stack = ArrayList<String>()
        for (start in nodes) {
            val r = dfs(start, nodes, deps, state, stack)
            if (r != null) return r
        }
        return null
    }

    private fun dfs(
        node: String, scope: Set<String>, deps: Map<String, Set<String>>,
        state: MutableMap<String, Int>, stack: MutableList<String>,
    ): List<String>? {
        if (state[node] == 1) {
            val idx = stack.indexOf(node)
            return if (idx >= 0) stack.subList(idx, stack.size).toList() + node else null
        }
        if (state[node] == 2 || node !in scope) return null
        state[node] = 1
        stack += node
        for (dep in deps[node].orEmpty()) {
            val cyc = dfs(dep, scope, deps, state, stack)
            if (cyc != null) return cyc
        }
        stack.removeAt(stack.size - 1)
        state[node] = 2
        return null
    }
}
