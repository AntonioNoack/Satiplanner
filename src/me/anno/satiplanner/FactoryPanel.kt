package me.anno.satiplanner

import me.anno.Time
import me.anno.input.Input
import me.anno.input.Key
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.dtTo01
import me.anno.maths.Maths.sq
import me.anno.parser.SimpleExpressionParser
import me.anno.satiplanner.FactoryOptimizer.createSimpleFactory
import me.anno.satiplanner.NumberFormatting.format
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.MapPanel
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.canvas.Canvas
import me.anno.ui.editor.files.FileNames.toAllowedFilename
import me.anno.utils.Color.withAlpha
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.none2
import me.anno.utils.types.Floats.roundToIntOr
import org.joml.Vector2d
import org.joml.Vector2f
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class FactoryPanel(val types: List<FactoryType>, style: Style) : MapPanel(style) {

    val factories = ArrayList<Factory>()

    var isValid = true
    var isCalculating = false
    var allFine: Boolean? = null

    fun validate() {
        if (isValid || isCalculating) return
        isCalculating = true
        val factories = ArrayList(factories)
        thread(name = "Calculate") {
            isValid = true
            allFine = null
            allFine = try {
                FactoryOptimizer.optimize(factories)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            isCalculating = false
        }
    }

    init {
        scale.set(30.0)
        targetScale.set(scale)

        factories += createSimpleFactory(types)
        isValid = false
    }

    val statusFont = style.getFont("text")
    var limitFont = statusFont
    var textFont = statusFont
    var titleFont = statusFont.withBold(true)

    val unitsX = 7
    val unitsY0 = 2
    val unitsY0s = 1

    val nodeBgColor = 0x252a33.withAlpha(200)
    val titleBgColor = 0x373C48.withAlpha(255)
    val bigBgColor = 0x222733.withAlpha(255)

    fun drawStatus(canvas: Canvas) {
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
        canvas.drawText(
            x, y, statusFont, statusText, statusColor,
            bigBgColor.withAlpha(0)
        )
    }

    fun drawPower(canvas: Canvas) {
        val power = factories.sumOf { it.calculated * it.powerMultiplier * it.type.power }
        canvas.drawText(
            x + width.shr(1), y, statusFont, "${power.roundToIntOr()} MW",
            0xffffaa.withAlpha(255), bigBgColor.withAlpha(0),
            AxisAlignment.CENTER, AxisAlignment.MIN
        )
    }

    fun drawLinks(canvas: Canvas, dx: Int, sw: Int, du: Int) {
        val scale = scale.x.toFloat()

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
                val typeName = t0.inputs[i].type
                for (f1 in inputs[i]) {
                    val t1 = f1.type

                    val u1 = unitsY0 + max(t1.inputs.size, t1.outputs.size)
                    val dy1 = (u1 * 0.5f * scale).toInt()

                    val px1 = coordsToWindowX(f1.posX)
                    val py1 = coordsToWindowY(f1.posY)

                    val j = t1.outputs.indexOfFirst { it.type == typeName }
                    val dy2 = (unitsY0s + i) * du + du.shr(1) - dy0
                    val dy3 = (unitsY0s + j) * du + du.shr(1) - dy1
                    canvas.drawLine(
                        px0 - dxl, py0 + dy2,
                        px1 + dxl, py1 + dy3,
                        1f, -1, nodeBgColor.withAlpha(0),
                        false,
                    )
                }
            }
        }
    }

    fun drawDraggedLink(canvas: Canvas, dx: Int, sw: Int, du: Int) {
        val window = window
        val factory = draggedFactory
        val scale = scale.x.toFloat()
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
            val yi = y0 + (unitsY0s + draggedSlot) * du + du.shr(1)

            canvas.drawLine(
                xi.toFloat(), yi.toFloat(), window.mouseX, window.mouseY,
                1f, -1, nodeBgColor.withAlpha(0), flatEnds = false
            )
            canvas.pop()
        }
    }

    fun drawFactories(canvas: Canvas, dx: Int, sw: Int, du: Int) {
        val scale = scale.x.toFloat()
        limitFont = limitFont.withSize(scale * 0.5f)
        textFont = textFont.withSize(scale * 0.3f)
        val titleFont = titleFont.withSize(scale * 0.3f)
        val limitFont = limitFont
        val textFont = textFont

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

            val titleX = x0 + du.shr(3)
            val titleY = y0 + (du - titleFont.sizeInt).shr(3)
            if (titleFont.sizeInt > 3) {
                canvas.drawText(
                    titleX, titleY, titleFont, type.recipeName,
                    0xffffff.withAlpha(255), titleBgColor.withAlpha(0),
                    w - du * 2, titleFont.sizeInt,
                    AxisAlignment.MIN, AxisAlignment.MIN,
                )
                canvas.drawText(
                    titleX, titleY + titleFont.sizeInt + limitFont.sizeInt.shr(2),
                    textFont, type.factoryName,
                    0xffffff.withAlpha(255), titleBgColor.withAlpha(0),
                    w - du * 2, limitFont.sizeInt,
                    AxisAlignment.MIN, AxisAlignment.MIN,
                )
            }

            if (limitFont.sizeInt > 3) {
                val countY = y1 - du.shr(2) * 3
                val hasLimit = factory.limit >= 0f
                canvas.drawText(
                    px, countY, limitFont,
                    if (hasLimit) "> ${factory.limit} <" else factory.calculatedStr,
                    if (hasLimit) 0xffcc99.withAlpha(255) else 0xcccccc.withAlpha(255),
                    titleBgColor.withAlpha(0),
                    w, -1,
                    AxisAlignment.CENTER, AxisAlignment.MIN,
                )
            }

            // draw multiplier and sloop
            if (textFont.sizeInt > 3) {
                canvas.drawText(
                    x1 - sw.shr(1), y0 + du * 2 / 5, textFont, "${factory.overclock.format()}%",
                    0xcccccc.withAlpha(255), titleBgColor.withAlpha(0),
                    AxisAlignment.MAX, AxisAlignment.CENTER,
                )
                canvas.drawText(
                    x1 - sw.shr(1), y0 + du * 4 / 5, textFont, "${factory.sloops} Sloops",
                    0xcccccc.withAlpha(255), titleBgColor.withAlpha(0),
                    AxisAlignment.MAX, AxisAlignment.CENTER,
                )
            }

            if (sw >= 2) {
                val hl = textFont.sizeInt
                val bg3 = 0x7985A0.withAlpha(255)
                val bg2 = 0x723333.withAlpha(255)
                val inputMultiplier = factory.inputMultiplier
                for (i in type.inputs.indices) {
                    val y = y0 + du * (unitsY0s + i) + (du - sw).shr(1)
                    val input = factory.inputs.getOrNull(i)
                    val inputType = type.inputs[i]
                    val color = if (!input.isNullOrEmpty()) bg3 else bg2
                    val wl = if (i >= type.outputs.size) w - du else w.shr(1) - du
                    canvas.drawRect(x0, y, sw, sw, color, 1)
                    if (textFont.sizeInt > 3) {
                        canvas.drawText(
                            x0 + du, y + sw.shr(1) - textFont.sizeInt, textFont, inputType.shortType,
                            0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                            wl, hl, AxisAlignment.MIN, AxisAlignment.MIN,
                        )
                        canvas.drawText(
                            x0 + du, y + (sw + textFont.sizeInt).shr(1), textFont,
                            "${(inputType.amount * factory.calculated * inputMultiplier).format()}/min",
                            0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                            wl, hl, AxisAlignment.MIN, AxisAlignment.MIN,
                        )
                    }
                }

                val outputMultiplier = factory.outputMultiplier
                for (i in type.outputs.indices) {
                    val y = y0 + du * (unitsY0s + i) + (du - sw).shr(1)
                    val output = factory.outputs.getOrNull(i)
                    val outputType = type.outputs[i]
                    val color = if (!output.isNullOrEmpty()) bg3 else bg2
                    val wl = if (i >= type.inputs.size) w - du else w.shr(1) - du
                    canvas.drawRect(x1 - sw, y, sw, sw, color, 1)
                    if (textFont.sizeInt > 3) {
                        canvas.drawText(
                            x1 - du, y + sw.shr(1) - textFont.sizeInt, textFont, outputType.shortType,
                            0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                            wl, hl, AxisAlignment.MAX, AxisAlignment.MIN,
                        )
                        canvas.drawText(
                            x1 - du, y + (sw + textFont.sizeInt).shr(1), textFont,
                            "${(outputType.amount * factory.calculated * outputMultiplier).format()}/min",
                            0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                            wl, hl, AxisAlignment.MAX, AxisAlignment.MIN,
                        )
                    }
                }
            }

            // to make overlap
            canvas.finish()

            // todo draw labels & hoverable buttons
            //  - how many machines we calculated
            //  - speedup multiplier
            //  - output multiplier
        }
    }

    fun drawGrid(canvas: Canvas) {
        draw2DLineGrid(canvas, 0x333355.withAlpha(255), 2.0)
        draw2DLineGrid(canvas, 0x43436F.withAlpha(255), 20.0)
        draw2DLineGrid(canvas, 0x575790.withAlpha(255), 200.0)
    }

    override fun drawBackground(canvas: Canvas, dx: Int, dy: Int) {
        canvas.drawRect(x, y, width, height, bigBgColor)
    }

    override fun draw(canvas: Canvas) {
        drawBackground(canvas)
        drawGrid(canvas)

        validate()

        val scale = scale.x.toFloat()
        val du = scale.toInt()
        val dx = (unitsX * 0.5f * scale).toInt()
        val sw = du * 2 / 3

        drawLinks(canvas, dx, sw, du)
        drawFactories(canvas, dx, sw, du)
        drawDraggedLink(canvas, dx, sw, du)

        drawStatus(canvas)
        drawPower(canvas)
    }

    val velocity = Vector2f()
    val acceleration = Vector2f()

    override fun onUpdate() {
        super.onUpdate()
        applyMovement()
    }

    private val prevPos = Vector2d()
    fun applyMovement() {
        // if hovers on edge or types arrow or wasd keys, move
        val a = acceleration
        a.set(0f)
        val window = window
        if (isHovered && isInFocus && window != null) {
            val dx = min(20, min(width, height) / 30)
            val invDx = 1f / dx

            a.x += clamp((window.mouseX - x) * invDx)
            a.x -= clamp(((x + width) - window.mouseX) * invDx)

            a.y += clamp((window.mouseY - y) * invDx)
            a.y -= clamp(((y + height) - window.mouseY) * invDx)

            val f = 2.5f
            if (Input.isKeyDown(Key.KEY_W) || Input.isKeyDown(Key.KEY_ARROW_UP)) a.y -= f
            if (Input.isKeyDown(Key.KEY_A) || Input.isKeyDown(Key.KEY_ARROW_LEFT)) a.x -= f
            if (Input.isKeyDown(Key.KEY_S) || Input.isKeyDown(Key.KEY_ARROW_DOWN)) a.y += f
            if (Input.isKeyDown(Key.KEY_D) || Input.isKeyDown(Key.KEY_ARROW_RIGHT)) a.x += f

            a.mul(0.01f * min(width, height))
        }

        val dt = Time.deltaTime
        val v = velocity.lerp(a, dtTo01(5f * dt.toFloat()))

        if (Input.isLeftDown) {
            // use actual movement, not direct movement
            val dx = (center.x - prevPos.x).toFloat()
            val dy = (center.y - prevPos.y).toFloat()
            for (i in factories.indices) {
                val factory = factories[i]
                if (factory.isSelected) {
                    factory.posX += dx
                    factory.posY += dy
                }
            }
        }
        prevPos.set(center)
        target.add(v.x * dt, v.y * dt)
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
                val slotList = if (isLeft) type.inputs else type.outputs
                if (slotIndex in slotList.indices) {
                    draggedSlot = slotIndex
                    draggedType = slotList[slotIndex].type
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
        if (key == Key.BUTTON_LEFT) stopDragging(x, y)
    }

    fun stopDragging(x: Float, y: Float) {
        val srcFactory = draggedFactory ?: return
        val typeName = draggedType
        val srcIsInput = draggedIsInput
        val dstData = getHoverData(x, y)
        var success = false
        if (dstData != null) {
            val dstFactory = dstData.factory
            success = if (srcIsInput) {
                dstFactory.connectTo(srcFactory, typeName)
            } else {
                srcFactory.connectTo(dstFactory, typeName)
            }
        }

        if (success) {
            isValid = false
        } else {
            // show this with input/output type being draggedType
            val options = if (draggedIsInput) {
                types.filter { type -> type.outputs.any2 { it.type == typeName } }
            } else {
                types.filter { type -> type.inputs.any2 { it.type == typeName } }
            }
            openOptionsForFactory(x, y, options) { dstFactory ->
                val success = if (srcIsInput) {
                    dstFactory.connectTo(srcFactory, typeName)
                } else {
                    srcFactory.connectTo(dstFactory, typeName)
                }
                if (success) isValid = false
            }
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

    override fun onDoubleClick(x: Float, y: Float, button: Key) {
        val clicked = getHoverData(x, y)
        if (clicked != null && clicked.slotIndex < 0 && !clicked.isInput) {
            val sloops = clicked.slotIndexF > -0.5f
            val factory = clicked.factory
            if (sloops) {
                Menu.askNumber(
                    windowStack, NameDesc("Set Sloops"),
                    factory.sloops.toString(), NameDesc("Set")
                ) { str ->
                    factory.sloops = str.toIntOrNull() ?: 0
                    isValid = false
                }
            } else {
                Menu.askNumber(
                    windowStack, NameDesc("Set Overclock"),
                    factory.overclock.format(), NameDesc("Set")
                ) { str ->
                    val value = SimpleExpressionParser.parseDouble(str)
                    factory.overclock = value ?: 1.0
                    isValid = false
                }
            }
        } else if (clicked != null && clicked.slotIndex >=
            clicked.factory.type.run { max(inputs.size, outputs.size) }
        ) {
            // ask for new limit
            Menu.askNumber(
                windowStack, NameDesc("Set Constant"),
                clicked.factory.limit.format(), NameDesc("Set")
            ) { str ->
                val value = SimpleExpressionParser.parseDouble(str)
                clicked.factory.limit = value ?: -1.0
                isValid = false
            }
        } else {
            openOptionsForFactory(x, y, types) {}
        }
    }

    fun openOptionsForFactory(
        x: Float, y: Float, types: List<FactoryType>,
        callback: (Factory) -> Unit
    ) {
        Menu.openMenu(windowStack, NameDesc("Add Factory"), types.map { type ->
            MenuOption(NameDesc("${type.recipeName} (${type.factoryName})")) {
                val factory = Factory(type).apply {
                    posX = windowToCoordsX(x)
                    posY = windowToCoordsY(y)
                }
                factories.add(factory)
                isValid = false
                callback(factory)
            }
        })
    }

    fun getHoverData(x: Float, y: Float): HoverData? {
        val factory = getFactoryAt(x, y) ?: return null

        val scale = scale.x
        val dx = (unitsX * 0.5f * scale).toInt()

        val type = factory.type
        val unitsY = unitsY0 + max(type.inputs.size, type.outputs.size)
        val dy = (unitsY * 0.5f * scale).toInt()

        val px = (x - coordsToWindowX(factory.posX) + dx) * 0.5f * unitsX / dx
        val py = (y - coordsToWindowY(factory.posY) + dy) * 0.5f * unitsY / dy

        val slotIndex = floor(py - unitsY0s).toInt()
        val isInput = px < unitsX * 0.5f

        return HoverData(factory, isInput, slotIndex, py - unitsY0s)
    }

    override fun getTooltipText(x: Float, y: Float): String? {
        val hoverData = getHoverData(x, y) ?: return null
        val factory = hoverData.factory
        val type = factory.type
        val slotIndex = hoverData.slotIndex
        val isLeft = hoverData.isInput
        val speed = factory.calculated
        if (isLeft && slotIndex in type.inputs.indices) {
            val input = type.inputs[slotIndex]
            return "${input.type} ${speed.format()} * ${input.amount.format()}/min"
        }

        if (!isLeft && slotIndex in type.outputs.indices) {
            val input = type.outputs[slotIndex]
            return "${input.type} ${speed.format()} * ${input.amount.format()}/min"
        }

        return "${type.recipeName} (${type.factoryName})"
    }

    var lastSaveName = ""

    override fun onKeyTyped(x: Float, y: Float, key: Key) {
        if (Input.isControlDown) when (key) {
            Key.KEY_S -> {
                Menu.askName(
                    windowStack, x.toInt() - Menu.paddingX, y.toInt() - Menu.paddingY,
                    NameDesc("Enter Name"), lastSaveName,
                    NameDesc("Save"), enableSpellcheck = false, {
                        when (it.toAllowedFilename()) {
                            it -> {
                                var fileName = it
                                if (!fileName.endsWith(".json")) fileName += ".json"
                                if (folder.getChild(fileName).exists) 0x33ffff
                                else 0x33ff33
                            }
                            null -> 0xff3333
                            else -> 0xffff33
                        }.withAlpha(255)
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
                // ask whether user is sure?
                askLoad(this, types)
            }
            else -> {}
        }
    }

}