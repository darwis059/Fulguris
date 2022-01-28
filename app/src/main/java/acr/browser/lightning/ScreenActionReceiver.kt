package acr.browser.lightning

import acr.browser.lightning.settings.activity.SettingsActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
//import android.content.IntentFilter
//import android.util.Log
import androidx.core.content.ContextCompat.startActivity

//class ScreenActionReceiver: BroadcastReceiver() {
private const val TAG = "MyBroadcastReceiver"

class ScreenActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
//        StringBuilder().apply {
//            append("Action: ${intent.action}\n")
//            append("URI: ${intent.toUri(Intent.URI_INTENT_SCHEME)}\n")
//            toString().also { log ->
//                Log.d(TAG, log)
//                Toast.makeText(context, log, Toast.LENGTH_LONG).show()
//            }
//        }
        fun openMainActivity() {
            context.startActivity(Intent(context, Blank::class.java).addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            if (BrowserApp.goHome) {
                context.startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) })
            }
        }
        val action = intent.action

        if (Intent.ACTION_SCREEN_ON == action) {
            openMainActivity()
        }

        if (Intent.ACTION_SCREEN_OFF == action) {
//            context.startActivity(Intent(context, SettingsActivity::class.java))
            openMainActivity()
        }

        if (Intent.ACTION_USER_PRESENT == action) {
//            context.startActivity(Intent(context, SettingsActivity::class.java))
            openMainActivity()
        }
    }

    fun getFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        return filter
    }

//    fun openMainActivity() {
//        val intent = Intent(this, SettingsActivity::class.java)
//        startActivity(intent)
//    }
}