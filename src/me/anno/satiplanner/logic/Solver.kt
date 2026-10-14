package me.anno.satiplanner.logic

import me.anno.satiplanner.nodes.Connector
import me.anno.satiplanner.nodes.Factory
import me.anno.satiplanner.nodes.Node
import me.anno.satiplanner.types.WasteGroup
import me.anno.utils.Clock
import org.apache.logging.log4j.LogManager
import org.joml.Vector2i
import kotlin.concurrent.thread
import kotlin.math.min

object Solver {

    private val LOGGER = LogManager.getLogger(Solver::class)

    fun optimizeProduction(nodes: List<Node>): Boolean {
        val factories = nodes.filterIsInstance<Factory>()
        if (factories.isEmpty()) return true

        prepareFactories(factories)

        val sb = StringBuilder()
        defineProduction(sb, factories)
        val wasteGroupsByInput = defineInputDependencies(sb, factories)
        val numWasteGroups = defineWasteGroups(sb, wasteGroupsByInput)
        defineTargets(sb, factories, numWasteGroups)

        return executeGLPKScript(sb, factories)
    }

    private fun executeGLPKScript(sb: StringBuilder, factories: List<Factory>): Boolean {
        val recipe = sb.toString()
        val clock = Clock(LOGGER)
        val process = ProcessBuilder("glpsol", "-m", "/dev/stdin").start()
        process.outputStream.write(recipe.toByteArray())
        process.outputStream.close()

        thread(name = "glpsol-err") {
            process.errorStream.bufferedReader().forEachLine { line ->
                LOGGER.error(line)
            }
        }

        process.inputStream.bufferedReader().forEachLine { line ->
            parseResult(line, factories)
            if (!line.startsWith("Generating") && ".val = " !in line) {
                LOGGER.info(line)
            }
        }
        val status = process.waitFor()
        clock.stop("Solving")
        return status == 0
    }

    private fun parseResult(line: String, factories: List<Factory>) {
        val mid = ".val = "
        val i = line.indexOf(mid)
        if (i >= 0) {
            if (line.startsWith('f')) {
                val factory = factories[line.substring(1, i).toInt()]
                val number = line.substring(i + mid.length)
                val numberF = number.toDouble()
                factory.calculated = numberF
                factory.calculatedStr = if (numberF > 1e9) "Inf" else number + "x"
            }
        }
    }

    private fun prepareFactories(factories: List<Factory>) {
        for (i in factories.indices) {
            val factory = factories[i]
            factory.calculatedStr = "?"
            factory.index = i
        }
    }

    private fun defineInputDependencies(sb: StringBuilder, factories: List<Factory>): Map<Vector2i, WasteGroup> {
        val wasteGroupsByInput = HashMap<Vector2i, WasteGroup>()

        // define input dependencies
        for (f in factories.indices) {
            val factory = factories[f]
            val type = factory.type

            // we must handle inputs and outputs for asymmetric layouts
            // we consume less than is being produced on the LHS
            for (i in 0 until min(factory.inputs.size, type.inputs.size)) {
                val inputs = factory.inputs[i]
                if (inputs.isEmpty()) continue

                val allInputs = collectInputs(inputs)
                if (allInputs.isEmpty()) continue

                val inputType = type.inputs[i]
                val inputTypeT = inputType.type
                sb.append("s.t. F${f}In${i}LTOut: ").production(f)
                    .append(" * ").append(factory.inputMultiplier * inputType.amount)
                    .append(" <= ")

                val wasteGroup = wasteGroupsByInput.getOrPut(Vector2i(f, i)) {
                    WasteGroup(inputTypeT)
                }

                if (wasteGroup.addWasteInput(factory)) {
                    for (output in wasteGroup.inputs) {
                        val oi = output.findInputIndex(inputTypeT)
                        if (oi >= 0) wasteGroupsByInput[Vector2i(output.index, oi)] = wasteGroup
                    }
                }

                var first = true
                for (input in allInputs) {
                    if (first) first = false
                    else sb.append(" + ")

                    val otherType = input.type
                    val outputType = otherType.outputs.firstOrNull { it.type == inputTypeT }
                        ?: error(
                            "Did not find $inputTypeT in ${otherType.recipeName}.outputs, " +
                                    "options: ${otherType.outputs}"
                        )

                    sb.production(input.index).append(" * ")
                        .append(input.outputMultiplier * outputType.amount)
                }
                sb.append(";\n")
            }
        }
        sb.append("\n")
        return wasteGroupsByInput
    }

    private fun collectInputs(nodes: List<Node>): Collection<Factory> {
        @Suppress("UNCHECKED_CAST")
        if (nodes.all { it is Factory }) return (nodes as List<Factory>)

        val result = HashSet<Factory>()
        val visited = HashSet<Connector>()

        fun findInputs(node: Node) {
            when (node) {
                is Factory -> result.add(node)
                is Connector -> if (visited.add(node)) {
                    for (member in node.inputs[0]) {
                        findInputs(member)
                    }
                }
            }
        }

        for (member in nodes) {
            findInputs(member)
        }
        return result
    }

    private fun defineWasteGroups(sb: StringBuilder, wasteGroupsByInput: Map<*, WasteGroup>): Int {
        var wgIndex = 0
        for ((_, wg) in wasteGroupsByInput) {
            sb.append("var wg").append(wgIndex).append(";\n")
            sb.append("s.t. WGMin").append(wgIndex)
                .append(": wg").append(wgIndex).append(" >= 0;\n")

            sb.append("s.t. WGWaste").append(wgIndex)
                .append(": wg").append(wgIndex).append(" = ")

            for (output in wg.outputs) {
                output as? Factory ?: continue
                val i = output.type.outputs.first { it.type == wg.type }
                sb.append(" + ").production(output.index).append(" * ")
                    .append(output.outputMultiplier * i.amount)
            }
            for (input in wg.inputs) {
                input as? Factory ?: continue
                val i = input.type.inputs.first { it.type == wg.type }
                sb.append(" - ").production(input.index).append(" * ")
                    .append(input.inputMultiplier * i.amount)
            }
            sb.append(";\n")
            wgIndex++
        }
        sb.append("\n")
        return wgIndex
    }

    private fun defineProduction(sb: StringBuilder, factories: List<Factory>) {
        for (i in factories.indices) {
            sb.append("var ").production(i).append(";\n")
        }
        sb.append("\n")

        for (i in factories.indices) {
            val factory = factories[i]
            if (factory.limit >= 0) {
                sb.append("s.t. FEq").append(i).append(": ").production(i)
                    .append(" = ").append(factory.limit).append(";\n")
            } else {
                sb.append("s.t. FMin").append(i)
                    .append(": ").production(i).append(" >= 0;\n")
            }
        }
    }

    private fun defineTargets(sb: StringBuilder, factories: List<Factory>, numWasteGroups: Int) {
        // minimize over-production
        sb.append("minimize Wasted: ")
        for (i in 0 until numWasteGroups) {
            sb.append(" + wg").append(i)
        }
        sb.append(";\n")

        sb.append("solve;\n")
        sb.append("display Wasted")
        for (i in factories.indices) {
            sb.append(", f").append(i)
        }
        sb.append(";\n")
        sb.append("end;\n")
    }

    private fun StringBuilder.production(f: Int): StringBuilder {
        append('f').append(f)
        return this
    }

}
