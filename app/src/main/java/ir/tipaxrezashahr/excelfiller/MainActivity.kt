package ir.tipaxrezashahr.excelfiller

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    private lateinit var senderNameInput: AutoCompleteTextView
    private lateinit var senderAddrInput: AutoCompleteTextView
    private lateinit var packageInput: AutoCompleteTextView
    private lateinit var serviceInput: AutoCompleteTextView
    private lateinit var senderNameLayout: TextInputLayout
    private lateinit var senderAddrLayout: TextInputLayout
    private lateinit var packageLayout: TextInputLayout
    private lateinit var serviceLayout: TextInputLayout
    private lateinit var senderNameAdapter: SearchAdapter
    private lateinit var senderAddrAdapter: SearchAdapter
    private lateinit var packageAdapter: SearchAdapter
    private lateinit var serviceAdapter: SearchAdapter
    private val listAssets = listOf("lists_fixed.txt", "lists_variable.txt")
    private var listsLoadSeq = 0

    /** گزینه‌های نوع قیمت‌گذاری - اندیسش دقیقاً با [PRICE_TYPE_ASSETS] و [PRICE_TYPE_TAGS] جور می‌مونه. */
    private val priceTypeLabels = listOf("قیمت ثابت", "قیمت متغیر")
    private val priceTypeAssets = listOf("template.xlsx", "template2.xlsx")
    private val priceTypeTags = listOf("fixed", "variable")
    private var selectedPriceTypeIndex = 0

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

        selectedPriceTypeIndex = intent.getIntExtra(EXTRA_PRICE_INDEX, 0).coerceIn(0, 1)
        findViewById<TextView>(R.id.screenTitle).text = "ثبت مرسولات — " + priceTypeLabels[selectedPriceTypeIndex]

        senderNameInput = findViewById(R.id.senderNameInput)
        senderAddrInput = findViewById(R.id.senderAddrInput)
        packageInput = findViewById(R.id.packageInput)
        serviceInput = findViewById(R.id.serviceInput)
        senderNameLayout = findViewById(R.id.senderNameLayout)
        senderAddrLayout = findViewById(R.id.senderAddrLayout)
        packageLayout = findViewById(R.id.packageLayout)
        serviceLayout = findViewById(R.id.serviceLayout)
        senderNameAdapter = SearchAdapter(this)
        senderAddrAdapter = SearchAdapter(this)
        packageAdapter = SearchAdapter(this)
        serviceAdapter = SearchAdapter(this)
        setupSearchBox(senderNameInput, senderNameAdapter)
        setupSearchBox(senderAddrInput, senderAddrAdapter)
        setupSearchBox(packageInput, packageAdapter)
        setupSearchBox(serviceInput, serviceAdapter)
        loadSenderLists()

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

    private fun setupSearchBox(box: AutoCompleteTextView, ad: SearchAdapter) {
        box.setAdapter(ad)
        box.threshold = 1
        box.setOnClickListener { if (box.text.isNullOrEmpty()) box.showDropDown() }
        box.setOnFocusChangeListener { _, has -> if (has && box.text.isNullOrEmpty()) box.showDropDown() }
    }

    private fun clearSenderFields() {
        senderNameInput.setText("")
        senderAddrInput.setText("")
        packageInput.setText("")
        serviceInput.setText("")
    }

    /** لیست‌های چهار باکس رو (بسته به قیمت ثابت/متغیر) تو پس‌زمینه از assets می‌خونه. */
    private fun loadSenderLists() {
        val seq = ++listsLoadSeq
        val asset = listAssets.getOrElse(selectedPriceTypeIndex) { listAssets[0] }
        Thread {
            val m = try { SearchAdapter.loadLists(this, asset) } catch (e: Exception) { emptyMap() }
            runOnUiThread {
                if (seq != listsLoadSeq) return@runOnUiThread
                senderNameAdapter.setItems(m["A"].orEmpty())
                senderAddrAdapter.setItems(m["B"].orEmpty())
                packageAdapter.setItems(m["C"].orEmpty())
                serviceAdapter.setItems(m["J"].orEmpty())
            }
        }.start()
    }

    private fun checkBox(layout: TextInputLayout, box: AutoCompleteTextView, ad: SearchAdapter, label: String): String? {
        layout.error = null
        val t = box.text?.toString()?.trim().orEmpty()
        if (t.isEmpty()) {
            layout.error = "$label رو انتخاب کن"
            return null
        }
        val orig = ad.original(t)
        if (orig == null) {
            layout.error = "این مورد تو لیست اکسل نیست - از پیشنهادها انتخاب کن"
            return null
        }
        return orig
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
        val sn = checkBox(senderNameLayout, senderNameInput, senderNameAdapter, "نام فرستنده")
        val sa = checkBox(senderAddrLayout, senderAddrInput, senderAddrAdapter, "عنوان آدرس فرستنده")
        val pk = checkBox(packageLayout, packageInput, packageAdapter, "عنوان مرسوله")
        val sv = checkBox(serviceLayout, serviceInput, serviceAdapter, "نوع سرویس")
        if (sn == null || sa == null || pk == null || sv == null) {
            Toast.makeText(this, "چهار باکس فرستنده/مرسوله/سرویس رو کامل کن.", Toast.LENGTH_LONG).show()
            return
        }
        doExport(SenderInfo(sn, sa, pk, sv))
    }

    private fun doExport(sender: SenderInfo) {
        val priceTag = priceTypeTags.getOrElse(selectedPriceTypeIndex) { "fixed" }
        val templateAsset = priceTypeAssets.getOrElse(selectedPriceTypeIndex) { "template.xlsx" }
        val fileName = "tipax_" + priceTag + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".xlsx"
        Thread {
            try {
                val uri = buildExportFile(fileName, templateAsset, sender)
                runOnUiThread {
                    if (uri != null) {
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

    /**
     * فایل اکسل رو تو فضای داخلی (خصوصی) خود اپ می‌سازه - رو گوشی جایی ذخیره
     * نمی‌مونه که از بیرون دیده بشه، فقط برای یه بار اشتراک‌گذاری ساخته
     * می‌شه. Uri قابل‌اشتراک‌گذاریش (از طریق FileProvider) رو برمی‌گردونه.
     */
    private fun buildExportFile(fileName: String, templateAsset: String, sender: SenderInfo): Uri? {
        val exportsDir = File(filesDir, "exports")
        if (!exportsDir.exists()) exportsDir.mkdirs()
        val file = File(exportsDir, fileName)
        FileOutputStream(file).use { out ->
            ExcelFiller.build(this, rows.toList(), out, templateAsset, sender)
        }
        return FileProvider.getUriForFile(this, "ir.tipaxrezashahr.excelfiller.fileprovider", file)
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
            Toast.makeText(this, "هیچ اپی برای اشتراک‌گذاری پیدا نشد.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_PRICE_INDEX = "price_index"
    }
}
