package me.anno.satiplanner

class Factory(var type: FactoryType) {

    var index = -1

    // 1% .. 250%, 5 decimals
    var overclock = 1f

    // 1,2 or 4, total = 2x?
    var sloops = 0

    // -1 = no limit
    var limit = -1f

    var posX = 0f
    var posY = 0f

    var isSelected = false
    var calculatedStr = ""
    var calculated = 0f

    val inputs = ArrayList<HashSet<Factory>>()
    val outputs = ArrayList<HashSet<Factory>>()

    fun connectTo(outputSlot: Int, other: Factory, inputSlot: Int) {
        while (outputs.size <= outputSlot) outputs.add(HashSet())
        outputs[outputSlot].add(other)

        while (other.inputs.size <= inputSlot) other.inputs.add(HashSet())
        other.inputs[inputSlot].add(this)
    }

    fun connectTo(other: Factory, type: String): Boolean {
        val outputSlot = this.type.outputs.indexOfFirst { it.type == type }
        if (outputSlot < 0) return false

        val inputSlot = other.type.inputs.indexOfFirst { it.type == type }
        if (inputSlot < 0) return false

        while (outputs.size <= outputSlot) outputs.add(HashSet())
        while (other.inputs.size <= inputSlot) other.inputs.add(HashSet())
        if (other in outputs[outputSlot]) {
            outputs[outputSlot].remove(other)
            other.inputs[inputSlot].remove(this)
        } else {
            outputs[outputSlot].add(other)
            other.inputs[inputSlot].add(this)
        }
        return true
    }

}