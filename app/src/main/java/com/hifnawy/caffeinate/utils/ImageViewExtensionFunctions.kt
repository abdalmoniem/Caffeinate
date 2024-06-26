package com.hifnawy.caffeinate.utils

import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.hifnawy.caffeinate.R

object ImageViewExtensionFunctions {

    fun ImageView.setColoredImageDrawable(
            @DrawableRes
            drawableResId: Int,
            @ColorInt
            color: Int = ContextCompat.getColor(context, R.color.colorPrimary)
    ) {
        val appIconDrawable = AppCompatResources.getDrawable(context, drawableResId)

        setImageDrawable(appIconDrawable)
        setColorFilter(color)
    }
}