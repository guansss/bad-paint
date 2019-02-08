import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Rectangle
import java.io.File
import java.lang.Exception

const val CANVAS_COLS = 37
const val CANVAS_ROWS = 22

val CANVAS_RECT = Rectangle(416, 164, 1110, 660)
val PAINT_BLACK = Paint(0, 1700, 200)
val PAINT_WHITE = Paint(1, 1700, 330)

fun main(args: Array<String>) {
    if (args.isEmpty())
        throw Exception("Argument required")

    val hWnd = getWindow("MuMu") ?: throw Exception("Window not found")
    val muMuWin = MuMuWindow(hWnd)

    if (args[0] == "reset") {
        reset(muMuWin)
    } else {
        if (args[0].endsWith(".bpv")) {
            play(muMuWin, File(args[0]))
        } else throw Exception("A .bpv file is required to play")
    }
}

fun reset(win: Window) {
    val gridSize = CANVAS_RECT.width / CANVAS_COLS
    val minX = CANVAS_RECT.x + gridSize / 2
    val minY = CANVAS_RECT.y + gridSize / 2

    for (y in 0 until CANVAS_ROWS * gridSize step gridSize)
        for (x in 0 until CANVAS_COLS * gridSize step gridSize)
            win.click(minX + x, minY + y)
}

fun play(win: Window, file: File) {
    val gridSize = CANVAS_RECT.width / CANVAS_COLS

    val minX = CANVAS_RECT.x + gridSize / 2
    val minY = CANVAS_RECT.y + gridSize / 2

    val data = file.readBytes()

    val canvasData = ByteArray(CANVAS_COLS * CANVAS_ROWS)
    var paint = PAINT_BLACK

    fun printCanvas() {
        for (y in 0 until CANVAS_ROWS) {
            for (x in 0 until CANVAS_COLS) {
                print("${canvasData[y * CANVAS_COLS + x]}".repeat(2))
            }
            println()
        }
    }

    for (i in 0 until data.size step 2) {
        if (data[i] != END_OF_FRAME) {
            val x = 0xff and data[i].toInt()
            val y = 0xff and data[i + 1].toInt()
            val index = y * CANVAS_COLS + x

            canvasData[index] = (1 - canvasData[index]).toByte()

            if (canvasData[index] != paint.value) {
                // toggle paint between black and white
                paint = if (paint === PAINT_BLACK) PAINT_WHITE else PAINT_BLACK
                win.click(paint.x, paint.y)
            }

            win.click(minX + x * gridSize, minY + y * gridSize)
            printCanvas()
        }
    }
}

interface Window {
    fun click(x: Int, y: Int)
}

class MuMuWindow(private val hWnd: WinDef.HWND) : Window {
    private var scale: Float

    init {
        val rect = WinDef.RECT()
        User32.INSTANCE.GetWindowRect(hWnd, rect)
        scale = (rect.right - rect.left) / 1920f
        println("MuMu scale: $scale")
    }

    override fun click(x: Int, y: Int) {
        click(hWnd, (x * scale).toInt(), (35 + y * scale).toInt())
        Thread.sleep(200)
    }
}

data class Paint(val value: Byte, val x: Int, val y: Int)

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
