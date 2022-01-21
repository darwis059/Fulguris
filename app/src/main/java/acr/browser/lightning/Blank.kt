package acr.browser.lightning

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button

class Blank : AppCompatActivity() {
    private lateinit var btnStart: Button
    private lateinit var btnBack: Button
//    private lateinit var am: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blank)

        val screenActionReceiver = ScreenActionReceiver()
        registerReceiver(screenActionReceiver, screenActionReceiver.getFilter())

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
//        val sens = getSystemService(SENSOR_SERVICE) as SensorManager

        btnStart = findViewById(R.id.btnStart)
        btnStart.setOnClickListener{ openNewActivity(am) }
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

    override fun onBackPressed() {
//        super.onBackPressed()
    }

    private fun openNewActivity(am: AudioManager){
        val volLevel: Int = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
//        val proximity = sens.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return

        if (volLevel == 0) {
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) - 4, AudioManager.FLAG_SHOW_UI)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
}