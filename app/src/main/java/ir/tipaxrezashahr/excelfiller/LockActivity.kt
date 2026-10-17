package ir.tipaxrezashahr.excelfiller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * صفحه‌ی ورود اپ، سه مرحله:
 *
 * ۱) فعال‌سازی گوشی - بار اولی که اپ رو رو یه گوشی جدید نصب و باز می‌کنن،
 *    یه «کد گوشی» منحصربه‌فرد نشون داده می‌شه. بدون یه «کد فعال‌سازی» که فقط
 *    خود احسان (با ابزار جداگانه‌ی activation_tool.html) می‌تونه از روی این
 *    کد گوشی بسازه، اپ اصلاً باز نمی‌شه - یعنی هر کسی فایل APK رو هم داشته
 *    باشه، بدون تایید احسان نمی‌تونه ازش استفاده کنه.
 * ۲) بعد از فعال‌سازی، تعریف یه رمز شخصی (مثل قبل).
 * ۳) دفعات بعدی فقط همون رمز شخصی لازمه (فعال‌سازی یه‌بار برای همیشه‌ست،
 *    مگر این‌که اپ پاک و دوباره نصب بشه).
 */
class LockActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var subtitleText: TextView
    private lateinit var deviceCodeText: TextView
    private lateinit var deviceCodeHintText: TextView
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmInput: TextInputEditText
    private lateinit var errorText: TextView
    private lateinit var actionButton: MaterialButton

    private enum class Mode { ACTIVATION, SETUP_PASSWORD, LOGIN }
    private lateinit var mode: Mode
    private lateinit var myDeviceCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        subtitleText = findViewById(R.id.lockSubtitleText)
        deviceCodeText = findViewById(R.id.deviceCodeText)
        deviceCodeHintText = findViewById(R.id.deviceCodeHintText)
        confirmLayout = findViewById(R.id.confirmLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        passwordInput = findViewById(R.id.passwordInput)
        confirmInput = findViewById(R.id.confirmInput)
        errorText = findViewById(R.id.lockErrorText)
        actionButton = findViewById(R.id.lockActionButton)

        myDeviceCode = deviceCode(this)
        val activatedFor = prefs.getString(KEY_ACTIVATED_DEVICE, null)
        val hasPassword = prefs.getString(KEY_PWD_HASH, null) != null

        mode = when {
            activatedFor != myDeviceCode -> Mode.ACTIVATION
            !hasPassword -> Mode.SETUP_PASSWORD
            else -> Mode.LOGIN
        }

        setupForMode()

        actionButton.setOnClickListener {
            when (mode) {
                Mode.ACTIVATION -> handleActivation()
                Mode.SETUP_PASSWORD -> handleSetupPassword()
                Mode.LOGIN -> handleLogin()
            }
        }
    }

    private fun setupForMode() {
        when (mode) {
            Mode.ACTIVATION -> {
                deviceCodeText.visibility = View.VISIBLE
                deviceCodeHintText.visibility = View.VISIBLE
                deviceCodeText.text = myDeviceCode
                deviceCodeText.setOnLongClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("کد گوشی", myDeviceCode))
                    Toast.makeText(this, "کد کپی شد", Toast.LENGTH_SHORT).show()
                    true
                }
                subtitleText.text = "این اپ باید توسط احسان فعال بشه. کد بالا رو براش بفرست و کد فعال‌سازی ۶ رقمی که برات می‌فرسته رو اینجا وارد کن."
                confirmLayout.visibility = View.GONE
                passwordLayout.hint = "کد فعال‌سازی (۶ رقم)"
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                passwordInput.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
                actionButton.text = "فعال‌سازی"
            }
            Mode.SETUP_PASSWORD -> {
                deviceCodeText.visibility = View.GONE
                deviceCodeHintText.visibility = View.GONE
                subtitleText.text = "گوشی فعال شد. حالا یه رمز برای خودت تعریف کن تا بقیه نتونن بدون اجازه از اپ استفاده کنن."
                confirmLayout.visibility = View.VISIBLE
                passwordLayout.hint = "رمز عبور"
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                passwordInput.filters = arrayOf()
                actionButton.text = "تنظیم رمز و ادامه"
            }
            Mode.LOGIN -> {
                deviceCodeText.visibility = View.GONE
                deviceCodeHintText.visibility = View.GONE
                subtitleText.text = "برای ادامه، رمز عبور اپ رو وارد کن."
                confirmLayout.visibility = View.GONE
                passwordLayout.hint = "رمز عبور"
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                passwordInput.filters = arrayOf()
                actionButton.text = "ورود"
            }
        }
    }

    private fun handleActivation() {
        errorText.text = ""
        val entered = passwordInput.text?.toString()?.trim().orEmpty()
        val expected = activationCodeFor(myDeviceCode)
        if (entered == expected) {
            prefs.edit().putString(KEY_ACTIVATED_DEVICE, myDeviceCode).apply()
            Toast.makeText(this, "گوشی فعال شد.", Toast.LENGTH_SHORT).show()
            mode = if (prefs.getString(KEY_PWD_HASH, null) == null) Mode.SETUP_PASSWORD else Mode.LOGIN
            passwordInput.text?.clear()
            setupForMode()
        } else {
            errorText.text = "کد فعال‌سازی اشتباهه. مطمئن شو همون کدی که احسان برای این گوشی فرستاده رو وارد کردی."
        }
    }

    private fun handleSetupPassword() {
        errorText.text = ""
        val p1 = passwordInput.text?.toString().orEmpty()
        val p2 = confirmInput.text?.toString().orEmpty()
        if (p1.length < 4) {
            errorText.text = "رمز باید حداقل ۴ کاراکتر باشه"
            return
        }
        if (p1 != p2) {
            errorText.text = "رمز و تکرارش یکی نیستن"
            return
        }
        prefs.edit().putString(KEY_PWD_HASH, hash(p1)).apply()
        proceed()
    }

    private fun handleLogin() {
        errorText.text = ""
        val entered = passwordInput.text?.toString().orEmpty()
        val stored = prefs.getString(KEY_PWD_HASH, null)
        if (stored != null && stored == hash(entered)) {
            proceed()
        } else {
            errorText.text = "رمز اشتباهه، دوباره امتحان کن"
            passwordInput.text?.clear()
        }
    }

    private fun proceed() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val PREFS_NAME = "excelfiller_lock_prefs"
        const val KEY_PWD_HASH = "pwd_hash"
        const val KEY_ACTIVATED_DEVICE = "activated_device"

        private const val PASSWORD_SALT = "tipax-excel-filler-v1-"

        // همین رشته باید عیناً تو ابزار activation_tool.html هم باشه، وگرنه
        // کدهای فعال‌سازی با هم جور درنمیان.
        private const val ACTIVATION_SECRET = "tipax-rezashahr-excelfiller-activation-secret-2026"

        fun hash(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest((PASSWORD_SALT + input).toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** یه کد ۸ کاراکتری که به گوشی (نصبِ این اپ رو این گوشی) وابسته‌ست. */
        fun deviceCode(context: Context): String {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(androidId.toByteArray(Charsets.UTF_8))
            val hex = bytes.joinToString("") { "%02x".format(it) }.uppercase().take(8)
            return "${hex.substring(0, 4)}-${hex.substring(4, 8)}"
        }

        /** از روی کد گوشی، کد فعال‌سازی ۶ رقمی متناظرش رو می‌سازه (HMAC-SHA256). */
        fun activationCodeFor(deviceCode: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(ACTIVATION_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val result = mac.doFinal(deviceCode.toByteArray(Charsets.UTF_8))
            val num = BigInteger(1, result).mod(BigInteger.valueOf(1_000_000))
            return num.toString().padStart(6, '0')
        }
    }
}
