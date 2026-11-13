package ir.tipaxrezashahr.activator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * فعال‌ساز تیپاکس (نسخه‌ی اپ ابزار فعال‌سازی گوشی‌ها) - کاملاً آفلاین.
 * «کد گوشی» (مثل A1B2-C3D4) که از اپ «ثبت مرسولات تیپاکس» گرفته شده رو
 * وارد می‌کنی و «کد فعال‌سازی» ۶ رقمیِ همون گوشی رو می‌گیری.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var input: EditText
    private lateinit var errorText: TextView
    private lateinit var resultBox: View
    private lateinit var codeText: TextView
    private var editing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.deviceCodeInput)
        errorText = findViewById(R.id.errorText)
        resultBox = findViewById(R.id.resultBox)
        codeText = findViewById(R.id.codeText)
        findViewById<TextView>(R.id.versionText).text =
            "نسخه " + try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (editing) return
                editing = true
                // حروف بزرگ + خط تیره‌ی خودکار وسط کد
                val clean = s.toString().uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(8)
                val fmt = if (clean.length > 4) clean.substring(0, 4) + "-" + clean.substring(4) else clean
                if (fmt != s.toString()) {
                    input.setText(fmt)
                    input.setSelection(fmt.length)
                }
                editing = false
                resultBox.visibility = View.GONE
                errorText.text = ""
            }
        })

        findViewById<View>(R.id.generateButton).setOnClickListener { generate() }
        findViewById<View>(R.id.copyButton).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("کد فعال‌سازی", codeText.text))
            Toast.makeText(this, "کد کپی شد", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.shareButton).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "کد فعال‌سازی اپ: " + codeText.text)
            }
            startActivity(Intent.createChooser(send, "ارسال کد فعال‌سازی"))
        }
    }

    private fun generate() {
        errorText.text = ""
        resultBox.visibility = View.GONE
        val raw = input.text.toString().trim().uppercase()
        if (!Regex("^[0-9A-F]{4}-[0-9A-F]{4}$").matches(raw)) {
            errorText.text = "کد گوشی باید به شکل XXXX-XXXX باشه (۸ حرف/عدد انگلیسی با یه خط تیره وسط)."
            return
        }
        codeText.text = activationCodeFor(raw)
        resultBox.visibility = View.VISIBLE
    }

    companion object {
        // همین رشته باید عیناً با ACTIVATION_SECRET تو اپ اصلی (LockActivity.kt) یکی باشه.
        private const val ACTIVATION_SECRET = "tipax-rezashahr-excelfiller-activation-secret-2026"

        fun activationCodeFor(deviceCode: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(ACTIVATION_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val result = mac.doFinal(deviceCode.toByteArray(Charsets.UTF_8))
            val num = BigInteger(1, result).mod(BigInteger.valueOf(1_000_000))
            return num.toString().padStart(6, '0')
        }
    }
}
