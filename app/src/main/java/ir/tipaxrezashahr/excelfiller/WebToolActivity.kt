package ir.tipaxrezashahr.excelfiller

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * ابزارهای HTML آفلاین (چرم دقت، تلگرام جیپ، جیپ) رو داخل خود اپ باز می‌کنه.
 * انتخاب فایل اکسل ورودی با انتخابگر فایل گوشی انجام می‌شه و فایل اکسل خروجی
 * بعد از زدن لینک دانلود، مستقیم به صفحه‌ی اشتراک‌گذاری می‌ره.
 */
class WebToolActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = filePathCallback
        filePathCallback = null
        if (cb == null) return@registerForActivityResult
        val data = res.data
        val uris: Array<Uri>? = if (res.resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            when {
                clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        cb.onReceiveValue(uris)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val asset = intent.getStringExtra(EXTRA_ASSET) ?: run { finish(); return }
        title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // کلیک روی لینک‌های دانلود (blob) رو می‌گیره و فایل رو به اپ می‌ده.
                view.evaluateJavascript(HOOK_JS, null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel.sheet.macroEnabled.12",
                            "application/octet-stream"
                        )
                    )
                }
                return try {
                    picker.launch(Intent.createChooser(i, "انتخاب فایل اکسل"))
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        val html = assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        // آدرس پایه‌ی https باعث می‌شه لینک‌های blob و CompressionStream درست کار کنن.
        web.loadDataWithBaseURL("https://appassets.local/", html, "text/html", "UTF-8", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    inner class Bridge {
        @JavascriptInterface
        fun saveBase64(b64: String, name: String) {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val dir = File(filesDir, "exports").apply { mkdirs() }
                val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "tipax.xlsx" }
                val f = File(dir, "${System.currentTimeMillis()}_" + (if (safe.endsWith(".xlsx")) safe else "$safe.xlsx"))
                f.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(this@WebToolActivity, "ir.tipaxrezashahr.excelfiller.fileprovider", f)
                runOnUiThread {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(Intent.createChooser(send, "اشتراک‌گذاری فایل اکسل"))
                    } catch (e: Exception) {
                        Toast.makeText(this@WebToolActivity, "هیچ اپی برای اشتراک‌گذاری پیدا نشد.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@WebToolActivity, "خطا در ذخیره فایل: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    companion object {
        const val EXTRA_ASSET = "asset"
        const val EXTRA_TITLE = "title"

        private const val HOOK_JS = """
(function(){
  if (window.__tipaxHook) return; window.__tipaxHook = true;
  document.addEventListener('click', function(e){
    var a = e.target.closest ? e.target.closest('a[download]') : null;
    if (!a || !a.href || a.href.indexOf('blob:') !== 0) return;
    e.preventDefault(); e.stopPropagation();
    var name = a.getAttribute('download') || 'tipax.xlsx';
    fetch(a.href).then(function(r){return r.blob();}).then(function(b){
      var fr = new FileReader();
      fr.onload = function(){ AndroidBridge.saveBase64(String(fr.result).split(',')[1], name); };
      fr.readAsDataURL(b);
    });
  }, true);
})();
"""
    }
}
