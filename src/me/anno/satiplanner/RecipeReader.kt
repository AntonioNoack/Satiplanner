package me.anno.satiplanner

import me.anno.utils.OS.res
import me.anno.utils.structures.Iterators.toList
import me.anno.utils.types.Strings.isBlank2

fun readRecipes(): List<FactoryType> {
    val file = res.getChild("recipes.txt")
    val lines = file.readLinesSync(1024).toList()
    val types = ArrayList<FactoryType>()
    for (i in lines.indices step 3) {
        val recipeName = lines[i]
        val (factoryName, power) = lines[i + 1].split(" · ")
        check(power.endsWith(" MW"))
        val (inputs, outputs) = lines[i + 2].split("→")
        val type = FactoryType(
            recipeName, factoryName,
            power.substring(0, power.length - 3).toInt(),
            inputs.toResourceList(),
            outputs.toResourceList()
        )
        types += type
    }
    return types
}

fun String.toResourceList(): ResourceList {
    val result = ResourceList(4)
    if (isBlank2()) return result
    val start = if (startsWith(' ')) 1 else 0
    val end = if (endsWith(" /min")) length - 5 else length
    for (part in substring(start, end).split(", ")) {
        val ci = part.indexOf(' ')
        val amount = part.substring(0, ci).toDouble()
        val name = part.substring(ci + 1).trim()
        result.add(Resource(name, amount))
    }
    return result
}

fun main() {
    for (factory in readRecipes()) {
        println(factory)
    }
}