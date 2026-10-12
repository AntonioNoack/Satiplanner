package me.anno.satiplanner

import me.anno.config.DefaultConfig.style
import me.anno.ui.debug.TestEngine.Companion.testUI3

// todo graph UI
// todo save files
// todo node = factory, N inputs, M outputs
// todo overclocking from 1% to 250%, 5 decimals
// todo output multiplier 1,2 or 4, having max is 200% (sloop)
// todo load all factories and resources
// todo fixing a factory, the start, or the end, and the other values are calculated

fun main() {
    val recipes = readRecipes2()
    testUI3("Satiplanner", FactoryPanel(recipes, style))
}