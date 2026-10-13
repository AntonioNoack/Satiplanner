package me.anno.satiplanner

import org.apache.logging.log4j.LogManager
import java.io.File

object RecipeSerialization {

    private val LOGGER = LogManager.getLogger(RecipeSerialization::class)

    fun write(recipes: List<FactoryType>) {
        val text = StringBuilder()
        for (recipe in recipes) {
            text.append("- id: ").appendStr(recipe.recipeName).append('\n')
            if (recipe.factoryName.isNotEmpty()) {
                text.append("  machine: ").appendStr(recipe.factoryName).append('\n')
            }
            if (recipe.power != 0) text.append("  power: ").append(recipe.power).append('\n')
            if (recipe.inputs.isNotEmpty()) text.append("  inputs: ").appendList(recipe.inputs).append('\n')
            if (recipe.outputs.isNotEmpty()) text.append("  outputs: ").appendList(recipe.outputs).append('\n')
        }

        File("recipes.yaml")
            .writeText(text.toString())
    }

    private fun StringBuilder.appendList(list: ArrayList<Resource>): StringBuilder {
        for (i in list.indices) {
            if (i > 0) append(", ")
            val entry = list[i]
            var number = entry.amount.toString()
            if (number.endsWith(".0")) number = number.substring(0, number.length - 2)
            append(number)
            append("x ")
            append(entry.type.replace(',', ' '))
        }
        return this
    }

    private fun StringBuilder.appendStr(str: String): StringBuilder {
        append(if (':' in str) "'$str'" else str)
        return this
    }

    private fun String.toStr(): String {
        if (startsWith('\'') && endsWith('\''))
            return substring(1, length - 1).trim()
        return this
    }

    fun read(lines: List<String>): List<FactoryType> {
        var i = 0
        val result = ArrayList<FactoryType>()
        while (i < lines.size) {
            val line0 = lines[i++]
            if (line0.startsWith("- ")) {
                val i0 = i-- // undo reading that line
                var id = ""
                var power = 0
                var machine = ""
                val inputs = ArrayList<Resource>()
                val outputs = ArrayList<Resource>()
                inner@ while (i < lines.size) {
                    val line = lines[i++]
                    if (i > i0 && line.startsWith("- ")) {
                        i--
                        break@inner
                    }
                    val colon = line.indexOf(":")
                    if (colon == -1) continue
                    when (val key = line.substring(2, colon).trim()) {
                        "id" -> id = line.substring(colon + 1).trim().toStr()
                        "machine" -> machine = line.substring(colon + 1).trim().toStr()
                        "power" -> power = line.substring(colon + 1).trim().toIntOrNull() ?: 0
                        "inputs" -> readResources(line.substring(colon + 1), inputs)
                        "outputs" -> readResources(line.substring(colon + 1), outputs)
                        else -> LOGGER.warn("Unknown properly $key")
                    }
                }
                if (id.isNotEmpty()) {
                    result.add(FactoryType(id, machine, power, inputs, outputs))
                }
            }
        }
        return result
    }

    private fun readResources(str: String, outputs: ArrayList<Resource>) {
        val split = str.split(",")
        for (part in split) {
            val x = part.indexOf('x')
            if (x == -1) continue

            val amount = part.substring(0, x).trim().toDoubleOrNull() ?: continue
            val type = part.substring(x + 1, part.length).trim()
            outputs.add(Resource(type, amount))
        }
    }
}
