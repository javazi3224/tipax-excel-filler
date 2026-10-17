package ir.tipaxrezashahr.excelfiller

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.security.MessageDigest

/**
 * صفحه‌ی قفل اپ: بار اولی که اپ باز می‌شه، از کاربر می‌خواد یه رمز برای خودش
 * تعریف کنه (رمز جایی به هیچ سروری فرستاده نمی‌شه، فقط رو همین گوشی و به‌صورت
 * هش‌شده ذخیره می‌شه). دفعات بعدی، همون رمز رو می‌خواد. تغییر رمز هم از داخل
 * خود فرم (دکمه‌ی «تغییر رمز») امکان‌پذیره.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var subtitleText: TextView
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmInput: TextInputEditText
    private lateinit var errorText: TextView
    private lateinit var actionButton: MaterialButton
    private var isSetupMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        subtitleText = findViewById(R.id.lockSubtitleText)
        confirmLayout = findViewById(R.id.confirmLayout)
        passwordInput = findViewById(R.id.passwordInput)
        confirmInput = findViewById(R.id.confirmInput)
        errorText = findViewById(R.id.lockErrorText)
        actionButton = findViewById(R.id.lockActionButton)

        isSetupMode = prefs.getString(KEY_HASH, null) == null

        if (isSetupMode) {
            subtitleText.text = "اولین باره که اپ رو باز می‌کنی. یه رمز برای خودت تعریف کن تا بقیه نتونن بدون اجازه ازش استفاده کنن."
            confirmLayout.visibility = View.VISIBLE
            actionButton.text = "تنظیم رمز و ادامه"
        } else {
            subtitleText.text = "برای ادامه، رمز عبور اپ رو وارد کن."
            confirmLayout.visibility = View.GONE
            actionButton.text = "ورود"
        }

        actionButton.setOnClickListener {
            if (isSetupMode) handleSetup() else handleLogin()
        }
    }

    private fun handleSetup() {
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
        prefs.edit().putString(KEY_HASH, hash(p1)).apply()
        proceed()
    }

    private fun handleLogin() {
        errorText.text = ""
        val entered = passwordInput.text?.toString().orEmpty()
        val stored = prefs.getString(KEY_HASH, null)
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
        const val KEY_HASH = "pwd_hash"
        private const val SALT = "tipax-excel-filler-v1-"

        fun hash(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest((SALT + input).toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
