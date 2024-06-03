package com.hifnawy.caffeinate.utils

import android.content.res.Resources
import android.util.TypedValue

object ThemeExtensionFunctions {

    val Resources.Theme.themeColor: Int
        get() {
            val typedValue = TypedValue()
            this@themeColor.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            return typedValue.data
        }
}