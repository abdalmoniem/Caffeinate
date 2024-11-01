package com.hifnawy.caffeinate.utils

import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.hifnawy.caffeinate.R

/**
 * A utility class that provides extension functions for [ImageView].
 *
 * @author AbdAlMoniem AlHifnawy
 */
object ImageViewExtensionFunctions {

    /**
     * Sets the image drawable of the given [ImageView] to the specified
     * [drawableResId] and colors it with the specified [color].
     *
     * @param drawableResId [DrawableRes] the id of the drawable to be set.
     * @param color [ColorInt] the color used to tint the drawable.
     */
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