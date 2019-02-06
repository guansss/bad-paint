package guansss.badpaint

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.TextView

class MainActivity : Activity() {
    private var alive = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.root).setOnTouchListener { view, motionEvent ->
            Log.i("touch", motionEvent.toString())
            true
        }

        findViewById<TextView>(R.id.text).setOnClickListener { view ->
            (view as TextView).text = "" + SystemClock.uptimeMillis()
        }

        findViewById<View>(R.id.text).setOnTouchListener { view, motionEvent ->
            Log.i("touch text", motionEvent.toString())
            false
        }

        alive = true

        Thread {
            while (alive) {
                InputManager.tap(530f, 1160f)
                Thread.sleep(1000)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        alive = false
    }
}
