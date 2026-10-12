package me.anno.satiplanner

import kotlin.math.pow

object NumberFormatting {

    fun Double.format(): String {
        if (this > 1e9) return "Inf"
        val str = toString()
        return if (str.endsWith(".0")) {
            str.substring(0, str.length - 2)
        } else if (str.endsInNines()) {
            str.removeNines()
        } else str
    }

    @Suppress("RedundantIf")
    fun String.endsInNines(): Boolean {
        if ("999" !in this || '.' !in this) return false
        return endsWith("9991") ||
                endsWith("9992") ||
                endsWith("9993") ||
                endsWith("9994") ||
                endsWith("9995") ||
                endsWith("9996") ||
                endsWith("9997") ||
                endsWith("9998") ||
                endsWith("9999")
    }

    fun String.removeNines(): String {
        if (startsWith('-')) {
            return "-${substring(1).removeNines()}"
        }

        var i = length - 2
        while (this[i] == '9') i--
        var j = i
        while (this[j] != '.') j--
        val pow = i - j
        val extra = 10.0.pow(j - length + 2)
        val str1 = (toDouble() + extra).toString()
        val i1 = str1.indexOf('.') + 1 + kotlin.math.max(pow, 1)
        val str2 = str1.substring(0, i1)
        return str2
    }
}