package guansss.badpaint

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.IInterface
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

import java.lang.reflect.Method

@SuppressLint("PrivateApi")
object InputManager {
    private var manager: IInterface? = null
    private var injectInputEventMethod: Method? = null

    private val pointerProperties = arrayOf(MotionEvent.PointerProperties())
    private val pointerCoords = arrayOf(MotionEvent.PointerCoords())

    init {
        // The original version of following code comes from project scrcpy (https://github.com/Genymobile/scrcpy)
        try {
            val getServiceMethod = Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "input") as IBinder
            val asInterfaceMethod = Class.forName("android.hardware.input.IInputManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)

            manager = asInterfaceMethod.invoke(null, binder) as IInterface
            injectInputEventMethod = manager?.javaClass?.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.java
            )

            val props = pointerProperties[0]
            props.id = 0
            props.toolType = MotionEvent.TOOL_TYPE_FINGER
        } catch (e: Exception) {
            Log.w("InputManager", "Failed to init")
            e.printStackTrace()
        }
    }

    fun injectInputEvent(inputEvent: InputEvent): Boolean {
        try {
            Log.i("injectInputEvent", inputEvent.toString())
            return injectInputEventMethod?.invoke(manager, inputEvent, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */) as Boolean
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun tap(x: Float, y: Float) {
        val coords = pointerCoords[0]
        coords.x = x
        coords.y = y

        val downTime = SystemClock.uptimeMillis()
        InputManager.injectInputEvent(
            MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                1,
                pointerProperties,
                pointerCoords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
        )

        Thread.sleep(100)

        InputManager.injectInputEvent(
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                1,
                pointerProperties,
                pointerCoords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
        )
    }
}