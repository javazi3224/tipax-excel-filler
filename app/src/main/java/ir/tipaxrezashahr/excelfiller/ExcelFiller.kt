package ir.tipaxrezashahr.excelfiller

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * فایل تمپلیت رسمی تیپاکس رو که تو assets/template.xlsx گذاشته شده برمی‌داره،
 * فقط ۴ ستونِ «نام و نام خانوادگی گیرنده» (E)، «شهر مقصد» (F)، «آدرس کامل گیرنده» (G)
 * و «شماره موبایل گیرنده» (I) رو برای هر ردیف پر می‌کنه، و بقیه‌ی فایل
 * (استایل‌ها، لیست شهرها، شیت‌های کمکی و غیره) رو دست‌نخورده نگه می‌داره تا
 * خروجی دقیقاً با فرمتی که سایت تیپاکس قبول می‌کنه سازگار بمونه.
 */
object ExcelFiller {

    private const val SHEET1_ENTRY = "xl/worksheets/sheet1.xml"
    private const val FIRST_DATA_ROW = 2
    private const val MAX_DATA_ROW = 1900 // dimension تمپلیت تا همین ردیف تعریف شده

    /** حداکثر تعداد ردیفی که تو یه بار ساخت فایل جا می‌شه. */
    val maxRows: Int get() = MAX_DATA_ROW - FIRST_DATA_ROW + 1

    private val selfClosingCell = Regex("<x:c r=\"([A-Z]+[0-9]+)\"([^>]*)/>")

    private fun xmlEscape(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\r\n", " ")
            .replace("\n", " ")
    }

    private fun buildSheetXml(template: String, rows: List<ShipmentRow>): String {
        val values = HashMap<String, String>(rows.size * 4)
        rows.forEachIndexed { idx, row ->
            val r = FIRST_DATA_ROW + idx
            values["E$r"] = row.recipientName
            values["F$r"] = row.city
            values["G$r"] = row.address
            values["I$r"] = row.phone
        }
        return selfClosingCell.replace(template) { match ->
            val cellRef = match.groupValues[1]
            val attrs = match.groupValues[2]
            val text = values[cellRef]
            if (text == null) {
                match.value
            } else {
                "<x:c r=\"$cellRef\"$attrs t=\"inlineStr\"><x:is><x:t>${xmlEscape(text)}</x:t></x:is></x:c>"
            }
        }
    }

    /**
     * فایل اکسل نهایی رو می‌سازه و تو [outputStream] می‌نویسه.
     * @throws IllegalArgumentException اگه تعداد ردیف‌ها از ظرفیت تمپلیت بیشتر باشه
     */
    fun build(context: Context, rows: List<ShipmentRow>, outputStream: OutputStream) {
        require(rows.isNotEmpty()) { "لیست خالیه" }
        require(rows.size <= maxRows) { "تعداد ردیف‌ها از ظرفیت فایل (حداکثر $maxRows ردیف) بیشتره" }

        // ZipFile به یه فایل روی دیسک نیاز داره (نه استریم مستقیم asset)،
        // پس اول تمپلیت رو تو یه فایل موقت کپی می‌کنیم.
        val tempTemplate = File.createTempFile("tipax_template", ".xlsx", context.cacheDir)
        try {
            context.assets.open("template.xlsx").use { input ->
                FileOutputStream(tempTemplate).use { out -> input.copyTo(out) }
            }

            ZipFile(tempTemplate).use { zipFile ->
                ZipOutputStream(outputStream).use { zipOut ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val newEntry = ZipEntry(entry.name)
                        zipOut.putNextEntry(newEntry)

                        if (entry.name == SHEET1_ENTRY) {
                            val xml = zipFile.getInputStream(entry).readBytes()
                                .toString(Charset.forName("UTF-8"))
                            val filled = buildSheetXml(xml, rows)
                            zipOut.write(filled.toByteArray(Charset.forName("UTF-8")))
                        } else {
                            zipFile.getInputStream(entry).use { it.copyTo(zipOut) }
                        }
                        zipOut.closeEntry()
                    }
                }
            }
        } finally {
            tempTemplate.delete()
        }
    }
}
