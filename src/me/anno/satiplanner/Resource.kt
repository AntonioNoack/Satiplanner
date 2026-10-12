package me.anno.satiplanner

import me.anno.utils.types.Strings.shorten

class Resource(val type: String, val amount: Float) {
    val shortType = type.shorten(30)

    override fun toString(): String {
        return "$type x$amount"
    }
}