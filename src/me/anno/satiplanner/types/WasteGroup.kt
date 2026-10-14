package me.anno.satiplanner.types

import me.anno.satiplanner.nodes.Connector
import me.anno.satiplanner.nodes.Node

/**
 * All inputs and outputs of one kind, which are in some way connected.
 * Their total waste shall be minimal; their waste must be non-negative.
 * */
class WasteGroup(val type: String) {

    val inputs = HashSet<Node>()
    val outputs = HashSet<Node>()

    fun addWasteOutput(node: Node) {
        val i = node.findOutputIndex(type)
        val inputs = node.outputs.getOrNull(i) ?: return
        if (this.outputs.add(node)) {
            for (input in inputs) {
                addWasteInput(input)
            }

            if (node is Connector) {
                // self is an input, too
                addWasteInput(node)
            }
        }
    }

    fun addWasteInput(node: Node): Boolean {
        val i = node.findInputIndex(type)
        val inputs = node.inputs.getOrNull(i) ?: return false
        if (this.inputs.add(node)) {
            for (input in inputs) {
                addWasteOutput(input)
            }

            if (node is Connector) {
                // self is an output, too
                addWasteOutput(node)
            }
            return true
        } else return false
    }
}
