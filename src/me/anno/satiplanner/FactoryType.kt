package me.anno.satiplanner

class FactoryType(
    val recipeName: String,
    val factoryName: String,
    val power: Int,
    val inputs: ArrayList<Resource>,
    val outputs: ArrayList<Resource>,
) {
    override fun toString(): String {
        return "Factory($recipeName, $factoryName, $power MW, $inputs -> $outputs)"
    }
}