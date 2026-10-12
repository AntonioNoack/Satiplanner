package me.anno.satiplanner

class FactoryType(
    val recipeName: String,
    val factoryName: String,
    val power: Int,
    val inputs: ResourceList,
    val outputs: ResourceList,
) {
    override fun toString(): String {
        return "Factory($recipeName, $factoryName, $power MW, $inputs -> $outputs)"
    }
}