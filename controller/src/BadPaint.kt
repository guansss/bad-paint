import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import java.lang.Exception

const val TITLE_BAR_HEIGHT = 35
const val GAME_RESOLUTION_WIDTH = 1920
const val CAPTURE_TIMEOUT = 1500

const val CANVAS_COLS = 37
const val CANVAS_ROWS = 22

val CANVAS_RECT = Rectangle(416, 164, 1110, 660)
val CANVAS_GRID_SIZE = CANVAS_RECT.width / CANVAS_COLS
val CANVAS_ORIGIN = Point(CANVAS_RECT.x + CANVAS_GRID_SIZE / 2, CANVAS_RECT.y + CANVAS_GRID_SIZE / 2)

val CAPTURE_BTN = Point(-235, -30)

val PAINT_BLACK = Paint(0, 1700, 200)
val PAINT_WHITE = Paint(1, 1700, 330)

fun main(args: Array<String>) {
    if (args.isEmpty())
        throw Exception("Argument required")

    val hWnd = getWindow("MuMu", null) ?: throw Exception("Window not found")
    val win = MuMuWindow(hWnd)

    if (args[0] == "reset") {
        win.resetCanvas()
    } else {
        if (args[0].endsWith(".bpv")) {
            play(win, File(args[0]))
        } else throw Exception("A .bpv file is required to play")
    }
}

fun play(win: MuMuWindow, file: File) {
    val data = file.readBytes()

    val canvasData = ByteArray(CANVAS_COLS * CANVAS_ROWS)

    fun printCanvas() {
        for (y in 0 until CANVAS_ROWS) {
            for (x in 0 until CANVAS_COLS) {
                print("${canvasData[y * CANVAS_COLS + x]}".repeat(2))
            }
            println()
        }
    }

    var frame = 1
    val startTime = System.currentTimeMillis()
    var frameTime = startTime

    for (i in 0 until data.size step 2) {
        if (data[i] != END_OF_FRAME) {
            val col = 0xff and data[i].toInt()
            val row = 0xff and data[i + 1].toInt()
            val index = row * CANVAS_COLS + col

            canvasData[index] = (1 - canvasData[index]).toByte()
            win.drawPixel(col, row, canvasData[index])

//            printCanvas()
        } else {
//            println()

            println("Frame ${frame++} (${System.currentTimeMillis() - frameTime} ms)")

            win.capture()

            frameTime = System.currentTimeMillis()
        }
    }

    println("Total (${System.currentTimeMillis() - startTime} ms)")
}

class MuMuWindow(private val hWnd: WinDef.HWND) {
    private val canvasHDC = User32.INSTANCE.GetDC(getWindow("canvas", hWnd))
    private var scale: Float
    private var rect: Rectangle

    private var blackPaint: Paint = PAINT_BLACK
    private var whitePaint: Paint = PAINT_WHITE
    private var paint = blackPaint

    private var lastCaptureTime = 0L

    init {
        val winRect = WinDef.RECT()
        User32.INSTANCE.GetWindowRect(hWnd, winRect)

        rect = Rectangle(winRect.left, winRect.top, winRect.right - winRect.left, winRect.bottom - winRect.top)
        scale = rect.width / GAME_RESOLUTION_WIDTH.toFloat()

        blackPaint = windowToGame(Paint(PAINT_BLACK)) as Paint
        whitePaint = windowToGame(Paint(PAINT_WHITE)) as Paint
        paint = blackPaint
    }

    fun drawPixel(col: Int, row: Int, brightness: Byte) {
        if (brightness != paint.value) {
            // toggle paint between black and white
            paint = if (paint === blackPaint) whitePaint else blackPaint
            click(hWnd, paint)
            Thread.sleep(10)
        }

        val pixel =
            windowToGame(Point(CANVAS_ORIGIN.x + col * CANVAS_GRID_SIZE, CANVAS_ORIGIN.y + row * CANVAS_GRID_SIZE))
        var waitTimes = 0

        while (getBrightness(pixel) != brightness) {
            if (waitTimes-- == 0) {
                // if the pixel still didn't change after checking for 8 times, then redraw it
                waitTimes = 8
                click(hWnd, pixel)
            }
            Thread.sleep(50) // wait for pixel to change
        }
    }

    private fun getBrightness(point: Point): Byte {
        val color = Color(WinGDI.INSTANCE.GetPixel(canvasHDC, point.x, point.y - TITLE_BAR_HEIGHT).toInt())
        return (Color.RGBtoHSB(color.red, color.green, color.blue, null)[2] + 0.5).toByte()
    }

    private fun windowToGame(point: Point): Point {
        point.x = (point.x * scale).toInt()
        point.y = (point.y * scale + TITLE_BAR_HEIGHT).toInt()
        return point
    }

    fun resetCanvas() {
        for (row in 0 until CANVAS_ROWS)
            for (col in 0 until CANVAS_COLS)
                drawPixel(col, row, blackPaint.value)
    }

    fun capture() {
        val dt = CAPTURE_TIMEOUT - (System.currentTimeMillis() - lastCaptureTime)

        // wait for capture to finish
        if (dt > 0) Thread.sleep(dt)
        lastCaptureTime = System.currentTimeMillis()

        click(hWnd, Point(rect.width + CAPTURE_BTN.x, rect.height + CAPTURE_BTN.y))
    }
}

class Paint(val value: Byte, x: Int, y: Int) : Point(x, y) {
    constructor(paint: Paint) : this(paint.value, paint.x, paint.y)
}

const val WM_LBUTTONDOWN = 0x0201
const val WM_LBUTTONUP = 0x0202

val WPARAM_MK_LBUTTON = WinDef.WPARAM(0x001L)
val WPARAM_NONE = WinDef.WPARAM(0L)
val LPARAM_NONE = WinDef.LPARAM(0L)

fun getWindow(name: String, parent: WinDef.HWND?): WinDef.HWND? {
    var hWnd: WinDef.HWND? = null

    val callback = WinUser.WNDENUMPROC { _hWnd, _ ->
        try {
            val nameArray = CharArray(512)
            val length = User32.INSTANCE.GetWindowText(_hWnd, nameArray, nameArray.size)
            val winName = String(nameArray.sliceArray(0 until length))

            if (winName.contains(name)) {
                println("Window found: [$winName] $_hWnd")
                hWnd = _hWnd
                return@WNDENUMPROC false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        true
    }

    User32.INSTANCE.EnumChildWindows(parent, callback, null)

    return hWnd
}

fun click(hWnd: WinDef.HWND, point: Point) {
    val lParam = makeLParam(point.x, point.y)
    User32.INSTANCE.PostMessage(hWnd, WM_LBUTTONDOWN, WPARAM_MK_LBUTTON, lParam)
//    print("DOWN $x $y".padEnd(16, ' '))

//    Thread.sleep(1000)
    User32.INSTANCE.PostMessage(hWnd, WM_LBUTTONUP, WPARAM_NONE, lParam)
//    println("UP")
}

/**
 * @see <a href="https://stackoverflow.com/q/28431173">StackOverflow</a>
 */
fun makeLParam(loWord: Int, hiWord: Int): WinDef.LPARAM =
    WinDef.LPARAM((hiWord.toLong() shl 16) or (loWord.toLong() and 0xFFFF))
