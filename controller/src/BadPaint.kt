import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.lang.Exception

const val WM_LBUTTONDOWN = 0x0201
const val WM_LBUTTONUP = 0x0202

val WPARAM_MK_LBUTTON = WinDef.WPARAM(0x001L)
val WPARAM_NONE = WinDef.WPARAM(0L)

fun main(args: Array<String>) {
    val hWnd = getWindow("碧蓝航线") ?: throw Exception("Window not found")

    val mu = MuMuWindow(hWnd)

    for (i in 0..10000) {
        mu.click((390..550).random(), (500..590).random())
    }
}

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
    print("DOWN $x $y".padEnd(16, ' '))

//    Thread.sleep(200)
    User32.INSTANCE.PostMessage(hWnd, WM_LBUTTONUP, WPARAM_NONE, makeLParam(x, y))
    println("UP")
}

/**
 * @see <a href="https://stackoverflow.com/q/28431173">StackOverflow</a>
 */
fun makeLParam(loWord: Int, hiWord: Int): WinDef.LPARAM =
    WinDef.LPARAM((hiWord.toLong() shl 16) or (loWord.toLong() and 0xFFFF))

class MuMuWindow(private val hWnd: WinDef.HWND) {
    var scale: Float

    init {
        val rect = WinDef.RECT()
        User32.INSTANCE.GetWindowRect(hWnd, rect)
        scale = (rect.right - rect.left) / 1920f
        println("MuMu scale: $scale")
    }

    fun click(x: Int, y: Int) {
        click(hWnd, (x * scale).toInt(), (35 + y * scale).toInt())
    }
}