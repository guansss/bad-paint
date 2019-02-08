import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import java.lang.Exception

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

    println("Total (${System.currentTimeMillis() - startTime})")
}

class MuMuWindow(private val hWnd: WinDef.HWND) {
    private val canvasHWnd = getWindow("canvas", hWnd)
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
        scale = rect.width / 1920f

        blackPaint = windowToScreen(Paint(PAINT_BLACK)) as Paint
        whitePaint = windowToScreen(Paint(PAINT_WHITE)) as Paint
        paint = blackPaint
    }

    fun drawPixel(col: Int, row: Int, brightness: Byte) {
        if (brightness != paint.value) {
            // toggle paint between black and white
            paint = if (paint === blackPaint) whitePaint else blackPaint
            click(hWnd, paint)
        }

        val pixel =
            windowToScreen(Point(CANVAS_ORIGIN.x + col * CANVAS_GRID_SIZE, CANVAS_ORIGIN.y + row * CANVAS_GRID_SIZE))

        while (getBrightness(pixel) != brightness) {
            click(hWnd, pixel)
            Thread.sleep(250)
        }
    }

    private fun getBrightness(point: Point): Byte {
        val hdc = User32.INSTANCE.GetDC(canvasHWnd)
        val color = Color(WinGDI.INSTANCE.GetPixel(hdc, point.x, point.y - 35).toInt())
        User32.INSTANCE.ReleaseDC(canvasHWnd, hdc)
        return (Color.RGBtoHSB(color.red, color.green, color.blue, null)[2] + 0.5).toByte()
    }

    private fun windowToScreen(point: Point): Point {
        point.x = (point.x * scale).toInt()
        point.y = (point.y * scale + 35).toInt() // offset 35 pixels of title bar
        return point
    }

    fun resetCanvas() {
        for (row in 0 until CANVAS_ROWS)
            for (col in 0 until CANVAS_COLS)
                drawPixel(col, row, blackPaint.value)
    }

    fun capture() {
        // wait 1.5 second for capture to finish
        val dt = 1500 - (System.currentTimeMillis() - lastCaptureTime)

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

    fun callback(_hWnd: WinDef.HWND?, pointer: Pointer?): Boolean {
        try {
            val nameArray = CharArray(512)
            User32.INSTANCE.GetWindowText(_hWnd, nameArray, nameArray.size)
            val winName = String(nameArray.sliceArray(0 until nameArray.indexOf('\u0000')))

            if (winName.contains(name)) {
                println("Window found: [$winName] $_hWnd")
                hWnd = _hWnd
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    if (parent == null)
        User32.INSTANCE.EnumWindows(::callback, null)
    else
        User32.INSTANCE.EnumChildWindows(parent, ::callback, null)

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
