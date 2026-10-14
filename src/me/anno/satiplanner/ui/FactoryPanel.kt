package me.anno.satiplanner.ui

import me.anno.Time
import me.anno.fonts.FontImpl
import me.anno.fonts.msdf.MSDFFont
import me.anno.gpu.GFX
import me.anno.input.Input
import me.anno.input.Key
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.dtTo01
import me.anno.maths.Maths.sq
import me.anno.parser.SimpleExpressionParser
import me.anno.satiplanner.askLoad
import me.anno.satiplanner.logic.NumberFormatting.format
import me.anno.satiplanner.logic.Solver
import me.anno.satiplanner.logic.createSimpleFactory
import me.anno.satiplanner.logic.folder
import me.anno.satiplanner.nodes.Connector
import me.anno.satiplanner.nodes.Factory
import me.anno.satiplanner.nodes.Node
import me.anno.satiplanner.types.FactoryType
import me.anno.satiplanner.types.WasteGroup
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.MapPanel
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.canvas.Canvas
import me.anno.ui.editor.files.FileNames.toAllowedFilename
import me.anno.utils.Color.withAlpha
import me.anno.utils.OS.res
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.none2
import me.anno.utils.types.Floats.roundToIntOr
import me.anno.utils.types.Strings.shorten
import org.joml.Vector2d
import org.joml.Vector2f
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class FactoryPanel(
    val types: List<FactoryType>,
    val renames: Map<String, String>,
    style: Style
) : MapPanel(style) {

    val shortRenames = renames
        .mapValues { (_, name) -> name.shorten(30) }

    val nodes = ArrayList<Node>()

    val fontImpl: FontImpl<*> = run {
        val folder = res.getChild("fonts")
        val fontImpl = MSDFFont(folder.getChild("VerdanaMSDF.png"))
        folder.getChild("VerdanaMSDF.json")
            .inputStream { input, exception ->
                exception?.printStackTrace()
                input?.use { fontImpl.read(input) }
            }
        fontImpl
    }

    var isValid = true
    var isCalculating = false
    var allFine: Boolean? = null

    fun validate() {
        if (isValid || isCalculating) return
        isCalculating = true
        thread(name = "Calculate") {
            isValid = true
            allFine = null
            allFine = try {
                Solver.optimizeProduction(nodes)
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

        nodes += createSimpleFactory(types)
        isValid = false
    }

    val statusFont = style.getFont("text")
    var limitFont = statusFont
    var textFont = statusFont
    var titleFont = statusFont.withBold(true)

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
        var power = 0.0
        for (i in nodes.indices) {
            val node = nodes[i] as? Factory ?: continue
            power += node.calculated * node.powerMultiplier * node.type.power
        }
        canvas.drawText(
            x + width.shr(1), y, statusFont, "${power.roundToIntOr()} MW",
            0xffffaa.withAlpha(255), bigBgColor.withAlpha(0),
            AxisAlignment.CENTER, AxisAlignment.MIN
        )
    }

    fun getUnitsX(node: Node): Int = when (node) {
        is Factory -> 7
        else -> 2
    }

    fun getUnitsY(node: Node): Int = when (node) {
        is Factory -> {
            val type = node.type
            2 + max(type.inputs.size, type.outputs.size)
        }
        else -> 1
    }

    fun getUnitsY0(node: Node): Int = when (node) {
        is Factory -> 1
        else -> 0
    }

    fun drawLinks(canvas: Canvas, sw: Int, du: Int) {
        val scale = scale.x.toFloat()

        // draw links
        for (nodeIndex in nodes.indices) {
            val node0 = nodes[nodeIndex]
            val u0 = getUnitsY(node0)
            val dy0 = (u0 * 0.5f * scale).toInt()

            val px0 = coordsToWindowX(node0.posX)
            val py0 = coordsToWindowY(node0.posY)

            val inputs = node0.inputs
            val maxNumInputs = when (node0) {
                is Factory -> node0.type.inputs.size
                is Connector -> 1
            }

            for (i in 0 until min(inputs.size, maxNumInputs)) {
                val dy2 = (getUnitsY0(node0) + i) * du + du.shr(1) - dy0
                val typeName = when (node0) {
                    is Factory -> node0.type.inputs[i].type
                    is Connector -> node0.resourceType
                }
                for (node1 in inputs[i]) {
                    val u1 = getUnitsY(node1)
                    val dy1 = (u1 * 0.5f * scale).toInt()

                    val px1 = coordsToWindowX(node1.posX)
                    val py1 = coordsToWindowY(node1.posY)

                    val j = node1.findOutputIndex(typeName)
                    val dy3 = (getUnitsY0(node1) + j) * du + du.shr(1) - dy1

                    val dx0 = (getUnitsX(node0) * 0.5f * scale).toInt()
                    val dxl0 = dx0 - sw.shr(1)

                    val dx1 = (getUnitsX(node1) * 0.5f * scale).toInt()
                    val dxl1 = dx1 - sw.shr(1)
                    canvas.drawLine(
                        px0 - dxl0, py0 + dy2,
                        px1 + dxl1, py1 + dy3,
                        1f, -1, nodeBgColor.withAlpha(0),
                        false,
                    )
                }
            }
        }
    }

    fun drawDraggedLink(canvas: Canvas, sw: Int, du: Int) {
        val window = window
        val node = draggedNode
        val scale = scale.x.toFloat()
        if (node != null && window != null) {
            canvas.push()

            val px = coordsToWindowX(node.posX).toInt()
            val py = coordsToWindowY(node.posY).toInt()

            val dx = (getUnitsX(node) * 0.5f * scale).toInt()
            val dy = (getUnitsY(node) * 0.5f * scale).toInt()

            val x0 = px - dx
            val x1 = px + dx
            val y0 = py - dy

            val xi = if (draggedIsInput) x0 + sw.shr(1) else x1 - sw.shr(1)
            val yi = y0 + (getUnitsY0(node) + draggedSlot) * du + du.shr(1)

            canvas.drawLine(
                xi.toFloat(), yi.toFloat(), window.mouseX, window.mouseY,
                1f, -1, nodeBgColor.withAlpha(0), flatEnds = false
            )
            canvas.pop()
        }
    }

    fun drawFactories(canvas: Canvas, sw: Int, du: Int) {
        val scale = scale.x.toFloat()
        limitFont = limitFont.withSize(scale * 0.5f)
        textFont = textFont.withSize(scale * 0.3f)
        val titleFont = titleFont.withSize(scale * 0.3f)
        val limitFont = limitFont
        val textFont = textFont

        for (nodeIndex in nodes.indices) {
            val node = nodes[nodeIndex] as? Factory ?: continue

            val px = coordsToWindowX(node.posX).toInt()
            val py = coordsToWindowY(node.posY).toInt()

            val dx = (getUnitsX(node) * 0.5f * scale).toInt()
            val dy = (getUnitsY(node) * 0.5f * scale).toInt()

            val x0 = px - dx
            val x1 = px + dx
            val y0 = py - dy
            val y1 = py + dy
            if (x1 < canvas.x0 || y1 < canvas.y0 ||
                x0 > canvas.x1 || y0 > canvas.y1
            ) continue

            val shadow =
                if (node.isSelected) selectedShadowColor
                else defaultShadowColor

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
                val type = node.type
                canvas.drawText(
                    titleX, titleY, 0, titleFont, fontImpl,
                    type.recipeName, 0xffffff.withAlpha(255), titleBgColor.withAlpha(0),
                    w - du * 2, titleFont.sizeInt,
                    AxisAlignment.MIN, AxisAlignment.MIN,
                )
                canvas.drawText(
                    titleX, titleY + titleFont.sizeInt + limitFont.sizeInt.shr(2),
                    0, textFont, fontImpl,
                    type.factoryName, 0xffffff.withAlpha(255), titleBgColor.withAlpha(0),
                    w - du * 2, limitFont.sizeInt,
                    AxisAlignment.MIN, AxisAlignment.MIN,
                )
            }

            if (limitFont.sizeInt > 3) {
                val countY = y1 - du.shr(2) * 3
                val hasLimit = node.limit >= 0f
                canvas.drawText(
                    px, countY, 0, limitFont, fontImpl,
                    if (hasLimit) "> ${node.limit} <" else node.calculatedStr,
                    if (hasLimit) 0xffcc99.withAlpha(255) else 0xcccccc.withAlpha(255),
                    titleBgColor.withAlpha(0),
                    w, -1,
                    AxisAlignment.CENTER, AxisAlignment.MIN,
                )
            }

            // draw multiplier and sloop
            if (textFont.sizeInt > 3) {
                canvas.drawText(
                    x1 - sw.shr(1), y0 + du * 2 / 5, 0, textFont, fontImpl,
                    "${node.overclock.format()}%", 0xcccccc.withAlpha(255), titleBgColor.withAlpha(0),
                    -1, -1, AxisAlignment.MAX, AxisAlignment.CENTER,
                )
                canvas.drawText(
                    x1 - sw.shr(1), y0 + du * 4 / 5, 0, textFont, fontImpl,
                    "${node.sloops} Sloops", 0xcccccc.withAlpha(255), titleBgColor.withAlpha(0),
                    -1, -1, AxisAlignment.MAX, AxisAlignment.CENTER,
                )
            }

            if (sw >= 2) {
                val hl = textFont.sizeInt
                val inputMultiplier = node.inputMultiplier
                val type = node.type
                for (i in type.inputs.indices) {
                    val y = y0 + du * (getUnitsY0(node) + i) + (du - sw).shr(1)
                    val input = node.inputs.getOrNull(i)
                    val inputType = type.inputs[i]
                    val color = if (!input.isNullOrEmpty()) slotFilledColor else slotEmptyColor
                    val wl = if (i >= type.outputs.size) w - du else w.shr(1) - du
                    canvas.drawRect(x0, y, sw, sw, color, 1)
                    if (textFont.sizeInt > 3) {
                        val inputType1 = shortRenames[inputType.type] ?: inputType.shortType
                        canvas.drawText(
                            x0 + du, y + sw.shr(1) - textFont.sizeInt, 0, textFont, fontImpl,
                            inputType1, 0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                            wl, hl, AxisAlignment.MIN, AxisAlignment.MIN,
                        )
                        canvas.drawText(
                            x0 + du, y + (sw + textFont.sizeInt).shr(1), 0, textFont, fontImpl,
                            "${(inputType.amount * node.calculated * inputMultiplier).format()}/min",
                            0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                            wl, hl, AxisAlignment.MIN, AxisAlignment.MIN,
                        )
                    }
                }

                val outputMultiplier = node.outputMultiplier
                for (i in type.outputs.indices) {
                    val y = y0 + du * (getUnitsY0(node) + i) + (du - sw).shr(1)
                    val output = node.outputs.getOrNull(i)
                    val outputType = type.outputs[i]
                    val color = if (!output.isNullOrEmpty()) slotFilledColor else slotEmptyColor
                    val wl = if (i >= type.inputs.size) w - du else w.shr(1) - du
                    canvas.drawRect(x1 - sw, y, sw, sw, color, 1)
                    if (textFont.sizeInt > 3) {
                        val outputType1 = shortRenames[outputType.type] ?: outputType.shortType
                        canvas.drawText(
                            x1 - du, y + sw.shr(1) - textFont.sizeInt, 0, textFont, fontImpl,
                            outputType1, 0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                            wl, hl, AxisAlignment.MAX, AxisAlignment.MIN,
                        )
                        canvas.drawText(
                            x1 - du, y + (sw + textFont.sizeInt).shr(1), 0, textFont, fontImpl,
                            "${(outputType.amount * node.calculated * outputMultiplier).format()}/min",
                            0xffffff.withAlpha(255), nodeBgColor.withAlpha(0),
                            wl, hl, AxisAlignment.MAX, AxisAlignment.MIN,
                        )
                    }
                }
            }

            // to make overlap
            canvas.finish()
        }
    }

    val slotFilledColor = 0x7985A0.withAlpha(255)
    val slotEmptyColor = 0x723333.withAlpha(255)

    val selectedShadowColor = 0xffaa13.withAlpha(255)
    val defaultShadowColor = 0x090B13.withAlpha(255)

    fun drawConnectors(canvas: Canvas, sw: Int, du: Int) {
        val scale = scale.x.toFloat()
        for (nodeIndex in nodes.indices) {
            val node = nodes[nodeIndex] as? Connector ?: continue

            val px = coordsToWindowX(node.posX).toInt()
            val py = coordsToWindowY(node.posY).toInt()

            val dx = (getUnitsX(node) * 0.5f * scale).toInt()
            val dy = (getUnitsY(node) * 0.5f * scale).toInt()

            val x0 = px - dx
            val x1 = px + dx
            val y0 = py - dy
            val y1 = py + dy
            if (x1 < canvas.x0 || y1 < canvas.y0 ||
                x0 > canvas.x1 || y0 > canvas.y1
            ) continue

            val shadow =
                if (node.isSelected) selectedShadowColor
                else defaultShadowColor

            val w = x1 - x0
            val h = y1 - y0

            // draw shadow around node
            canvas.drawRect(x0 - 1, y0 - 1, 1, h, shadow)
            canvas.drawRect(x1, y0 - 1, 1, h, shadow)
            canvas.drawRect(x0, y0 - 1, w, 1, shadow)
            canvas.drawRect(x0, y1, w, 1, shadow)
            canvas.drawRect(x0, y0, w, h, titleBgColor)

            if (sw >= 2) {

                val y = y0 + du * getUnitsY0(node) + (du - sw).shr(1)
                val input = node.inputs.firstOrNull()
                val colorIn = if (!input.isNullOrEmpty()) slotFilledColor else slotEmptyColor
                canvas.drawRect(x0, y, sw, sw, colorIn, 1)

                val output = node.outputs.firstOrNull()
                val colorOut = if (!output.isNullOrEmpty()) slotFilledColor else slotEmptyColor
                canvas.drawRect(x1 - sw, y, sw, sw, colorOut, 1)
            }

            // to make overlap
            canvas.finish()
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
        val sw = du * 2 / 3

        drawLinks(canvas, sw, du)
        drawConnectors(canvas, sw, du)
        drawFactories(canvas, sw, du)
        drawDraggedLink(canvas, sw, du)

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
        if (isHovered && isInFocus &&
            window != null &&
            GFX.someWindow.isInFocus
        ) {
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
            for (i in nodes.indices) {
                val node = nodes[i]
                if (node.isSelected) {
                    node.posX += dx
                    node.posY += dy
                }
            }
        }
        prevPos.set(center)
        target.add(v.x * dt, v.y * dt)
    }

    val isDragging get() = draggedNode != null

    var draggedNode: Node? = null
    var draggedSlot = -1
    var draggedType = ""
    var draggedIsInput = false

    fun getNodeAt(x: Float, y: Float): Node? {
        val cx = windowToCoordsX(x)
        val cy = windowToCoordsY(y)
        val node = nodes.minByOrNull { sq(it.posX - cx, it.posY - cy) }
            ?: return null

        val scale = scale.x
        val unitsX = getUnitsX(node)
        val dx = (unitsX * 0.5f * scale).toInt()

        val unitsY = getUnitsY(node)
        val dy = (unitsY * 0.5f * scale).toInt()

        val px = coordsToWindowX(node.posX).toInt()
        val py = coordsToWindowY(node.posY).toInt()
        return if (abs(px - x) < dx && abs(py - y) < dy) node else null
    }

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        val hoverData = getHoverData(x, y)
        nodes.forEach { if (it != hoverData?.node) it.isSelected = false }

        if (key == Key.BUTTON_LEFT) {
            // if is on input/output, start dragging
            if (hoverData != null) {
                val node = hoverData.node
                when (node) {
                    is Connector -> {
                        val slotIndex = hoverData.slotIndex
                        val isLeft = hoverData.isInput
                        if (slotIndex == 0) {
                            draggedSlot = 0
                            draggedType = node.resourceType
                            draggedNode = node
                            draggedIsInput = isLeft
                            return
                        }
                    }
                    is Factory -> {
                        val type = node.type
                        val slotIndex = hoverData.slotIndex
                        val isLeft = hoverData.isInput
                        val slotList = if (isLeft) type.inputs else type.outputs
                        if (slotIndex in slotList.indices) {
                            draggedSlot = slotIndex
                            draggedType = slotList[slotIndex].type
                            draggedNode = node
                            draggedIsInput = isLeft
                            return
                        }
                    }
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
        val srcNode = draggedNode ?: return
        val typeName = draggedType
        val srcIsInput = draggedIsInput
        val dstData = getHoverData(x, y)
        var success = false
        if (dstData != null) {
            val dstFactory = dstData.node
            success = if (srcIsInput) {
                dstFactory.connectTo(srcNode, typeName)
            } else {
                srcNode.connectTo(dstFactory, typeName)
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
            openOptionsForFactory(x, y, options, typeName) { dstNode ->
                val success = if (srcIsInput) {
                    dstNode.connectTo(srcNode, typeName)
                } else {
                    srcNode.connectTo(dstNode, typeName)
                }
                if (success) isValid = false
            }
        }

        draggedNode = null
        draggedType = ""
        draggedSlot = -1
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        val clicked = getNodeAt(x, y)

        // todo rectangle selection
        if (Input.isShiftDown || Input.isControlDown) {
            if (clicked != null) {
                clicked.isSelected = !clicked.isSelected
            }
        } else {
            nodes.forEach { it.isSelected = false }
            clicked?.isSelected = true
        }
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (isDragging) return
        if (nodes.none2 { it.isSelected } || !Input.isLeftDown) {
            return super.onMouseMoved(x, y, dx, dy)
        }

        val dx = windowToCoordsDirX(dx.toDouble()).toFloat()
        val dy = windowToCoordsDirY(dy.toDouble()).toFloat()
        for (i in nodes.indices) {
            val node = nodes[i]
            if (!node.isSelected) continue

            node.posX += dx
            node.posY += dy
        }
    }

    override fun onDeleteKey(x: Float, y: Float) {
        // delete factories
        val changed = nodes.removeIf { it.isSelected }
        if (changed) {
            for (i in nodes.indices) {
                val factory = nodes[i]
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
        if (clicked != null && clicked.node is Factory &&
            clicked.slotIndex < 0 && !clicked.isInput
        ) {
            val sloops = clicked.slotIndexF > -0.5f
            val factory = clicked.node
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
        } else if (clicked != null &&
            clicked.node is Factory &&
            clicked.slotIndex >= clicked.node.type
                .run { max(inputs.size, outputs.size) }
        ) {
            // ask for new limit
            Menu.askNumber(
                windowStack, NameDesc("Set Constant"),
                clicked.node.limit.format(), NameDesc("Set")
            ) { str ->
                val value = SimpleExpressionParser.parseDouble(str)
                clicked.node.limit = value ?: -1.0
                isValid = false
            }
        } else {
            openOptionsForFactory(
                x, y, types,
                resourceType = null, callback = null
            )
        }
    }

    fun openOptionsForFactory(
        x: Float, y: Float, types: List<FactoryType>,
        resourceType: String?,
        callback: ((Node) -> Unit)?
    ) {
        val options = ArrayList<MenuOption>(types.size + 1)
        fun create(node: Node) {
            node.posX = windowToCoordsX(x)
            node.posY = windowToCoordsY(y)

            nodes.add(node)
            isValid = false
            callback?.invoke(node)
        }
        if (resourceType != null) {
            options += MenuOption(NameDesc("Connector")) {
                create(Connector(resourceType))
            }
        }
        for (i in types.indices) {
            val type = types[i]
            options += MenuOption(NameDesc("${type.recipeName} (${type.factoryName})")) {
                create(Factory(type))
            }
        }
        Menu.openMenu(windowStack, NameDesc("Add Factory"), options)
    }

    fun getHoverData(x: Float, y: Float): HoverData? {
        val node = getNodeAt(x, y) ?: return null

        val scale = scale.x
        val unitsX = getUnitsX(node)
        val dx = (unitsX * 0.5f * scale).toInt()

        val unitsY = getUnitsY(node)
        val dy = (unitsY * 0.5f * scale).toInt()

        val px = (x - coordsToWindowX(node.posX) + dx) * 0.5f * unitsX / dx
        val py = (y - coordsToWindowY(node.posY) + dy) * 0.5f * unitsY / dy

        val unitsY0s = getUnitsY0(node).toFloat()
        val slotIndexF = py - unitsY0s
        val slotIndex = floor(slotIndexF).toInt()
        val sideX = px - unitsX * 0.5f
        val isInput = sideX < 0f

        if (node is Connector && abs(sideX) < 0.4f) {
            return HoverData(node, isInput, -1, slotIndexF)
        }

        return HoverData(node, isInput, slotIndex, slotIndexF)
    }

    private fun getInput(it: Node, type: String): Double {
        if (it !is Factory) return 0.0
        val idx = it.findInputIndex(type)
        if (idx < 0) return 0.0
        return it.inputMultiplier * it.calculated * it.type.inputs[idx].amount
    }

    private fun getOutput(it: Node, type: String): Double {
        if (it !is Factory) return 0.0
        val idx = it.findOutputIndex(type)
        if (idx < 0) return 0.0
        return it.outputMultiplier * it.calculated * it.type.outputs[idx].amount
    }

    override fun getTooltipText(x: Float, y: Float): String? {
        val hoverData = getHoverData(x, y) ?: return null
        return when (val node = hoverData.node) {
            is Connector -> {
                // sum all inputs and outputs for interesting statistics
                val type = node.resourceType
                val wg = WasteGroup(type)
                wg.addWasteInput(node)
                wg.addWasteOutput(node)

                val directInputSum = node.inputs.first().sumOf { getOutput(it, type) }
                val directOutputSum = node.outputs.first().sumOf { getInput(it, type) }
                val wgInputSum = wg.inputs.sumOf { getInput(it, type) }
                val wgOutputSum = wg.outputs.sumOf { getOutput(it, type) }
                val allInputSum = nodes.sumOf { getInput(it, type) }
                val allOutputSum = nodes.sumOf { getOutput(it, type) }
                "$type\n" +
                        "Node: ${directOutputSum.format()} → ${directInputSum.format()} (${(directOutputSum - directInputSum).format()})\n" +
                        "Net: ${wgOutputSum.format()} → ${wgInputSum.format()} (${(wgOutputSum - wgInputSum).format()})\n" +
                        "All: ${allOutputSum.format()} → ${allInputSum.format()} (${(allOutputSum - allInputSum).format()})\n"
            }
            is Factory -> {
                val type = node.type
                val slotIndex = hoverData.slotIndex
                val isLeft = hoverData.isInput
                val speed = node.calculated
                if (isLeft && slotIndex in type.inputs.indices) {
                    val input = type.inputs[slotIndex]
                    val typeName = renames[input.type] ?: input.type
                    return "$typeName ${node.inputMultiplier} * ${speed.format()} * ${input.amount.format()}/min"
                }

                if (!isLeft && slotIndex in type.outputs.indices) {
                    val input = type.outputs[slotIndex]
                    val typeName = renames[input.type] ?: input.type
                    return "$typeName ${node.outputMultiplier} * ${speed.format()} * ${input.amount.format()}/min"
                }

                "${type.recipeName} (${type.factoryName})"
            }
        }
    }

    var lastSaveName = ""

    override fun onKeyTyped(x: Float, y: Float, key: Key) {
        if (Input.isControlDown) when (key) {
            Key.KEY_S -> askSave(x, y)
            Key.KEY_L -> askLoad(this, types)
            else -> {}
        }
    }

    fun askSave(x: Float, y: Float) {
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
                me.anno.satiplanner.logic.save(file, nodes)
                Menu.msg(NameDesc("Saved as $file"))
            }
        }
    }

}