package ir.tipaxrezashahr.excelfiller

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** صفحه‌ی اول اپ: انتخاب بین «قیمت ثابت» و «قیمت متغیر». */
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        findViewById<android.view.View>(R.id.fixedButton).setOnClickListener { open(0) }
        findViewById<android.view.View>(R.id.variableButton).setOnClickListener { open(1) }
        findViewById<android.view.View>(R.id.cheramButton).setOnClickListener { tool("tool_cheram.html", "چرم دقت") }
        findViewById<android.view.View>(R.id.telegramJeepButton).setOnClickListener { tool("tool_telegram_jeep.html", "تلگرام جیپ") }
        findViewById<android.view.View>(R.id.jeepButton).setOnClickListener { tool("tool_jeep.html", "جیپ") }
    }

    private fun tool(asset: String, title: String) {
        startActivity(
            Intent(this, WebToolActivity::class.java)
                .putExtra(WebToolActivity.EXTRA_ASSET, asset)
                .putExtra(WebToolActivity.EXTRA_TITLE, title)
        )
    }

    private fun open(index: Int) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PRICE_INDEX, index))
    }
}
