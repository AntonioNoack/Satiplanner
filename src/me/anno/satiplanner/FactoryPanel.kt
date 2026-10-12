package me.anno.satiplanner

import me.anno.input.Input
import me.anno.input.Key
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.sq
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.MapPanel
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.canvas.Canvas
import me.anno.ui.editor.files.FileNames.toAllowedFilename
import me.anno.utils.Color.withAlpha
import me.anno.utils.structures.lists.Lists.none2
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class FactoryPanel(val types: List<FactoryType>, style: Style) : MapPanel(style) {

    val factories = ArrayList<Factory>()

    var isValid = true
    var isCalculating = false

    // todo resource inputs
    // todo resource outputs

    var allFine: Boolean? = null

    fun validate() {
        if (isValid || isCalculating) return
        isCalculating = true
        val factories = ArrayList(factories)
        thread(name = "Calculate") {
            isValid = true
            allFine = null
            allFine = FactoryOptimizer.optimize(factories)
            isCalculating = false
        }
    }

    init {
        scale.set(30.0)
        targetScale.set(scale)

        factories += createSimpleFactory(types)
        isValid = false
    }

    var titleFont = style.getFont("text")
    var textFont = titleFont

    val unitsX = 7
    val unitsY0 = 2
    val unitsY0s = 1

    val nodeBgColor = 0x252a33.withAlpha(200)
    val titleBgColor = 0x373C48.withAlpha(255)
    val bigBgColor = 0x222733.withAlpha(255)

    override fun draw(canvas: Canvas) {
        canvas.drawRect(x, y, width, height, bigBgColor)
        draw2DLineGrid(canvas, 0x333355.withAlpha(255), 2.0)
        draw2DLineGrid(canvas, 0x43436F.withAlpha(255), 20.0)
        draw2DLineGrid(canvas, 0x575790.withAlpha(255), 200.0)

        val statusColor = when (allFine) {
            null -> 0x777777
            true -> 0x77ff77
            false -> 0xff7777
        }.withAlpha(255)
        val statusText = when (allFine) {
            null -> "?"
            true -> "✅"
            false -> "❌"
        }
        canvas.drawText(x, y, titleFont, statusText, statusColor, bigBgColor)

        validate()

        val scale = scale.x.toFloat()
        val du = scale.toInt()
        val dx = (unitsX * 0.5f * scale).toInt()

        titleFont = titleFont.withSize(scale * 0.6f)
        textFont = textFont.withSize(scale * 0.4f)
        val titleFont = titleFont
        val textFont = textFont

        val sw = du * 2 / 3

        // draw links
        val dxl = dx - sw.shr(1)
        for (i in factories.indices) {
            val f0 = factories[i]
            val t0 = f0.type
            val u0 = unitsY0 + max(t0.inputs.size, t0.outputs.size)
            val dy0 = (u0 * 0.5f * scale).toInt()

            val px0 = coordsToWindowX(f0.posX)
            val py0 = coordsToWindowY(f0.posY)

            val inputs = f0.inputs
            for (i in 0 until min(inputs.size, t0.inputs.size)) {
                for (f1 in inputs[i]) {
                    val t1 = f1.type

                    val u1 = unitsY0 + max(t1.inputs.size, t1.outputs.size)
                    val dy1 = (u1 * 0.5f * scale).toInt()

                    val px1 = coordsToWindowX(f1.posX)
                    val py1 = coordsToWindowY(f1.posY)

                    val dy = (unitsY0s + i) * du + sw.shr(1)
                    canvas.drawLine(
                        px0 - dxl, py0 - dy0 + dy,
                        px1 + dxl, py1 - dy1 + dy,
                        1f, -1, nodeBgColor.withAlpha(0),
                        false,
                    )
                }
            }
        }

        for (i in factories.indices) {
            val factory = factories[i]
            val type = factory.type
            val unitsY = unitsY0 + max(type.inputs.size, type.outputs.size)
            val dy = (unitsY * 0.5f * scale).toInt()

            val px = coordsToWindowX(factory.posX).toInt()
            val py = coordsToWindowY(factory.posY).toInt()

            val x0 = px - dx
            val x1 = px + dx
            val y0 = py - dy
            val y1 = py + dy
            if (x1 < canvas.x0 || y1 < canvas.y0 ||
                x0 > canvas.x1 || y0 > canvas.y1
            ) continue

            val shadow =
                if (factory.isSelected) 0xffaa13.withAlpha(255)
                else 0x090B13.withAlpha(255)

            val w = x1 - x0
            val h = y1 - y0

            // draw shadow around node
            canvas.drawRect(x0 - 1, y0 - 1, 1, h, shadow)
            canvas.drawRect(x1, y0 - 1, 1, h, shadow)
            canvas.drawRect(x0, y0 - 1, w, 1, shadow)
            canvas.drawRect(x0, y1, w, 1, shadow)

            canvas.drawRect(x0, y0 + du, w, y1 - (y0 + du), nodeBgColor)
            canvas.drawRect(x0, y0, w, du, titleBgColor)

            val titleX = x0 + du.shr(2)
            val titleY = y0 + (du - titleFont.sizeInt).shr(1)
            canvas.drawText(
                titleX, titleY, titleFont, type.shortRecipeName,
                0xffffff.withAlpha(255), titleBgColor.withAlpha(0),
                w, -1, // todo bug: height limit is not respected properly
                AxisAlignment.MIN, AxisAlignment.MIN,
            )

            val countY = y1 - du
            if (allFine == true) canvas.drawText(
                px, countY, textFont, factory.calculated,
                0xffffff.withAlpha(255), titleBgColor.withAlpha(0),
                w, -1,
                AxisAlignment.CENTER, AxisAlignment.MIN,
            )

            if (sw >= 2) {
                val bg3 = 0x7985A0.withAlpha(255)
                val bg2 = 0x60697F.withAlpha(255)
                for (i in type.inputs.indices) {
                    val y = y0 + du * (unitsY0s + i) + (du - sw).shr(1)
                    val input = factory.inputs.getOrNull(i)
                    val color = if (!input.isNullOrEmpty()) bg3 else bg2
                    canvas.drawRect(x0, y, sw, sw, color, 1)
                    canvas.drawText(
                        x0 + du, y + (sw - textFont.sizeInt).shr(1), textFont, type.inputs[i].shortType,
                        0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                        w.shr(1), -1, // todo bug: height limit is not respected properly
                        AxisAlignment.MIN, AxisAlignment.MIN,
                    )
                }

                for (i in type.outputs.indices) {
                    val y = y0 + du * (unitsY0s + i) + (du - sw).shr(1)
                    val output = factory.outputs.getOrNull(i)
                    val color = if (!output.isNullOrEmpty()) bg3 else bg2
                    canvas.drawRect(x1 - sw, y, sw, sw, color, 1)
                    canvas.drawText(
                        x1 - du, y + (sw - textFont.sizeInt).shr(1), textFont, type.outputs[i].shortType,
                        0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                        w.shr(1), -1, // todo bug: height limit is not respected properly
                        AxisAlignment.MAX, AxisAlignment.MIN,
                    )
                }
            }

            // to make overlap
            canvas.finish()

            // todo draw labels & hoverable buttons
            //  - how many machines we calculated
            //  - speedup multiplier
            //  - output multiplier
        }

        val window = window
        val factory = draggedFactory
        if (factory != null && window != null) {
            canvas.push()

            val px = coordsToWindowX(factory.posX).toInt()
            val py = coordsToWindowY(factory.posY).toInt()

            val type = factory.type
            val unitsY = unitsY0 + max(type.inputs.size, type.outputs.size)
            val dy = (unitsY * 0.5f * scale).toInt()

            val x0 = px - dx
            val x1 = px + dx
            val y0 = py - dy

            val xi = if (draggedIsInput) x0 + sw.shr(1) else x1 - sw.shr(1)
            val yi = y0 + (unitsY0s + draggedSlot) * du + sw.shr(1)

            canvas.drawLine(
                xi.toFloat(), yi.toFloat(), window.mouseX, window.mouseY,
                1f, -1, nodeBgColor.withAlpha(0), flatEnds = false
            )
            canvas.pop()
        }
    }

    val isDragging get() = draggedFactory != null

    var draggedFactory: Factory? = null
    var draggedSlot = -1
    var draggedType = ""
    var draggedIsInput = false

    fun getFactoryAt(x: Float, y: Float): Factory? {
        val cx = windowToCoordsX(x)
        val cy = windowToCoordsY(y)
        val factory = factories.minByOrNull { sq(it.posX - cx, it.posY - cy) }
            ?: return null

        val scale = scale.x
        val dx = (unitsX * 0.5f * scale).toInt()

        val type = factory.type
        val unitsY = unitsY0 + max(type.inputs.size, type.outputs.size)
        val dy = (unitsY * 0.5f * scale).toInt()

        val px = coordsToWindowX(factory.posX).toInt()
        val py = coordsToWindowY(factory.posY).toInt()
        return if (abs(px - x) < dx && abs(py - y) < dy) factory else null
    }

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        val hoverData = getHoverData(x, y)
        factories.forEach { if (it != hoverData?.factory) it.isSelected = false }

        if (key == Key.BUTTON_LEFT) {
            // if is on input/output, start dragging
            if (hoverData != null) {
                val factory = hoverData.factory
                val type = factory.type
                val slotIndex = hoverData.slotIndex
                val isLeft = hoverData.isInput
                val limit = if (isLeft) type.inputs.size else type.outputs.size
                if (slotIndex in 0 until limit) {
                    draggedSlot = slotIndex
                    draggedType = type.inputs[slotIndex].type
                    draggedFactory = factory
                    draggedIsInput = isLeft
                    return
                }
            }
        }

        super.onKeyDown(x, y, key)
    }

    override fun onKeyUp(x: Float, y: Float, key: Key) {
        super.onKeyUp(x, y, key)
        stopDragging(x, y)
    }

    fun stopDragging(x: Float, y: Float) {
        val srcFactory = draggedFactory ?: return
        val dstData = getHoverData(x, y)
        var success = false
        if (dstData != null) {
            val dstFactory = dstData.factory
            val dstType = dstFactory.type
            val srcType = srcFactory.type
            val dstIndex = dstData.slotIndex
            val dstIsInput = dstData.isInput
            val srcList = if (dstIsInput) srcFactory.outputs else srcFactory.inputs
            val dstList = if (dstIsInput) dstFactory.inputs else srcFactory.outputs
            val srcTypeList = if (dstIsInput) srcType.outputs else srcType.inputs
            val dstTypeList = if (dstIsInput) dstType.inputs else dstType.outputs
            if (dstIndex in dstTypeList.indices && dstIsInput != draggedIsInput) {
                val srcIndex = draggedSlot
                val srcType = srcTypeList[srcIndex].type
                val dstType = dstTypeList[dstIndex].type

                val alreadyConnected =
                    dstFactory in (srcList.getOrNull(srcIndex) ?: emptySet()) ||
                            srcFactory in (dstList.getOrNull(dstIndex) ?: emptySet())

                if (alreadyConnected) {
                    // disconnect
                    srcList[srcIndex] -= dstFactory
                    dstList[dstIndex] -= srcFactory
                    success = true
                } else if (srcType == dstType) {
                    while (srcList.size <= srcIndex) srcList.add(HashSet())
                    while (dstList.size <= dstIndex) dstList.add(HashSet())

                    srcList[srcIndex].add(dstFactory)
                    dstList[dstIndex].add(srcFactory)
                    success = true
                }
            }
        }

        if (success) {
            isValid = false
        } else {
            // todo show this with input/output type being draggedType

        }

        draggedFactory = null
        draggedType = ""
        draggedSlot = -1
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        val clicked = getFactoryAt(x, y)

        // todo rectangle selection
        if (Input.isShiftDown || Input.isControlDown) {
            if (clicked != null) {
                clicked.isSelected = !clicked.isSelected
            }
        } else {
            factories.forEach { it.isSelected = false }
            clicked?.isSelected = true
        }
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (isDragging) return
        if (factories.none2 { it.isSelected } || !Input.isLeftDown) {
            return super.onMouseMoved(x, y, dx, dy)
        }

        val dx = windowToCoordsDirX(dx.toDouble()).toFloat()
        val dy = windowToCoordsDirY(dy.toDouble()).toFloat()
        for (i in factories.indices) {
            val factory = factories[i]
            if (!factory.isSelected) continue

            factory.posX += dx
            factory.posY += dy
        }
    }

    override fun onDeleteKey(x: Float, y: Float) {
        // delete factories
        val changed = factories.removeIf { it.isSelected }
        if (changed) {
            for (i in factories.indices) {
                val factory = factories[i]
                val inputs = factory.inputs
                for (j in inputs.indices) {
                    inputs[j].removeIf { it.isSelected }
                }

                val outputs = factory.outputs
                for (j in outputs.indices) {
                    outputs[j].removeIf { it.isSelected }
                }
            }
            isValid = false
        }
    }

    class HoverData(val factory: Factory, val isInput: Boolean, val slotIndex: Int)

    override fun onDoubleClick(x: Float, y: Float, button: Key) {
        Menu.openMenu(windowStack, NameDesc("Add Factory"), types.map { type ->
            MenuOption(NameDesc("${type.recipeName} (${type.factoryName})")) {
                factories.add(Factory(type).apply {
                    posX = windowToCoordsX(x)
                    posY = windowToCoordsY(y)
                })
                isValid = false
            }
        })
    }

    fun getHoverData(x: Float, y: Float): HoverData? {
        val factory = getFactoryAt(x, y) ?: return null

        val scale = scale.x
        val du = scale.toInt()
        val dx = (unitsX * 0.5f * scale).toInt()

        titleFont = titleFont.withSize(du * 0.6f)

        val type = factory.type
        val unitsY = unitsY0 + max(type.inputs.size, type.outputs.size)
        val dy = (unitsY * 0.5f * scale).toInt()

        val px = (x - coordsToWindowX(factory.posX) + dx) * 0.5f * unitsX / dx
        val py = (y - coordsToWindowY(factory.posY) + dy) * 0.5f * unitsY / dy

        val slotIndex = floor(py - unitsY0s).toInt()
        val isLeft = px < unitsX * 0.5f

        return HoverData(factory, isLeft, slotIndex)
    }

    override fun getTooltipText(x: Float, y: Float): String? {
        val hoverData = getHoverData(x, y) ?: return null
        val factory = hoverData.factory
        val type = factory.type
        val slotIndex = hoverData.slotIndex
        val isLeft = hoverData.isInput
        if (isLeft && slotIndex in type.inputs.indices) {
            val input = type.inputs[slotIndex]
            return "${input.type} x${input.amount}/min"
        }

        if (!isLeft && slotIndex in type.outputs.indices) {
            val input = type.outputs[slotIndex]
            return "${input.type} x${input.amount}/min"
        }

        return "${type.recipeName} (${type.factoryName})"
    }

    var lastSaveName = ""

    override fun onKeyTyped(x: Float, y: Float, key: Key) {
        if (Input.isControlDown) when (key) {
            Key.KEY_S -> {
                Menu.askName(
                    windowStack, NameDesc("Enter Name"), lastSaveName,
                    NameDesc("Save"), {
                        when (it.toAllowedFilename()) {
                            it -> {
                                var fileName = it
                                if (!fileName.endsWith(".json")) fileName += ".json"
                                if (folder.getChild(fileName).exists) 0x33ffff
                                else 0x33ff33
                            }
                            null -> 0xff3333
                            else -> 0xffff33
                        }
                    }) {
                    var fileName = it.toAllowedFilename()
                    if (fileName != null) {
                        if (!fileName.endsWith(".json")) fileName += ".json"
                        lastSaveName = fileName
                        val file = folder.getChild(fileName)
                        save(file, factories)
                        Menu.msg(NameDesc("Saved as $file"))
                    }
                }
            }
            Key.KEY_L -> {
                // todo ask whether user is sure
                askLoad(this, types)
            }
            else -> {}
        }
    }

}