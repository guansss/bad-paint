import java.awt.Color
import java.awt.image.DataBufferByte
import java.io.*
import java.lang.Exception
import javax.imageio.ImageIO

const val END_OF_FRAME = 0b10000000.toByte()

fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()

    val cwd = File(if (args.isNotEmpty()) args[0] else ".")

    generateVideoFile(cwd)

    println("\nFinished in ${System.currentTimeMillis() - startTime} ms")
}

fun generateVideoFile(dir: File): File {
    val files = dir.listFiles()
    files.sortBy { it.name }

    val outputFile = File(dir.absolutePath, dir.name + ".bpv")
    val output = BufferedOutputStream(FileOutputStream(outputFile))

    val imageData = ByteArray(CANVAS_COLS * CANVAS_ROWS)
    val fileNumLength = "${files.size}".length

    for (i in 0 until files.size) {
        val file = files[i]
        print("(${"${i + 1}".padStart(fileNumLength)}/${files.size}) ${"${file.name} ".padEnd(30, '.')} ")

        try {
            val data = readImage(file, imageData)
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
 * Known supported image format is JPEG, maybe also PNG?
 * @see <a href="https://stackoverflow.com/a/9470843">Getting image pixels</a>
 */
fun readImage(file: File, imageData: ByteArray): ByteArray {
    val image = ImageIO.read(file) ?: throw Exception("Not an image")
    val imageWidth = image.width

    val scaleX = image.width / CANVAS_COLS.toFloat()
    val scaleY = image.height / CANVAS_ROWS.toFloat()

    val coords = mutableListOf<Byte>()

    val pixels = (image.raster.dataBuffer as DataBufferByte).data
    val pixelLength = if (image.alphaRaster == null) 3 else 4

    for (y in 0 until CANVAS_ROWS) {
        for (x in 0 until CANVAS_COLS) {
            val pixelIndex = ((y * scaleY).toInt() * imageWidth + (x * scaleX).toInt()) * pixelLength +
                    (pixelLength - 3 /* offset the alpha channel */)

            val hsb = Color.RGBtoHSB(
                0xff and pixels[pixelIndex + 2].toInt(),
                0xff and pixels[pixelIndex + 1].toInt(),
                0xff and pixels[pixelIndex].toInt(),
                null
            )
            val brightness = (hsb[2] + 0.5).toByte() // 0 for dark, 1 for bright

            if (imageData[y * CANVAS_COLS + x] != brightness) {
                coords.add(x.toByte())
                coords.add(y.toByte())
            }

            imageData[y * CANVAS_COLS + x] = brightness
        }
    }

    coords.add(END_OF_FRAME)
    coords.add(END_OF_FRAME) // double it to store it like a coordinate pair

    return coords.toByteArray()
}