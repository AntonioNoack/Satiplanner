package me.anno.satiplanner

import me.anno.io.json.generic.JsonScanner
import me.anno.utils.OS.res
import me.anno.utils.types.Strings.toDouble
import kotlin.math.max

fun readRecipes2(): List<FactoryType> {
    val file = res.getChild("sfplus.json")
    val scanner = JsonScanner(file.readTextSync())
    val types = ArrayList<FactoryType>()
    scanner.scanArray {
        var recipeName = "?"
        var factoryName = "?"
        var category = "?"
        var power = 0
        val input = ResourceList(4)
        val output = ResourceList(4)
        scanner.scanObject { k1 ->
            when (k1) {
                "name" -> recipeName = scanner.readString()
                "building" -> factoryName = scanner.readString()
                    .extractFactoryName()
                "producedIn" -> {
                    val sb = StringBuilder()
                    scanner.scanArray {
                        if (sb.isNotEmpty()) sb.append(", ")
                        sb.append(scanner.readString().extractFactoryName())
                    }
                    factoryName = sb.toString()
                }
                "category" -> category = scanner.readString()
                "input" -> scanner.scanArray {
                    var amount = 0.0
                    var type = "?"
                    scanner.scanObject { k2 ->
                        when (k2) {
                            "item" -> type = scanner.readString().extractItemName()
                            "perMin" -> amount = scanner.readNumber().toDouble()
                            else -> scanner.skipValue()
                        }
                    }
                    if (amount > 0.0) input.add(Resource(type, amount))
                }
                "output" -> scanner.scanArray {
                    var amount = 0.0
                    var type = "?"
                    scanner.scanObject { k2 ->
                        when (k2) {
                            "item" -> type = scanner.readString().extractItemName()
                            "perMin" -> amount = scanner.readNumber().toDouble()
                            else -> scanner.skipValue()
                        }
                    }
                    if (amount > 0.0) output.add(Resource(type, amount))
                }
                "powerAverage" -> power = max(scanner.readNumber().toDouble().toInt(), 0)
                else -> scanner.skipValue()
            }
        }
        when (category) {
            "Walls", "Architecture", "Foundations" -> {}
            else -> {
                if (input.isNotEmpty() || output.isNotEmpty()) {
                    types.add(FactoryType(recipeName, factoryName, power, input, output))
                }
            }
        }
    }
    return types
}

fun String.extractFactoryName(): String {
    return substringAfterLast('/')
        .substringAfterLast("Build_")
}

fun String.extractItemName(): String {
    return substringAfterLast('/')
        .substringAfterLast("Desc_")
        .replace('_', ' ')
}

fun main() {
    val recipes = readRecipes2()
    for (factory in recipes) {
        println(factory)
    }
    println("${recipes.size}x")
}