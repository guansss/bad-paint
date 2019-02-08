import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import java.lang.Exception

const val CANVAS_COLS = 37
const val CANVAS_ROWS = 22

val CANVAS_RECT = Rectangle(416, 164, 1110, 660)
val PAINT_BLACK = Point(1700, 200)
val PAINT_WHITE = Point(1700, 330)

fun main(args: Array<String>) {
    if (args.isEmpty() || !args[0].endsWith(".bpv"))
        throw Exception("A .bpv file is required to play")

    val hWnd = getWindow("MuMu") ?: throw Exception("Window not found")

    play(MuMuWindow(hWnd), File(args[0]))
}

fun play(win: Window, file: File) {
    val gridSize = CANVAS_RECT.width / CANVAS_COLS

    val minX = CANVAS_RECT.x + gridSize / 2
    val minY = CANVAS_RECT.y + gridSize / 2
    val width = CANVAS_RECT.width
    val height = CANVAS_RECT.height

    val data = file.readBytes()
    var x = 0
    var y = 0
    var paint = PAINT_BLACK

    for (i in 0 until CANVAS_COLS * CANVAS_ROWS / 8 + 1) {
        val dataByte = data[i]

        for (bit in 7 downTo 0) {
            print("${(dataByte.toInt() shr bit) and 1}".repeat(2))

            val curPaint: Point = if ((dataByte.toInt() shr bit) and 1 == 0) PAINT_BLACK else PAINT_WHITE

            if (curPaint !== paint) {
                win.click(curPaint.x, curPaint.y)
                paint = curPaint
            }

            win.click(minX + x, minY + y)
            Thread.sleep(200)

            x += gridSize

            if (x >= width) {
                x = 0
                y += gridSize
                println()

                if (y >= height) {
                    y = 0
                    println()
                    break // break to prevent outputting overflowed bits
                }
            }
        }
    }
}

interface Window {
    fun click(x: Int, y: Int)
}

class MuMuWindow(private val hWnd: WinDef.HWND) : Window {
    var scale: Float

    init {
        val rect = WinDef.RECT()
        User32.INSTANCE.GetWindowRect(hWnd, rect)
        scale = (rect.right - rect.left) / 1920f
        println("MuMu scale: $scale")
    }

    override fun click(x: Int, y: Int) {
        click(hWnd, (x * scale).toInt(), (35 + y * scale).toInt())
    }
}

const val WM_LBUTTONDOWN = 0x0201
const val WM_LBUTTONUP = 0x0202

val WPARAM_MK_LBUTTON = WinDef.WPARAM(0x001L)
val WPARAM_NONE = WinDef.WPARAM(0L)

fun getWindow(name: String): WinDef.HWND? {
    var hWnd: WinDef.HWND? = null

    User32.INSTANCE.EnumWindows({ _hWnd, _ ->
        try {
            val nameArray = CharArray(512)
            User32.INSTANCE.GetWindowText(_hWnd, nameArray, nameArray.size)
            val winName = String(nameArray.sliceArray(0 until nameArray.indexOf('\u0000')))

            if (winName.contains(name)) {
                println("Window found: [$winName] $_hWnd")
                hWnd = _hWnd
                return@EnumWindows false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        true
    }, null)

    return hWnd
}

fun click(hWnd: WinDef.HWND, x: Int, y: Int) {
    User32.INSTANCE.PostMessage(hWnd, WM_LBUTTONDOWN, WPARAM_MK_LBUTTON, makeLParam(x, y))
//    print("DOWN $x $y".padEnd(16, ' '))

//    Thread.sleep(1000)
    User32.INSTANCE.PostMessage(hWnd, WM_LBUTTONUP, WPARAM_NONE, makeLParam(x, y))
//    println("UP")
}

/**
 * @see <a href="https://stackoverflow.com/q/28431173">StackOverflow</a>
 */
fun makeLParam(loWord: Int, hiWord: Int): WinDef.LPARAM =
    WinDef.LPARAM((hiWord.toLong() shl 16) or (loWord.toLong() and 0xFFFF))
