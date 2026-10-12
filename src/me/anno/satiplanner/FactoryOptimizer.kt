package me.anno.satiplanner

import java.io.File
import kotlin.concurrent.thread
import kotlin.math.min

object FactoryOptimizer {

    val file = File("lastModel.mod")

    fun optimize(factories: List<Factory>): Boolean {

        for (i in factories.indices) {
            factories[i].calculated = "?"
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

        // todo fOut = f * factor

        // todo define the system of variables using glpsol
        val inf = 1000_000_000
        val sb = StringBuilder()
        for (i in factories.indices) {
            val factory = factories[i]
            val type = factory.type
            sb.append("var f").append(i).append(";\n")
            for (j in 0 until min(factory.inputs.size, type.inputs.size)) {
                sb.append("var f").append(i).append("In").append(j).append(";\n")
            }
        }
        sb.append("\n")

        for (i in factories.indices) {
            val factory = factories[i]
            if (factory.limit >= 0) {
                sb.append("s.t. FMax").append(i).append(": f").append(i)
                    .append(" <= ").append(factory.limit).append(";\n")
            }
            sb.append("s.t. FMin").append(i).append(": f").append(i).append(" >= 0;\n")
        }

        // todo define input dependencies
        for (i in factories.indices) {
            val factory = factories[i]
            val type = factory.type
            for (j in 0 until min(factory.inputs.size, type.inputs.size)) {
                sb.append("s.t. F${i}In${j}Min: f").append(i).append("In").append(j).append(" >= 0;\n")

                // in must be <= out
                sb.append("s.t. F${i}In${j}LTOut: f").append(i).append("In").append(j).append(" <= ")
                val inputs = factory.inputs[j]
                var first = true
                for (input in inputs) {
                    if (first) first = false
                    else sb.append(" + ")
                    sb.append("f").append(input.index).append(" * ")
                        .append(type.inputs[j].amount)
                }
                sb.append(";\n")

                // production is less than inputs
                sb.append("s.t. F${i}In${j}GTProd: f").append(i).append("In").append(j)
                    .append(" >= f$i * ${type.inputs[j].amount};\n")
            }
        }
        sb.append("\n")

        sb.append("maximize P: ")
        for (i in factories.indices) {
            if (i > 0) sb.append(" + ")
            sb.append("f").append(i)
        }
        sb.append(";\n")

        sb.append("solve;\n")
        sb.append("display P")
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
                    factory.calculated = line.substring(i + mid.length) + "x"
                }
            }
            println(line)
        }

        return process.waitFor() == 0
    }

}

fun createSimpleFactory(recipes: List<FactoryType>): List<Factory> {
    val f0 = Factory(recipes.first { it.recipeName == "Crushed Callanite" })
    val f1 = Factory(recipes.first { it.recipeName == "Crushed Copper" })
    val f2 = Factory(recipes.first { it.recipeName == "Copper Ingot" })
    val f3 = Factory(recipes.first { it.recipeName == "Copper Rod" })
    val f4 = Factory(recipes.first { it.recipeName == "Wire" })

    f0.limit = 3
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

fun main() {
    val recipes = readRecipes2()
    FactoryOptimizer.optimize(createSimpleFactory(recipes))
}