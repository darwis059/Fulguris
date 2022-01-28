package acr.browser.lightning

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.util.*
import kotlin.concurrent.schedule

class Blank : AppCompatActivity() {
    private lateinit var btnStart: Button
    private lateinit var btnBack: Button
    private lateinit var am: AudioManager
    private lateinit var txt: TextView
    private var pin: String = ""
//    private lateinit var am: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blank)

        val screenActionReceiver = ScreenActionReceiver()
        registerReceiver(screenActionReceiver, screenActionReceiver.getFilter())

        am = getSystemService(AUDIO_SERVICE) as AudioManager
//        val sens = getSystemService(SENSOR_SERVICE) as SensorManager
        txt = findViewById(R.id.textView)
        txt.setOnClickListener {
            txt.text = "Loading ..."
            pin = ""
            findViewById<Button>(R.id.btn5).setTextColor(Color.parseColor("#FFFFFF"))
//            Handler().postDelayed(findViewById<Button>(R.id.btn5).setTextColor(Color.parseColor(R.color.white.toString())),1000)
            Timer().schedule(1000) {
                findViewById<Button>(R.id.btn5).setTextColor(Color.parseColor("#80353535"))
            }
        }
//        txt.setOnTouchListener { v, event ->
//            val action = event.action
//            when (action) {
//                MotionEvent.ACTION_DOWN -> findViewById<Button>(R.id.btn5).setTextColor(Color.parseColor(R.color.white.toString()))
//                MotionEvent.ACTION_UP -> findViewById<Button>(R.id.btn5).setTextColor(0)
//            }
//            true
//        }
        btnStart = findViewById(R.id.btnStart)
        btnStart.setOnClickListener{ openNewActivity() }
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
//            val i = Intent(Intent.ACTION_SEND)
//            i.setClassName("hesoft.T2S", "hesoft.T2S.share2speak.ShareSpeakActivity")
//            i.type = "text/plain"
//            i.addCategory(Intent.CATEGORY_DEFAULT)
//            i.putExtra(Intent.EXTRA_TEXT, "back")
//            startActivity(i)
            if(am.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) {
                if (!isTaskRoot) {
                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) - 4, AudioManager.FLAG_SHOW_UI)
                    finish()
                }
            }
        }
    }

    fun btnClicked(view: View) {
        when (view.id) {
            R.id.btn0 -> setPin("0")
            R.id.btn1 -> setPin("1")
            R.id.btn2 -> setPin("2")
            R.id.btn3 -> setPin("3")
            R.id.btn4 -> setPin("4")
            R.id.btn5 -> setPin("5")
            R.id.btn6 -> setPin("6")
            R.id.btn7 -> setPin("7")
            R.id.btn8 -> setPin("8")
            R.id.btn9 -> setPin("9")
        }
    }

    private fun setPin(p: String) {
        pin += p
        Log.d("pin", pin)
        when (pin.length) {
            0 -> txt.text = "Loading ..."
            1 -> txt.text = "Loading ...."
            2 -> txt.text = "Loading ....."
            3 -> txt.text = "Loading ......"
            else -> {
                txt.text = "Loading ....... "
                val now = Calendar.getInstance()
                val h = now.get(Calendar.HOUR_OF_DAY)
                val m = now.get(Calendar.MINUTE) + 1
                val pwd = StringBuilder().append(h.toString().padStart(2,'0')).append(m.toString().padStart(2,'0')).toString()
                val pwd2 = StringBuilder().append(h.plus(1).toString().padStart(2,'0')).append(m.toString().padStart(2,'0')).toString()
//                Log.d("pin", pin)
//                Log.d("pwd", pwd)
                if (pwd2 == pin) {
                    BrowserApp.goHome = false
                }
                if (pwd == pin || pwd2 == pin) {
                    if (!isTaskRoot) {
                        finish()
                    } else {
                        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_SHOW_UI)
                        openNewActivity()
                    }
                }
                pin = ""
                txt.text = "Loading ..."
            }
        }
//        if (pin.length>3) {
//            val now = Calendar.getInstance()
//            val h = now.get(Calendar.HOUR_OF_DAY) + 1
//            val m = now.get(Calendar.MINUTE) + 1
//            val pwd = StringBuilder().append(h.toString().padStart(2,'0')).append(m.toString().padStart(2,'0')).toString()
//            Log.d("pin", pin)
//            Log.d("pwd", pwd)
//            if (pwd == pin) {
//                if (!isTaskRoot) {
//                    finish()
//                } else {
//                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_SHOW_UI)
//                    openNewActivity()
//                }
//            }
//            pin = ""
//        }

    }

    override fun onBackPressed() {
//        super.onBackPressed()
    }

    private fun openNewActivity(){
        val volLevel: Int = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
//        val proximity = sens.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return

        if (volLevel == 0) {
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) - 4, AudioManager.FLAG_SHOW_UI)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
}