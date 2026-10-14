package me.anno.satiplanner.ui

import me.anno.satiplanner.nodes.Node

class HoverData(
    val node: Node, val isInput: Boolean,
    val slotIndex: Int, val slotIndexF: Float
)