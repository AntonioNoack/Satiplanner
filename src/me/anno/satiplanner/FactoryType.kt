package me.anno.satiplanner

import me.anno.utils.types.Strings.shorten

class FactoryType(
    val recipeName: String,
    val factoryName: String,
    val power: Int,
    val inputs: ResourceList,
    val outputs: ResourceList,
) {
    val shortRecipeName = recipeName.shorten(20)
    override fun toString() = "Factory($recipeName, $factoryName, $power MW, $inputs -> $outputs)"
}