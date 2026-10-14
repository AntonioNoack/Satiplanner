package me.anno.satiplanner.logic

import me.anno.satiplanner.nodes.Factory
import me.anno.satiplanner.types.FactoryType

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
