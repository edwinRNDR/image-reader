package org.openrndr.imagereader

import kotlinx.coroutines.yield
import org.openrndr.draw.*
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class Tile(val x: Int, val y: Int, val colorBuffer: ColorBuffer)


suspend fun readImageTiles(file: File, tileWidth:Int, tileHeight:Int) : List<List<Tile>>  {
    val image = ImageIO.read(file)

    if (image.type == TYPE_CUSTOM) {
        println("custom type")
    }

    if (image.type == TYPE_INT_RGB) {
        println("RGB")
    }

    if (image.type == TYPE_INT_ARGB) {
        println("ARGB")
    }

    if (image.type == TYPE_INT_BGR) {
        println("BGR")
    }

    val xTiles = Math.ceil(image.width.toDouble() / (tileWidth)).toInt()
    val yTiles = Math.ceil(image.height.toDouble() / (tileHeight)).toInt()

    val tiles = mutableListOf<MutableList<Tile>>()

    for (y in 0 until yTiles) {
        val row = mutableListOf<Tile>()
        for (x in 0 until xTiles) {

            val xOff = (x * (tileWidth))
            val yOff = (y * (tileHeight))
            val width = Math.min(image.width - xOff, tileWidth)
            val height = Math.min(image.height - yOff, tileHeight)

            val components = 4

            val bb = ByteBuffer.allocateDirect(width * height * components)
            bb.order(ByteOrder.nativeOrder())
            bb.rewind()

            val cb = colorBuffer(width, height, format = when(components) {
                3 -> ColorFormat.RGB
                4 -> ColorFormat.RGBa
                else -> throw IllegalArgumentException("only supporting RGB and RGBa formats")
            })

            for (v in 0 until height) {
                for (u in 0 until width) {
                    val rgb = image.getRGB(xOff + u, yOff + v)
                    bb.putInt(rgb)

                }
            }
            bb.rewind()
            cb.write(bb)
            val tile = Tile(xOff, yOff, cb)
            row.add(tile)
            yield()
        }
        tiles.add(row)
    }
    return tiles
}

fun writeImageTiles(tiles: List<List<Tile>>, file: File, filter:Filter) {
    val totalWidth = tiles[0].sumBy { it.colorBuffer.width }
    val totalHeight = tiles.sumBy { it[0].colorBuffer.height }

    val bi = BufferedImage(totalWidth, totalHeight, TYPE_INT_RGB)

    val tileWidth = tiles[0][0].colorBuffer.width
    val tileHeight = tiles[0][0].colorBuffer.height

    for ((v, row) in tiles.withIndex()) {
        for ((u, tile) in row.withIndex()) {
            val cb: ColorBuffer?
            if (filter != null) {
                cb = colorBuffer(tile.colorBuffer.width, tile.colorBuffer.height, 1.0, ColorFormat.RGB, ColorType.UINT8)
                filter.apply(tile.colorBuffer, cb)
            } else {
                cb = null
            }

            val shad = cb?.shadow ?: tile.colorBuffer.shadow
            shad.download()
            for (y in 0 until tile.colorBuffer.height) {
                for (x in 0 until tile.colorBuffer.width) {
                    val j = tileHeight * v + y
                    val i = tileWidth * u + x
                    val c = shad.read(x, y)
                    val rgb = arrayOf((c.r * 255).toInt(), (c.g * 255).toInt(), (c.b * 255).toInt())
                    bi.setRGB(i, j, rgb[2] + (rgb[1] shl 8) + (rgb[0] shl 16))
                }
            }
            cb?.destroy()
        }
    }
    ImageIO.write(bi,"tif", file)

}


//fun main() {
//    readImage(File("/home/rndr/git/openrndr-tiff/data/test-image-001.tif"))
//
//
//}