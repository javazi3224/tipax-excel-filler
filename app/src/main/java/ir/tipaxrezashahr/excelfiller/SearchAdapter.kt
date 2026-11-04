package ir.tipaxrezashahr.excelfiller

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

/**
 * آداپتور جستجوی خودکار: هر چی کاربر تایپ کنه، تو کل لیست (حتی صدها هزار اسم) دنبالش
 * می‌گرده و حداکثر [LIMIT] نتیجه‌ی اول رو نشون می‌ده. اسمایی که با عبارت شروع
 * می‌شن بالاتر میان. جستجو به ی/ک عربی، اعداد فارسی/انگلیسی و فاصله‌ها حساس نیست.
 */
class SearchAdapter(private val context: Context) : BaseAdapter(), Filterable {

    private var items: List<String> = emptyList()
    private var norm: List<String> = emptyList()
    private var shown: List<String> = emptyList()

    fun setItems(list: List<String>) {
        items = list
        norm = list.map { normalize(it) }
        shown = list.take(LIMIT)
        notifyDataSetChanged()
    }

    val size: Int get() = items.size

    fun contains(text: String): Boolean = items.any { it.trim() == text.trim() }

    /** مقدار اصلی (با همون املا/فاصله‌ی داخل اکسل) برای متن انتخاب‌شده. */
    fun original(text: String): String? = items.firstOrNull { it.trim() == text.trim() }

    override fun getCount() = shown.size
    override fun getItem(position: Int): String = shown[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = (convertView as? TextView) ?: android.view.LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false) as TextView
        v.text = shown[position].trim()
        return v
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val q = normalize(constraint?.toString().orEmpty())
            val out = ArrayList<String>(LIMIT)
            if (q.isEmpty()) {
                for (i in 0 until minOf(LIMIT, items.size)) out.add(items[i])
            } else {
                val tokens = q.split(' ').filter { it.isNotEmpty() }
                val starts = ArrayList<String>()
                val others = ArrayList<String>()
                for (i in norm.indices) {
                    val n = norm[i]
                    if (tokens.all { n.contains(it) }) {
                        if (n.startsWith(q)) starts.add(items[i]) else others.add(items[i])
                        if (starts.size >= LIMIT) break
                    }
                }
                out.addAll(starts)
                for (o in others) { if (out.size >= LIMIT) break; out.add(o) }
            }
            return FilterResults().apply { values = out; count = out.size }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            shown = (results?.values as? List<String>) ?: emptyList()
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? String)?.trim() ?: ""
    }

    companion object {
        const val LIMIT = 50

        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s) {
                when (ch) {
                    'ي', 'ى' -> sb.append('ی')
                    'ك' -> sb.append('ک')
                    '‌', '‏', '‎' -> sb.append(' ')
                    in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                    in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                    else -> sb.append(ch.lowercaseChar())
                }
            }
            return sb.toString().trim().replace(Regex("\\s+"), " ")
        }

        /** فایل لیست‌ها (assets) رو می‌خونه: بخش‌ها با خط ##A ##B ##C ##J شروع می‌شن. */
        fun loadLists(context: Context, assetName: String): Map<String, List<String>> {
            val map = LinkedHashMap<String, MutableList<String>>()
            var cur: MutableList<String>? = null
            context.assets.open(assetName).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("##")) {
                        cur = ArrayList<String>().also { map[line.substring(2)] = it }
                    } else if (line.isNotEmpty()) {
                        cur?.add(line)
                    }
                }
            }
            return map
        }
    }
}
