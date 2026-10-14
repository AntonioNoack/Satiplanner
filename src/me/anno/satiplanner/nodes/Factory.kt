package me.anno.satiplanner.nodes

import me.anno.maths.Maths.sq
import me.anno.satiplanner.types.FactoryType
import me.anno.satiplanner.types.Resource
import kotlin.math.log2
import kotlin.math.pow

class Factory(val type: FactoryType) : Node() {

    companion object {
        val overclockPower = log2(2.5) / log2(2.0)
    }

    // 1% .. 250%, 5 decimals
    var overclock = 100.0

    // 1,2 or 4, total = 2x?
    var sloops = 0

    val inputMultiplier: Double
        get() = overclock * 0.01
    val outputMultiplier: Double
        get() = inputMultiplier * sloopMultiplier
    val powerMultiplier: Double
        get() = (overclock * 0.01).pow(overclockPower) * sq(sloopMultiplier)

    // todo find out max sloops
    val sloopMultiplier: Double
        get() = 1.0 + sloops * 0.25

    // -1 = no limit
    var limit = -1.0

    var calculatedStr = ""
    var calculated = 0.0

    var checked = false

    override fun findInputIndex(type: String): Int = findTypeIndex(this.type.inputs, type)
    override fun findOutputIndex(type: String): Int = findTypeIndex(this.type.outputs, type)

    fun findTypeIndex(list: List<Resource>, type: String): Int {
        return list.indexOfFirst { it.type == type }
    }

}