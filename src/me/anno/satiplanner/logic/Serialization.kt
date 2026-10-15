package me.anno.satiplanner.logic

import me.anno.io.files.FileReference
import me.anno.io.generic.GenericWriter.Companion.writeArray
import me.anno.io.generic.GenericWriter.Companion.writeObject
import me.anno.io.json.generic.JsonScanner
import me.anno.io.json.generic.JsonWriter
import me.anno.satiplanner.nodes.Connector
import me.anno.satiplanner.nodes.Factory
import me.anno.satiplanner.nodes.Node
import me.anno.satiplanner.types.FactoryType
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

fun loadNodes(file: FileReference, types: List<FactoryType>): List<Node> {
    val scanner = JsonScanner(file.readTextSync())
    return loadNodes(scanner, types)
}

fun loadNodes(scanner: JsonScanner, types: List<FactoryType>): List<Node> {
    val nodes = ArrayList<Node>()
    val type0 = types.first()
    val typesByName = types.associateBy { it.recipeName }
    val inputData = IntArrayList()
    scanner.scanObject { k1 ->
        when (k1) {
            "factories" -> scanner.scanArray {
                lateinit var node: Node
                val nodeIndex = nodes.size
                scanner.scanObject { k2 ->

                    when (k2) {
                        "type" -> {
                            val typeName = scanner.readString()
                            val type = typesByName[typeName] ?: type0
                            node = Factory(type)
                        }
                        "resourceType" -> {
                            val typeName = scanner.readString()
                            node = Connector(typeName)
                        }
                        "posX" -> node.posX = scanner.readNumber().toFloat()
                        "posY" -> node.posY = scanner.readNumber().toFloat()
                        "limit" -> {
                            val value = scanner.readNumber().toDouble()
                            (node as? Factory)?.limit = value
                        }
                        "overclock" -> {
                            val value = scanner.readNumber().toDouble()
                            (node as? Factory)?.overclock = value
                        }
                        "sloops" -> {
                            val value = scanner.readNumber().toDouble()
                            (node as? Factory)?.sloops = value
                        }
                        "checked" -> {
                            val value = scanner.readBoolean()
                            (node as? Factory)?.checked = value
                        }
                        "inputs" -> {
                            var slot = 0
                            scanner.scanArray {
                                scanner.scanArray {
                                    val otherIndex = scanner.readNumber().toInt()
                                    inputData.add(slot)
                                    inputData.add(nodeIndex)
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
                nodes.add(node)
            }
        }
    }

    for (i in inputData.indices step 3) {
        val inputSlot = inputData[i]
        val fIn = nodes[inputData[i + 1]]
        val fOut = nodes[inputData[i + 2]]
        val type = when (fIn) {
            is Factory -> fIn.type.inputs.getOrNull(inputSlot)?.type ?: continue
            is Connector -> fIn.resourceType
        }
        val outputSlot = fOut.findOutputIndex(type)
        if (outputSlot >= 0) fOut.connectTo(outputSlot, fIn, inputSlot)
    }

    return nodes
}

fun saveNodes(file: FileReference, nodes: List<Node>) {
    file.getParent().tryMkdirs()
    file.writeText(saveNodes(nodes).toString())
}

fun saveNodes(nodes: List<Node>): StringWriter {
    val output = StringWriter()
    val writer = JsonWriter(output)
    for (i in nodes.indices) {
        nodes[i].index = i
    }

    writer.writeObject {
        writer.attr("factories")
        writer.writeArray(nodes) { node ->
            writer.writeObject {

                when (node) {
                    is Factory -> {
                        writer.attr("type")
                        writer.write(node.type.recipeName)

                        writer.attr("limit")
                        writer.write(node.limit)

                        writer.attr("overclock")
                        writer.write(node.overclock)

                        writer.attr("sloops")
                        writer.write(node.sloops)

                        writer.attr("checked")
                        writer.write(node.checked)
                    }
                    is Connector -> {
                        writer.attr("resourceType")
                        writer.write(node.resourceType)
                    }
                }

                writer.attr("posX")
                writer.write(node.posX)

                writer.attr("posY")
                writer.write(node.posY)

                writer.attr("inputs")
                writer.writeArray(node.inputs) { input ->
                    writer.writeArray(input) { other ->
                        if (nodes.getOrNull(other.index) === other) {
                            writer.write(other.index)
                        }
                    }
                }
            }
        }
    }

    return output
}