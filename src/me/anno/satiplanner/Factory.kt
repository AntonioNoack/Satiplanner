package me.anno.satiplanner

class Factory(var type: FactoryType) {

    var index = -1

    // 1% .. 250%, 5 decimals
    var overclock = 1f

    // 1,2 or 4, total = 2x?
    var sloops = 0

    // -1 = no limit
    var limit = -1

    var posX = 0f
    var posY = 0f

    var isSelected = false
    var calculated = ""

    val inputs = ArrayList<HashSet<Factory>>()
    val outputs = ArrayList<HashSet<Factory>>()

    fun connectTo(outputSlot: Int, other: Factory, inputSlot: Int) {
        while (outputs.size <= outputSlot) outputs.add(HashSet())
        outputs[outputSlot].add(other)

        while (other.inputs.size <= inputSlot) other.inputs.add(HashSet())
        other.inputs[inputSlot].add(this)
    }

}