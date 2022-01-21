package acr.browser.lightning

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import android.content.Intent
import androidx.core.content.ContextCompat.startActivity


class WebAppInterface(private val ctx: Context) {
    @JavascriptInterface
    fun speak(content: String) {
//        Log.d("WebAppInterface", content)
        val i = Intent(Intent.ACTION_SEND)
        i.setClassName("hesoft.T2S", "hesoft.T2S.share2speak.ShareSpeakActivity")
        i.type = "text/plain"
        i.addCategory(Intent.CATEGORY_DEFAULT)
        i.putExtra(Intent.EXTRA_TEXT, content)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}