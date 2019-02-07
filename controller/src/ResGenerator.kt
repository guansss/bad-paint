import java.awt.Color
import java.awt.image.DataBufferByte
import java.io.*
import java.lang.Exception
import javax.imageio.ImageIO

const val FRAME_WIDTH = 37
const val FRAME_HEIGHT = 22

fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()

    val cwd = File(if (args.isNotEmpty()) args[0] else ".")

    val outputFile = generateVideoFile(cwd)
    val data = outputFile.readBytes()

    var x = 0
    var y = 0

    for (i in 0 until data.size) {
        val dataByte = data[i]

        for (bit in 7 downTo 0) {
            print("${(dataByte.toInt() shr bit) and 1}".repeat(2))

            if (++x >= FRAME_WIDTH) {
                x = 0
                println()

                if (++y >= FRAME_HEIGHT) {
                    y = 0
                    println()
                    break
                }
            }
        }
    }

    println("\nFinished in ${System.currentTimeMillis() - startTime} ms")
}

fun generateVideoFile(dir: File): File {
    val files = dir.listFiles()
    files.sortBy { it.name }

    val outputFile = File(dir.absolutePath, dir.name + ".bpv")
    val output = BufferedOutputStream(FileOutputStream(outputFile))

    val fileNumLength = "${files.size}".length

    for (i in 0 until files.size) {
        val file = files[i]
        print("(${"${i + 1}".padStart(fileNumLength)}/${files.size}) ${"${file.name} ".padEnd(30, '.')} ")

        try {
            val data = readImage(file)
            output.write(data)
            println("done")
        } catch (e: Exception) {
            println("error: $e")
        }
    }

    output.close()

    println("\nOutput video file: ${outputFile.absolutePath} (${outputFile.length()} Bytes)")

    return outputFile
}

/**
 * This method may not work on all image types (or formats),
 * because supporting other types will require extra works...
 * @see <a href="https://stackoverflow.com/a/9470843">Getting image pixels</a>
 */
fun readImage(file: File): ByteArray {
    val image = ImageIO.read(file) ?: throw Exception("Not an image")
    val imageWidth = image.width

    val scaleX = image.width / FRAME_WIDTH
    val scaleY = image.height / FRAME_HEIGHT

    val data = ByteArray(Math.ceil(FRAME_WIDTH * FRAME_HEIGHT / (8.0 /* bit */)).toInt())

    val pixels = (image.raster.dataBuffer as DataBufferByte).data
    val pixelLength = if (image.alphaRaster == null) 3 else 4

    var dataByte = 0
    var bit = 0
    var i = 0

    for (y in 0 until FRAME_HEIGHT) {
        for (x in 0 until FRAME_WIDTH) {
            val pixelIndex = (y * scaleY * imageWidth + x * scaleX) * pixelLength +
                    (pixelLength - 3 /* offset the alpha channel */)

            val hsb = Color.RGBtoHSB(
                0xff and pixels[pixelIndex + 2].toInt(),
                0xff and pixels[pixelIndex + 1].toInt(),
                0xff and pixels[pixelIndex].toInt(),
                null
            )
            val brightness = (hsb[2] + 0.5).toInt() // 0 for dark, 1 for bright

            dataByte = dataByte or (brightness shl (7 - bit))

            if (++bit == 8) {
                bit = 0
                data[i++] = dataByte.toByte()
                dataByte = 0
            }
        }
    }

    if (i < data.size) {
        data[i] = dataByte.toByte()
    }

//    var dataByte = 0
//    var bit = 0
//    var i = 0
//    for (y in 0 until FRAME_HEIGHT) {
//        for (x in 0 until FRAME_WIDTH) {
//            val color = Color(image.getRGB(x * scaleX, y * scaleY))
//            val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
//
//            dataByte = dataByte or ((hsb[2] + 0.5).toInt() shl (7 - bit))
//
//            val pixelIndex = (i + bit) * pixelLength
//
//            if (++bit == 8) {
//                bit = 0
//                data[i++] = dataByte.toByte()
//                dataByte = 0
//            }
//        }
//    }
//    data[i] = dataByte.toByte()

    return data
}