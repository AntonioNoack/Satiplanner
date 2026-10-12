package me.anno.satiplanner

import me.anno.Build
import me.anno.config.DefaultConfig.style
import me.anno.engine.Events.addEvent
import me.anno.engine.WindowRenderFlags
import me.anno.language.translation.NameDesc
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.debug.PureTestEngine.Companion.testPureUI

// todo overclocking from 1% to 250%, 5 decimals
// todo output multiplier 1,2 or 4, having max is 200% (sloop)
// todo minimize over-production

fun main() {
    val recipes = readRecipes2()
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