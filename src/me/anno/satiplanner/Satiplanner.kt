package me.anno.satiplanner

import me.anno.Build
import me.anno.config.DefaultConfig.style
import me.anno.engine.Events.addEvent
import me.anno.engine.WindowRenderFlags
import me.anno.language.translation.NameDesc
import me.anno.satiplanner.logic.RecipeSerialization
import me.anno.satiplanner.logic.loadNodes
import me.anno.satiplanner.logic.loadFiles
import me.anno.satiplanner.types.FactoryType
import me.anno.satiplanner.ui.FactoryPanel
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.debug.PureTestEngine.Companion.testPureUI
import me.anno.utils.OS
import me.anno.utils.structures.Iterators.toList
import java.io.File

fun main() {

    val recipes = readRecipesFromFile()
    val renames = loadRenames()

    Build.isDebug = false
    Build.isShipped = true
    testPureUI("Satiplanner") {
        WindowRenderFlags.enableVSync = false
        WindowRenderFlags.showFPS = true
        WindowRenderFlags.showTutorialKeys = false

        val panel = FactoryPanel(recipes, renames, style)
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
                val factories = loadNodes(file, recipes)
                panel.nodes.clear()
                panel.nodes.addAll(factories)
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
