package me.anno.satiplanner.types

import me.anno.satiplanner.nodes.Node

/**
 * All inputs and outputs of one kind, which are in some way connected.
 * Their total waste shall be minimal; their waste must be non-negative.
 * */
class WasteGroup(val type: String) {

    val inputs = HashSet<Node>()
    val outputs = HashSet<Node>()

    fun addWasteOutput(factory: Node) {
        val i = factory.findOutputIndex(type)
        val inputs = factory.outputs.getOrNull(i) ?: return
        if (this.outputs.add(factory)) {
            for (input in inputs) {
                addWasteInput(input)
            }
        }
    }

    fun addWasteInput(factory: Node): Boolean {
        val i = factory.findInputIndex(type)
        val inputs = factory.inputs.getOrNull(i) ?: return false
        if (this.inputs.add(factory)) {
            for (input in inputs) {
                addWasteOutput(input)
            }
            return true
        } else return false
    }
}
