package me.anno.satiplanner.ui

import me.anno.Time
import me.anno.fonts.FontImpl
import me.anno.fonts.msdf.MSDFFont
import me.anno.gpu.GFX
import me.anno.gpu.drawing.DrawCurves
import me.anno.input.Input
import me.anno.input.Key
import me.anno.io.json.generic.JsonScanner
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.dtTo01
import me.anno.maths.Maths.sq
import me.anno.parser.SimpleExpressionParser
import me.anno.satiplanner.askLoad
import me.anno.satiplanner.logic.*
import me.anno.satiplanner.logic.NumberFormatting.format
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
import me.anno.ui.input.NumberType
import me.anno.utils.Color.withAlpha
import me.anno.utils.OS.res
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.count2
import me.anno.utils.structures.lists.Lists.none2
import me.anno.utils.structures.lists.Lists.sumOfFloat
import me.anno.utils.types.Floats.f1
import me.anno.utils.types.Floats.roundToIntOr
import me.anno.utils.types.Floats.toIntOr
import me.anno.utils.types.Strings.shorten
import org.joml.AABBi
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

    @Suppress("unused")
    fun getUnitsX(node: Node): Int = 7

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
        canvas.custom {
            val batch = DrawCurves.lineBatch.start()
            drawLinksImpl(canvas, sw, du)
            DrawCurves.lineBatch.finish(batch)
        }
    }

    fun drawLinksImpl(canvas: Canvas, sw: Int, du: Int) {
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

                    val x0 = px0 - dxl0
                    val y0 = py0 + dy2
                    val x1 = px1 + dxl1
                    val y1 = py1 + dy3

                    if (overlaps(
                            min(x0, x1).toInt(), min(y0, y1).toInt(),
                            max(x0, x1).toInt(), max(y0, y1).toInt(), canvas
                        )
                    ) DrawCurves.drawLine(
                        x0, y0, x1, y1, 1f, -1,
                        nodeBgColor.withAlpha(0), false,
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

    fun updateFonts() {
        val scale = scale.x.toFloat()
        limitFont = limitFont.withSize(scale * 0.5f)
        textFont = textFont.withSize(scale * 0.3f)
        titleFont = titleFont.withSize(scale * 0.3f)
    }

    var numRecentBounds = 0
    private val maxRecentBounds = 32
    private val recentBounds = IntArray(maxRecentBounds * 4)
    fun drawFactories(
        canvas: Canvas, sw: Int, du: Int,
        isRectSelecting: Boolean
    ) {
        val scale = scale.x.toFloat()
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
            if (!overlaps(x0, y0, x1, y1, canvas)) continue

            if (numRecentBounds >= maxRecentBounds ||
                overlapsRecentBounds(x0, y0, x1, y1)
            ) finishBounds(canvas)

            drawFactory(
                canvas, sw, du, isRectSelecting,
                node, x0, y0, x1, y1
            )

            pushRecentBounds(x0, y0, x1, y1)
        }
    }

    fun overlapsRecentBounds(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        val recentBounds = recentBounds
        for (j in 0 until numRecentBounds) {
            val i = j shl 2
            if (overlaps(
                    x0, y0, x1, y1,
                    recentBounds[i], recentBounds[i + 1],
                    recentBounds[i + 2], recentBounds[i + 3]
                )
            ) return true
        }
        return false
    }

    fun pushRecentBounds(x0: Int, y0: Int, x1: Int, y1: Int) {
        val i = numRecentBounds++ * 4
        val recentBounds = recentBounds
        recentBounds[i] = x0
        recentBounds[i + 1] = y0
        recentBounds[i + 2] = x1
        recentBounds[i + 3] = y1
    }

    fun drawFactory(
        canvas: Canvas, sw: Int, du: Int,
        isRectSelecting: Boolean, node: Factory,
        x0: Int, y0: Int, x1: Int, y1: Int,
    ) {
        val titleFont = titleFont
        val limitFont = limitFont
        val textFont = textFont

        val willBeSelected = isRectSelecting && overlaps(x0, y0, x1, y1, rectSelect)
        val shadow =
            if (node.isSelected || willBeSelected) selectedShadowColor
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
                w - du * 2, textFont.sizeInt,
                AxisAlignment.MIN, AxisAlignment.MIN,
            )
        }

        if (limitFont.sizeInt > 3) {
            // limit
            val countY = y1 - du.shr(2) * 3
            val hasLimit = node.limit >= 0f
            canvas.drawText(
                (x0 + x1).shr(1), countY, 0, limitFont, fontImpl,
                if (hasLimit) "> ${node.limit} <" else node.calculatedStr,
                if (hasLimit) 0xffcc99.withAlpha(255) else 0xcccccc.withAlpha(255),
                titleBgColor.withAlpha(0),
                w, -1,
                AxisAlignment.CENTER, AxisAlignment.MIN,
            )
            // checkbox
            canvas.drawText(
                x1 - du.shr(3), y1 - du / 12, 0, limitFont, fontImpl,
                if (node.checked) "✅" else "⬜",
                0xcccccc.withAlpha(255), titleBgColor.withAlpha(0),
                -1, -1, AxisAlignment.MAX, AxisAlignment.MAX,
            )
        }

        // draw multiplier and sloop
        if (textFont.sizeInt > 3) {
            // overclock
            val dj = sw / 6
            canvas.drawText(
                x1 - dj, y0 + dj, 0, textFont, fontImpl,
                "${node.overclock.format1()}%", 0xcccccc.withAlpha(255), titleBgColor.withAlpha(0),
                -1, -1, AxisAlignment.MAX, AxisAlignment.MIN,
            )
            // sloop
            canvas.drawText(
                x1 - dj, y0 + du - dj, 0, textFont, fontImpl,
                "${node.sloops.format1()} Sloops", 0xcccccc.withAlpha(255), titleBgColor.withAlpha(0),
                -1, -1, AxisAlignment.MAX, AxisAlignment.MAX,
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
    }

    val slotFilledColor = 0x7985A0.withAlpha(255)
    val slotEmptyColor = 0x723333.withAlpha(255)

    val selectedShadowColor = 0xffaa13.withAlpha(255)
    val defaultShadowColor = 0x090B13.withAlpha(255)

    private fun overlaps(
        x0: Int, y0: Int, x1: Int, y1: Int,
        x2: Int, y2: Int, x3: Int, y3: Int,
    ): Boolean {
        return x1 >= x2 && y1 >= y2 &&
                x0 <= x3 && y0 <= y3
    }

    private fun overlaps(
        x0: Int, y0: Int, x1: Int, y1: Int,
        canvas: Canvas
    ): Boolean {
        return overlaps(
            x0, y0, x1, y1,
            canvas.x0, canvas.y0,
            canvas.x1, canvas.y1
        )
    }

    private fun overlaps(
        x0: Int, y0: Int, x1: Int, y1: Int,
        bounds: AABBi
    ): Boolean {
        return overlaps(
            x0, y0, x1, y1,
            bounds.minX, bounds.minY,
            bounds.maxX, bounds.maxY,
        )
    }

    fun drawConnectors(canvas: Canvas, sw: Int, du: Int, isRectSelecting: Boolean) {
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
            if (!overlaps(x0, y0, x1, y1, canvas)) continue

            if (numRecentBounds >= maxRecentBounds ||
                overlapsRecentBounds(x0, y0, x1, y1)
            ) finishBounds(canvas)

            drawConnector(
                canvas, sw, du, isRectSelecting,
                node, x0, y0, x1, y1
            )

            pushRecentBounds(x0, y0, x1, y1)
        }
    }

    fun drawConnector(
        canvas: Canvas, sw: Int, du: Int, isRectSelecting: Boolean,
        node: Connector, x0: Int, y0: Int, x1: Int, y1: Int,
    ) {

        val titleFont = titleFont
        val textFont = textFont

        val willBeSelected = isRectSelecting && overlaps(x0, y0, x1, y1, rectSelect)
        val shadow =
            if (node.isSelected || willBeSelected) selectedShadowColor
            else defaultShadowColor

        val w = x1 - x0
        val h = y1 - y0

        // draw shadow around node
        canvas.drawRect(x0 - 1, y0 - 1, 1, h, shadow)
        canvas.drawRect(x1, y0 - 1, 1, h, shadow)
        canvas.drawRect(x0, y0 - 1, w, 1, shadow)
        canvas.drawRect(x0, y1, w, 1, shadow)
        canvas.drawRect(x0, y0, w, h, titleBgColor)

        val titleW = w - du * 2
        val titleX = x0 + du + du.shr(3) + titleW.shr(1)
        val titleY = y0 + (du - titleFont.sizeInt).shr(3)
        if (titleFont.sizeInt > 3) {
            val type = node.resourceType
            canvas.drawText(
                titleX, titleY, 0, titleFont, fontImpl,
                type, 0xffffff.withAlpha(255), titleBgColor.withAlpha(0),
                titleW, titleFont.sizeInt,
                AxisAlignment.CENTER, AxisAlignment.MIN,
            )
            val directInputSum = node.inputs.first().sumOf { getOutput(it, type) }
            val directOutputSum = node.outputs.first().sumOf { getInput(it, type) }
            canvas.drawText(
                titleX, titleY + titleFont.sizeInt + limitFont.sizeInt.shr(2),
                0, textFont, fontImpl,
                "${directInputSum.f1()} -> ${directOutputSum.f1()}",
                0xffffff.withAlpha(255), titleBgColor.withAlpha(0),
                titleW, textFont.sizeInt,
                AxisAlignment.CENTER, AxisAlignment.MIN,
            )
        }

        if (sw >= 2) {

            val y = y0 + du * getUnitsY0(node) + (du - sw).shr(1)
            val input = node.inputs.firstOrNull()
            val colorIn = if (!input.isNullOrEmpty()) slotFilledColor else slotEmptyColor
            canvas.drawRect(x0, y, sw, sw, colorIn, 1)

            val output = node.outputs.firstOrNull()
            val colorOut = if (!output.isNullOrEmpty()) slotFilledColor else slotEmptyColor
            canvas.drawRect(x1 - sw, y, sw, sw, colorOut, 1)
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
        val isRectSelecting = Input.isLeftDown && Input.isShiftDown

        updateRectSelect()
        updateFonts()

        drawLinks(canvas, sw, du)
        drawConnectors(canvas, sw, du, isRectSelecting)
        drawFactories(canvas, sw, du, isRectSelecting)
        finishBounds(canvas)

        drawDraggedLink(canvas, sw, du)
        drawRectangleSelect(canvas, isRectSelecting)

        drawStatus(canvas)
        drawPower(canvas)
    }

    fun finishBounds(canvas: Canvas) {
        numRecentBounds = 0
        canvas.finish()
    }

    fun updateRectSelect() {
        val window = window ?: return
        val x0 = window.mouseXi
        val y0 = window.mouseYi
        val x1 = coordsToWindowX(downX).toIntOr()
        val y1 = coordsToWindowY(downY).toIntOr()
        rectSelect.minX = min(x0, x1)
        rectSelect.maxX = max(x0, x1)
        rectSelect.minY = min(y0, y1)
        rectSelect.maxY = max(y0, y1)
    }

    fun drawRectangleSelect(canvas: Canvas, isRectSelecting: Boolean) {
        if (!isRectSelecting) return
        val minX = rectSelect.minX
        val maxX = rectSelect.maxX
        val minY = rectSelect.minY
        val maxY = rectSelect.maxY
        val color = 0xffff77.withAlpha(255)
        canvas.drawRect(minX, minY, maxX - minX, 1, color)
        canvas.drawRect(minX, maxY, maxX - minX, 1, color)
        canvas.drawRect(minX, minY, 1, maxY - minY, color)
        canvas.drawRect(maxX, minY, 1, maxY - minY, color)
    }

    val velocity = Vector2f()
    val acceleration = Vector2f()

    var downX = 0f
    var downY = 0f
    val rectSelect = AABBi()

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

        if (key == Key.BUTTON_LEFT) {

            downX = windowToCoordsX(x)
            downY = windowToCoordsY(y)

            val hasManySelected = nodes.count2 { it.isSelected } > 1
            if (hasManySelected) return // don't start to drag

            // if is on input/output, start dragging
            val hoverData = getHoverData(x, y)
            if (hoverData != null) {
                when (val node = hoverData.node) {
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

    fun applyRectSelect() {
        val selectAll = !Input.isControlDown
        val scale = scale.x.toFloat()
        val rectSelect = rectSelect
        for (i in nodes.indices) {
            val node = nodes[i]
            val px = coordsToWindowX(node.posX).toInt()
            val py = coordsToWindowY(node.posY).toInt()

            val dx = (getUnitsX(node) * 0.5f * scale).toInt()
            val dy = (getUnitsY(node) * 0.5f * scale).toInt()

            val x0 = px - dx
            val x1 = px + dx
            val y0 = py - dy
            val y1 = py + dy
            if (overlaps(x0, y0, x1, y1, rectSelect)) {
                node.isSelected = selectAll || !node.isSelected
            }
        }
    }

    fun stopDragging(x: Float, y: Float) {

        if (Input.isShiftDown) {
            applyRectSelect()
            return
        }

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
        if (Input.isShiftDown) return // handled separately
        if (Input.isControlDown) {
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
        if (Input.isShiftDown && Input.isLeftDown) return // rectangle-select
        if (nodes.none2 { it.isSelected } || !Input.isLeftDown) {
            // move grid
            return super.onMouseMoved(x, y, dx, dy)
        }

        // move nodes
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
        val node = clicked?.node
        if (clicked != null && node is Factory &&
            clicked.onCheckbox
        ) {
            // toggle checked
            node.checked = !node.checked
        } else if (clicked != null && node is Factory &&
            clicked.slotIndex < 0 && !clicked.isInput
        ) {
            // edit sloops or overclock
            val sloops = clicked.slotIndexF > -0.5f
            if (sloops) {
                Menu.askFloat(
                    windowStack, NameDesc("Set Sloops"),
                    node.sloops, NameDesc("Set"),
                    NumberType.DOUBLE_PLUS
                ) { value ->
                    node.sloops = value
                    isValid = false
                }
            } else {
                Menu.askFloat(
                    windowStack, NameDesc("Set Overclock"),
                    node.overclock, NameDesc("Set"),
                    NumberType.DOUBLE_PLUS
                ) { value ->
                    node.overclock = value
                    isValid = false
                }
            }
        } else if (clicked != null &&
            node is Factory &&
            clicked.slotIndex >= node.type
                .run { max(inputs.size, outputs.size) }
        ) {
            // ask for new limit
            Menu.askNumber(
                windowStack, NameDesc("Set Constant"),
                node.limit.format(), NameDesc("Set")
            ) { str ->
                val value = SimpleExpressionParser.parseDouble(str)
                node.limit = value ?: -1.0
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

        if (node is Connector && abs(sideX) < 3.1f) {
            return HoverData(node, isInput, -1, slotIndexF, onCheckbox = false)
        }

        val onCheckbox = slotIndexF > unitsY - 2f && px > unitsX - 1f
        return HoverData(node, isInput, slotIndex, slotIndexF, onCheckbox)
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
            Key.KEY_S -> askSave()
            Key.KEY_L -> askLoad(this, types)
            else -> {}
        }
    }

    fun askSave() {
        Menu.askName(
            windowStack, NameDesc("Enter Name"), lastSaveName,
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
                saveNodes(file, nodes)
                Menu.msg(NameDesc("Saved as $file"))
            }
        }
    }

    override fun onCopyRequested(x: Float, y: Float): Any? {
        val selectedNodes = nodes.filter { it.isSelected }
        if (selectedNodes.isEmpty()) return null
        return saveNodes(selectedNodes).toString()
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        val newNodes = try {
            loadNodes(JsonScanner(data), types)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        if (newNodes.isEmpty()) return

        val dx = windowToCoordsX(x) - newNodes.sumOfFloat { it.posX } / newNodes.size
        val dy = windowToCoordsY(y) - newNodes.sumOfFloat { it.posY } / newNodes.size

        for (i in newNodes.indices) {
            val node = newNodes[i]
            node.posX += dx
            node.posY += dy
            node.isSelected = true
        }

        for (i in nodes.indices) {
            val node = nodes[i]
            node.isSelected = false
        }
        nodes.addAll(newNodes)
        isValid = false
    }

    fun Double.format1(): String {
        val str = f1()
        return if (str.endsWith(".0")) str.substring(0, str.length - 2) else str
    }

}