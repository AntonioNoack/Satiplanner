package me.anno.satiplanner

/**
 * All inputs and outputs of one kind, which are in some way connected.
 * Their total waste shall be minimal; their waste must be non-negative.
 * */
class WasteGroup(val type: String) {
    val inputs = HashSet<Factory>()
    val outputs = HashSet<Factory>()

    fun addWasteOutput(factory: Factory) {
        val i = factory.type.outputs.indexOfFirst { it.type == type }
        check(i >= 0)
        val inputs = factory.outputs[i]
        if (this.outputs.add(factory)) {
            for (input in inputs) {
                addWasteInput(input)
            }
        }
    }

    fun addWasteInput(factory: Factory): Boolean {
        val i = factory.type.inputs.indexOfFirst { it.type == type }
        check(i >= 0)
        val inputs = factory.inputs[i]
        if (this.inputs.add(factory)) {
            for (input in inputs) {
                addWasteOutput(input)
            }
            return true
        } else return false
    }
}
