package com.mentis.sahasiparis

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtil {
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale("tr", "TR")).format(Date())
}

object MoneyUtil {
    private val money = DecimalFormat("#,##0.00")
    private val qty = DecimalFormat("#,##0.###")
    fun m(value: Double): String = money.format(value)
    fun q(value: Double): String = qty.format(value)
}
