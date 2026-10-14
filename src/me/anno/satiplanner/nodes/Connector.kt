package me.anno.satiplanner.nodes

class Connector(val resourceType: String) : Node() {

    override fun findInputIndex(type: String): Int = findIndex(type)
    override fun findOutputIndex(type: String): Int = findIndex(type)

    init {
        ensureInputSlot(0)
        ensureOutputSlot(0)
    }

    fun findIndex(type1: String): Int {
        return if (type1 == resourceType) 0 else -1
    }
}