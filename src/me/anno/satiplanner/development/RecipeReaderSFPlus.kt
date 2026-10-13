package me.anno.satiplanner.development

import me.anno.io.json.generic.JsonScanner
import me.anno.satiplanner.FactoryType
import me.anno.satiplanner.Resource
import me.anno.utils.OS.res
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.types.Strings.toDouble
import kotlin.math.max

fun readRecipesSFPlus(): List<FactoryType> {
    val file = res.getChild("sfplus.json")
    val scanner = JsonScanner(file.readTextSync())
    val types = ArrayList<FactoryType>()
    scanner.scanArray {
        var recipeName = "?"
        var factoryName = "?"
        var category = "?"
        var power = 0
        val input = ArrayList<Resource>(4)
        val output = ArrayList<Resource>(4)
        scanner.scanObject { k1 ->
            when (k1) {
                "name" -> recipeName = scanner.readString()
                "building" -> factoryName = scanner.readString()
                    .extractFactoryName()
                "producedIn" -> {
                    val sb = StringBuilder()
                    scanner.scanArray {
                        val name = scanner.readString().extractFactoryName()
                        if (name != "WorkBench" && name != "Workshop") {
                            if (sb.isNotEmpty()) sb.append(", ")
                            sb.append(name)
                        }
                    }
                    factoryName = sb.toString()
                }
                "category" -> category = scanner.readString()
                "input",
                "output" -> {
                    val isInput = k1 == "input"
                    scanner.scanArray {
                        var amount = 0.0
                        var isFluid = false
                        var type = "?"
                        scanner.scanObject { k2 ->
                            when (k2) {
                                "item" -> {
                                    val name = scanner.readString()
                                    isFluid = "/Fluids/" in name ||
                                            "Water/Desc" in name ||
                                            "Gas/Desc_" in name ||
                                            "Oil/Desc_" in name ||
                                            "Liquid" in name ||
                                            "Acid" in name
                                    type = name.extractItemName()
                                }
                                "perMin" -> amount = scanner.readNumber().toDouble()
                                else -> scanner.skipValue()
                            }
                        }
                        if (isFluid) amount /= 1000.0
                        if (amount > 0.0) {
                            val list = if (isInput) input else output
                            if (list.any2 { it.type == type }) {
                                amount += list.sumOf { if (it.type == type) it.amount else 0.0 }
                                list.removeIf { it.type == type }
                            }
                            list.add(Resource(type, amount))
                        }
                    }
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
    val recipes = readRecipesSFPlus()
    for (factory in recipes) {
        println(factory)
    }
    println("${recipes.size}x")
}