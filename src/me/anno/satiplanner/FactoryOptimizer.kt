package me.anno.satiplanner

import org.joml.Vector2i
import kotlin.concurrent.thread
import kotlin.math.min

object FactoryOptimizer {

    fun optimize(factories: List<Factory>): Boolean {

        if (factories.isEmpty()) return true

        for (i in factories.indices) {
            val factory = factories[i]
            factory.calculatedStr = "?"
            factory.index = i
        }

        // fOut = f * factor
        // define the system of variables using glpsol
        val sb = StringBuilder()
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
                        val oi = output.type.inputs.indexOfFirst { it.type == inputTypeT }
                        check(oi >= 0)
                        wasteGroupsByInput[Vector2i(output.index, oi)] = wasteGroup
                    }
                }

                var first = true
                for (input in inputs) {
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

        var wgIndex = 0
        for ((_, wg) in wasteGroupsByInput) {
            sb.append("var wg").append(wgIndex).append(";\n")
            sb.append("s.t. WGMin").append(wgIndex)
                .append(": wg").append(wgIndex).append(" >= 0;\n")

            sb.append("s.t. WGWaste").append(wgIndex)
                .append(": wg").append(wgIndex).append(" = ")

            for (output in wg.outputs) {
                val i = output.type.outputs.first { it.type == wg.type }
                sb.append(" + ").production(output.index).append(" * ")
                    .append(output.outputMultiplier * i.amount)
            }
            for (input in wg.inputs) {
                val i = input.type.inputs.first { it.type == wg.type }
                sb.append(" - ").production(input.index).append(" * ")
                    .append(input.inputMultiplier * i.amount)
            }
            sb.append(";\n")
            wgIndex++
        }
        sb.append("\n")

        // minimize over-production
        sb.append("minimize Wasted: ")
        for (i in 0 until wgIndex) {
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

        val recipe = sb.toString()
        val process = ProcessBuilder("glpsol", "-m", "/dev/stdin").start()
        process.outputStream.write(recipe.toByteArray())
        process.outputStream.close()

        thread(name = "glpsol-err") {
            process.errorStream.bufferedReader().forEachLine { line ->
                System.err.println(line)
            }
        }
        process.inputStream.bufferedReader().forEachLine { line ->
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
            println(line)
        }

        return process.waitFor() == 0
    }


    fun StringBuilder.production(f: Int): StringBuilder {
        append('f').append(f)
        return this
    }

    fun createSimpleFactory(recipes: List<FactoryType>): List<Factory> {
        try {
            val f0 = Factory(recipes.first { it.recipeName == "Crushed Callanite" })
            val f1 = Factory(recipes.first { it.recipeName == "Crushed Copper" })
            val f2 = Factory(recipes.first { it.recipeName == "Copper Ingot" })
            val f3 = Factory(recipes.first { it.recipeName == "Copper Rod" })
            val f4 = Factory(recipes.first { it.recipeName == "Wire" })

            f0.limit = 3.0
            f0.connectTo(0, f1, 0)
            f1.connectTo(0, f2, 0)
            f2.connectTo(0, f3, 0)
            f3.connectTo(0, f4, 0)
            val nodes = listOf(f0, f1, f2, f3, f4)
            for (i in nodes.indices) {
                nodes[i].posX = i * 10f
            }
            return nodes
        } catch (_: NoSuchElementException) {
            return emptyList()
        }
    }

}
