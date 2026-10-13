package me.anno.satiplanner

import me.anno.Build
import me.anno.config.DefaultConfig.style
import me.anno.engine.Events.addEvent
import me.anno.engine.WindowRenderFlags
import me.anno.language.translation.NameDesc
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.debug.PureTestEngine.Companion.testPureUI
import me.anno.utils.OS
import me.anno.utils.structures.Iterators.toList
import java.io.File

fun main() {

    val recipes = readRecipesFromFile()
        .applyRenames(loadRenames())

    Build.isDebug = false
    Build.isShipped = true
    testPureUI("Satiplanner") {
        WindowRenderFlags.enableVSync = true
        WindowRenderFlags.showFPS = false
        WindowRenderFlags.showTutorialKeys = false

        val panel = FactoryPanel(recipes, style)
        addEvent { askLoad(panel, recipes) }
        panel
    }
}

fun readRecipesFromFile(): List<FactoryType> {
    val file = File("SatiplannerRecipes.yaml")
    if (file.exists()) return RecipeSerialization.read(file.readLines())

    val lines = OS.res.getChild("recipes/sfplus.yaml")
        .readLinesSync(256).toList()
    return RecipeSerialization.read(lines)
}

fun askLoad(panel: FactoryPanel, recipes: List<FactoryType>) {
    Menu.openMenu(
        panel.windowStack, NameDesc("Load"),
        loadFiles().map { file ->
            MenuOption(NameDesc(file.nameWithoutExtension)) {
                val factories = load(file, recipes)
                panel.factories.clear()
                panel.factories.addAll(factories)
                panel.lastSaveName = file.nameWithoutExtension
                panel.isValid = false
            }
        })
}

fun loadRenames(): Map<String, String> {
    val file = File("SatiplannerRenaming.txt")
    if (!file.exists()) return emptyMap()

    return file.readLines()
        .mapNotNull {
            val split = it.split("->")
            if (split.size == 2) split[0] to split[1] else null
        }.toMap()
}

fun List<FactoryType>.applyRenames(renames: Map<String, String>): List<FactoryType> {
    if (renames.isEmpty()) return this

    return map {
        FactoryType(
            it.recipeName, it.factoryName, it.power,
            it.inputs.applyRenames(renames),
            it.outputs.applyRenames(renames)
        )
    }
}

fun ArrayList<Resource>.applyRenames(renames: Map<String, String>): ArrayList<Resource> {
    return ArrayList(map { it.applyRenames(renames) })
}

fun Resource.applyRenames(renames: Map<String, String>): Resource {
    return Resource(renames[type] ?: type, amount)
}