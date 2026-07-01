package com.quniu.baselib.util

import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.widget.EditText
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2


/**
 * 规范化城市名用于比较：去除常见行政区划后缀、括号内容、空白等。
 * 例如 “北京市” -> "北京"，"北京市朝阳区" -> "北京朝阳"（可进一步按需调整）
 */
private fun normalizeCityName(name: String?): String? {
    if (name.isNullOrBlank()) return null
    var s = name.trim()
    // 去掉括号及内部内容
    s = s.replace(Regex("\\(.*?\\)|（.*?）"), "")
    // 移除常见行政区划后缀
    val suffixes = listOf(
        "省",
        "市",
        "自治区",
        "特别行政区",
        "自治州",
        "自治县",
        "县",
        "区",
        "盟",
        "地区",
        "市辖区",
        "县级市"
    )
    var changed = true
    while (changed) {
        changed = false
        for (suf in suffixes) {
            if (s.endsWith(suf)) {
                s = s.substring(0, s.length - suf.length)
                s = s.trim()
                changed = true
            }
        }
    }
    // 去掉所有空白并统一小写（对英文名有作用）
    s = s.replace(Regex("\\s+"), "").lowercase()
    return if (s.isEmpty()) null else s
}

/**
 * 更鲁棒的城市名匹配：在规范化后优先做相等判断，再做包含判断。
 * 提供为 nullable String 的扩展函数，方便调用：
 *    ContentProviderUtil.getCity().cityNamesMatch(selectedCity)
 */
fun String?.cityNamesMatch(other: String?): Boolean {
    val na = normalizeCityName(this) ?: return false
    val nb = normalizeCityName(other) ?: return false
    if (na == nb) return true
    // 若其中一个包含另一个则认为匹配（例如 na="北京" nb="北京市朝阳"）
    return na.contains(nb) || nb.contains(na)
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}

fun EditText.onTextChanged(onTextChanged: (String,Int,Int,Int) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {

        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            onTextChanged.invoke(s.toString(),start,before,count)
        }
    })
}

fun ViewPager.afterPageChanged(afterPageChanged: (Int) -> Unit) {
    this.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {

        }

        override fun onPageSelected(position: Int) {
            afterPageChanged.invoke(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
        }
    })
}

fun ViewPager2.afterPageChanged(afterPageChanged: (Int) -> Unit) {
    this.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            afterPageChanged.invoke(position)
        }

    })


}


val Float.changeToDp
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )
