package me.anno.satiplanner

import java.io.File
import kotlin.concurrent.thread
import kotlin.math.min

object FactoryOptimizer {

    val file = File("lastModel.mod")

    fun optimize(factories: List<Factory>): Boolean {

        for (i in factories.indices) {
            factories[i].calculatedStr = "?"
        }

        // if not all inputs are connected, we can automatically set a factory to 0
        /*val factories = factories.filter {
            val type = it.type
            it.inputs.size >= type.inputs.size &&
                    type.inputs.indices.all { idx -> it.inputs[idx].isNotEmpty() }
        }*/

        for (i in factories.indices) {
            factories[i].index = i
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
                    .append(" * ").append(inputType.amount)
                    .append(" <= ")

                var first = true
                for (input in inputs) {
                    if (first) first = false
                    else sb.append(" + ")

                    val otherType = input.type
                    val otherI = otherType.outputs.firstOrNull { it.type == inputTypeT }
                        ?: error(
                            "Did not find $inputTypeT in ${otherType.recipeName}.outputs, " +
                                    "options: ${otherType.outputs}"
                        )

                    sb.production(input.index).append(" * ")
                        .append(otherI.amount)
                }
                sb.append(";\n")
            }

            for (i in 0 until min(factory.outputs.size, type.outputs.size)) {
                val outputs = factory.outputs[i]
                if (outputs.isEmpty()) continue

                val outputType = type.outputs[i]
                val outputTypeT = outputType.type
                sb.append("s.t. F${f}Out${i}GTIn: ").production(f)
                    .append(" * ").append(outputType.amount)
                    .append(" >= ")

                var first = true
                for (output in outputs) {
                    if (first) first = false
                    else sb.append(" + ")

                    val otherType = output.type
                    val otherI = otherType.inputs.firstOrNull { it.type == outputTypeT }
                        ?: error(
                            "Did not find $outputTypeT in ${otherType.recipeName}.inputs, " +
                                    "options: ${otherType.inputs}"
                        )

                    sb.production(output.index).append(" * ")
                        .append(otherI.amount)
                }
                sb.append(";\n")
            }
        }
        sb.append("\n")

        // minimize over-production
        sb.append("minimize Wasted: 0")
        for (f in factories.indices) {
            val factory = factories[f]
            val type = factory.type
            for (i in 0 until min(factory.inputs.size, type.inputs.size)) {
                val input = factory.inputs[i]
                if (input.isEmpty()) continue // ignored

                sb.append(" - ").production(f)
                    .append(" * ").append(type.inputs[i].amount)
            }
            for (i in 0 until min(factory.outputs.size, type.outputs.size)) {
                val output = factory.outputs[i]
                if (output.isEmpty()) continue // ignored

                sb.append(" + ").production(f)
                    .append(" * ").append(type.outputs[i].amount)
            }
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
        file.writeText(recipe)

        val process = ProcessBuilder("glpsol", "-m", file.absolutePath).start()
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
                    val numberF = number.toFloat()
                    factory.calculated = numberF
                    factory.calculatedStr = if (numberF > 1e6f) "Inf" else number + "x"
                }
            }
            println(line)
        }

        return process.waitFor() == 0
    }

}

fun StringBuilder.production(f: Int): StringBuilder {
    append('f').append(f)
    return this
}

@Deprecated("Not useful")
fun StringBuilder.input(f: Int, i: Int): StringBuilder {
    append('f').append(f).append("In").append(i)
    return this
}

fun createSimpleFactory(recipes: List<FactoryType>): List<Factory> {
    val f0 = Factory(recipes.first { it.recipeName == "Crushed Callanite" })
    val f1 = Factory(recipes.first { it.recipeName == "Crushed Copper" })
    val f2 = Factory(recipes.first { it.recipeName == "Copper Ingot" })
    val f3 = Factory(recipes.first { it.recipeName == "Copper Rod" })
    val f4 = Factory(recipes.first { it.recipeName == "Wire" })

    f0.limit = 3f
    f0.connectTo(0, f1, 0)
    f1.connectTo(0, f2, 0)
    f2.connectTo(0, f3, 0)
    f3.connectTo(0, f4, 0)
    val nodes = listOf(f0, f1, f2, f3, f4)
    for (i in nodes.indices) {
        nodes[i].posX = i * 10f
    }
    return nodes
}
