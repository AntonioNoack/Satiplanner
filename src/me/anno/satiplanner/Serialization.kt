package me.anno.satiplanner

import me.anno.io.files.FileReference
import me.anno.io.generic.GenericWriter.Companion.writeArray
import me.anno.io.generic.GenericWriter.Companion.writeObject
import me.anno.io.json.generic.JsonScanner
import me.anno.io.json.generic.JsonWriter
import me.anno.utils.OS.documents
import me.anno.utils.structures.arrays.IntArrayList
import me.anno.utils.types.Strings.toDouble
import me.anno.utils.types.Strings.toFloat
import me.anno.utils.types.Strings.toInt
import org.apache.logging.log4j.LogManager
import java.io.StringWriter

private val LOGGER = LogManager.getLogger("Serialization")

val folder = documents.getChild("Satiplanner")
fun loadFiles(): List<FileReference> {
    return folder.listChildren().filter { it.lcExtension == "json" }
}

fun load(file: FileReference, types: List<FactoryType>): List<Factory> {
    val scanner = JsonScanner(file.readTextSync())
    val factories = ArrayList<Factory>()
    val type0 = types.first()
    val typesByName = types.associateBy { it.recipeName }
    val inputData = IntArrayList()
    scanner.scanObject { k1 ->
        when (k1) {
            "factories" -> scanner.scanArray {
                val factory = Factory(type0)
                val factoryIndex = factories.size
                scanner.scanObject { k2 ->
                    when (k2) {
                        "type" -> factory.type = typesByName[scanner.readString()] ?: type0
                        "posX" -> factory.posX = scanner.readNumber().toFloat()
                        "posY" -> factory.posY = scanner.readNumber().toFloat()
                        "limit" -> factory.limit = scanner.readNumber().toDouble()
                        "overclock" -> factory.overclock = scanner.readNumber().toDouble()
                        "sloops" -> factory.sloops = scanner.readNumber().toInt()
                        "inputs" -> {
                            var slot = 0
                            scanner.scanArray {
                                scanner.scanArray {
                                    val otherIndex = scanner.readNumber().toInt()
                                    inputData.add(slot)
                                    inputData.add(factoryIndex)
                                    inputData.add(otherIndex)
                                }
                                slot++
                            }
                        }
                        else -> {
                            LOGGER.warn("Unknown property $k2")
                            scanner.skipValue()
                        }
                    }
                }
                factories.add(factory)
            }
        }
    }

    for (i in inputData.indices step 3) {
        val inputSlot = inputData[i]
        val facWithInput = factories[inputData[i + 1]]
        val facWithOutput = factories[inputData[i + 2]]
        val inputType = facWithInput.type.inputs.getOrNull(inputSlot)?.type ?: continue
        val outputSlot = facWithOutput.type.outputs.indexOfFirst { it.type == inputType }
        if (outputSlot >= 0) facWithOutput.connectTo(outputSlot, facWithInput, inputSlot)
    }

    return factories
}

fun save(file: FileReference, factories: List<Factory>) {
    file.getParent().tryMkdirs()

    val output = StringWriter()
    val writer = JsonWriter(output)
    for (i in factories.indices) {
        factories[i].index = i
    }

    writer.writeObject {
        writer.attr("factories")
        writer.writeArray(factories) { factory ->
            writer.writeObject {
                writer.attr("type")
                writer.write(factory.type.recipeName)

                writer.attr("posX")
                writer.write(factory.posX)

                writer.attr("posY")
                writer.write(factory.posY)

                writer.attr("limit")
                writer.write(factory.limit)

                writer.attr("overclock")
                writer.write(factory.overclock)

                writer.attr("sloops")
                writer.write(factory.sloops)

                writer.attr("inputs")
                writer.writeArray(factory.inputs) { input ->
                    writer.writeArray(input.map { it.index }) { index ->
                        writer.write(index)
                    }
                }
            }
        }
    }

    file.writeText(output.toString())
}