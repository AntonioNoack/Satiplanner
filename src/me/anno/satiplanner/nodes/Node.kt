package me.anno.satiplanner.nodes

sealed class Node {

    var posX = 0f
    var posY = 0f

    var index = -1

    var isSelected = false

    val inputs = ArrayList<ArrayList<Node>>(4)
    val outputs = ArrayList<ArrayList<Node>>(4)

    fun connectTo(outputSlot: Int, other: Node, inputSlot: Int) {
        ensureOutputSlot(outputSlot).add(other)
        other.ensureInputSlot(inputSlot).add(this)
    }

    fun ensureSlot(list: ArrayList<ArrayList<Node>>, slot: Int): ArrayList<Node> {
        while (list.size <= slot) list.add(ArrayList(4))
        return list[slot]
    }

    fun ensureInputSlot(inputSlot: Int) = ensureSlot(inputs, inputSlot)
    fun ensureOutputSlot(outputSlot: Int) = ensureSlot(outputs, outputSlot)

    abstract fun findInputIndex(type: String): Int
    abstract fun findOutputIndex(type: String): Int

    fun connectTo(other: Node, type: String): Boolean {
        val outputSlot = findOutputIndex(type)
        if (outputSlot < 0) return false

        val inputSlot = other.findInputIndex(type)
        if (inputSlot < 0) return false

        val outputs = ensureOutputSlot(outputSlot)
        val inputs = other.ensureInputSlot(inputSlot)

        if (this in inputs || other in outputs) {
            outputs.remove(other)
            inputs.remove(this)
        } else {
            outputs.addUnique(other)
            inputs.addUnique(this)
        }
        return true
    }

    fun ArrayList<Node>.addUnique(n: Node) {
        if (!contains(n)) add(n)
    }

}