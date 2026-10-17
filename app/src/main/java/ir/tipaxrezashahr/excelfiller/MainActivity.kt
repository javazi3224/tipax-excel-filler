package ir.tipaxrezashahr.excelfiller

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * اپ ساده‌ی «فرم مرسولات تیپاکس» - کاملاً مستقل از سایت، هیچ اتصال اینترنتی
 * لازم نداره. فقط اسم گیرنده، شهر مقصد، آدرس و موبایل رو می‌گیره و با همون
 * فایل اکسل رسمی تیپاکس (که بقیه‌ی ستون‌ها و شیت‌هاش دست‌نخورده می‌مونه) یه
 * فایل خروجی می‌سازه. تایپ صوتی از طریق دکمه‌ی میکروفونِ خودِ کیبورد گوشی
 * (مثلاً Gboard) رو هر کدوم از فیلدهای متنی کار می‌کنه، بدون نیاز به کد
 * اضافه.
 */
class MainActivity : AppCompatActivity() {

    private val rows = mutableListOf<ShipmentRow>()
    private lateinit var adapter: RowsAdapter
    private lateinit var cityList: List<String>

    private lateinit var nameLayout: TextInputLayout
    private lateinit var cityLayout: TextInputLayout
    private lateinit var addressLayout: TextInputLayout
    private lateinit var phoneLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var cityInput: AutoCompleteTextView
    private lateinit var addressInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var listCountText: android.widget.TextView

    private var pendingExportAction: (() -> Unit)? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingExportAction?.invoke()
        } else {
            Toast.makeText(this, "بدون این مجوز نمی‌تونم فایل رو تو پوشه Download ذخیره کنم.", Toast.LENGTH_LONG).show()
        }
        pendingExportAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nameLayout = findViewById(R.id.nameLayout)
        cityLayout = findViewById(R.id.cityLayout)
        addressLayout = findViewById(R.id.addressLayout)
        phoneLayout = findViewById(R.id.phoneLayout)
        nameInput = findViewById(R.id.nameInput)
        cityInput = findViewById(R.id.cityInput)
        addressInput = findViewById(R.id.addressInput)
        phoneInput = findViewById(R.id.phoneInput)
        listCountText = findViewById(R.id.listCountText)

        cityList = loadCities()
        val cityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cityList)
        cityInput.setAdapter(cityAdapter)
        cityInput.threshold = 1

        val recyclerView = findViewById<RecyclerView>(R.id.rowsRecyclerView)
        adapter = RowsAdapter(rows) { position ->
            rows.removeAt(position)
            adapter.notifyItemRemoved(position)
            updateCountText()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<android.view.View>(R.id.addRowButton).setOnClickListener { onAddRow() }
        findViewById<android.view.View>(R.id.exportButton).setOnClickListener { onExportClicked() }
        findViewById<android.view.View>(R.id.clearButton).setOnClickListener { onClearClicked() }

        updateCountText()
    }

    private fun loadCities(): List<String> {
        return try {
            val json = assets.open("cities.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(json)
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateCountText() {
        listCountText.text = if (rows.isEmpty()) {
            "هنوز هیچ ردیفی اضافه نشده"
        } else {
            "${rows.size} ردیف اضافه شده (حداکثر ${ExcelFiller.maxRows} ردیف تو یه فایل جا می‌شه)"
        }
    }

    private fun onAddRow() {
        nameLayout.error = null
        cityLayout.error = null
        addressLayout.error = null
        phoneLayout.error = null

        val name = nameInput.text?.toString()?.trim().orEmpty()
        val city = cityInput.text?.toString()?.trim().orEmpty()
        val address = addressInput.text?.toString()?.trim().orEmpty()
        val phone = phoneInput.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (name.isEmpty()) {
            nameLayout.error = "نام گیرنده رو وارد کن"
            hasError = true
        }
        if (city.isEmpty()) {
            cityLayout.error = "شهر مقصد رو وارد کن"
            hasError = true
        } else if (cityList.isNotEmpty() && cityList.none { it == city }) {
            cityLayout.error = "این شهر تو لیست شهرهای تیپاکس نیست - از پیشنهادها انتخاب کن"
            hasError = true
        }
        if (address.isEmpty()) {
            addressLayout.error = "آدرس کامل رو وارد کن"
            hasError = true
        }
        if (phone.isEmpty()) {
            phoneLayout.error = "شماره موبایل رو وارد کن"
            hasError = true
        } else if (!phone.matches(Regex("^[0-9]{11}$"))) {
            phoneLayout.error = "شماره موبایل باید دقیقاً ۱۱ رقم باشه (مثلاً 09123456789)"
            hasError = true
        }
        if (hasError) return

        if (rows.size >= ExcelFiller.maxRows) {
            Toast.makeText(this, "ظرفیت فایل پر شده؛ اول یه فایل اکسل بساز و لیست رو پاک کن.", Toast.LENGTH_LONG).show()
            return
        }

        rows.add(ShipmentRow(name, city, address, phone))
        adapter.notifyItemInserted(rows.size - 1)
        updateCountText()

        nameInput.text?.clear()
        cityInput.text?.clear()
        addressInput.text?.clear()
        phoneInput.text?.clear()
        nameInput.requestFocus()
    }

    private fun onClearClicked() {
        if (rows.isEmpty()) return
        android.app.AlertDialog.Builder(this)
            .setTitle("پاک کردن لیست")
            .setMessage("مطمئنی می‌خوای همه‌ی ${rows.size} ردیف رو پاک کنی؟")
            .setPositiveButton("بله، پاک کن") { _, _ ->
                val count = rows.size
                rows.clear()
                adapter.notifyItemRangeRemoved(0, count)
                updateCountText()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun onExportClicked() {
        if (rows.isEmpty()) {
            Toast.makeText(this, "اول حداقل یه ردیف به لیست اضافه کن.", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingExportAction = { doExport() }
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        doExport()
    }

    private fun doExport() {
        val fileName = "tipax_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".xlsx"
        Thread {
            try {
                val uri = writeToDownloads(fileName)
                runOnUiThread {
                    if (uri != null) {
                        Toast.makeText(this, "فایل ساخته و تو پوشه Download ذخیره شد.", Toast.LENGTH_LONG).show()
                        shareFile(uri)
                    } else {
                        Toast.makeText(this, "ساخت فایل با خطا مواجه شد.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** فایل رو تو پوشه‌ی Download گوشی می‌سازه و Uri قابل اشتراک‌گذاریش رو برمی‌گردونه. */
    private fun writeToDownloads(fileName: String): Uri? {
        val mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                ExcelFiller.build(this, rows.toList(), out)
            } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { out ->
                ExcelFiller.build(this, rows.toList(), out)
            }
            return FileProvider.getUriForFile(this, "ir.tipaxrezashahr.excelfiller.fileprovider", file)
        }
    }

    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل اکسل"))
        } catch (e: Exception) {
            // کاربر می‌تونه فایل رو خودش از پوشه‌ی Download پیدا کنه
        }
    }
}
