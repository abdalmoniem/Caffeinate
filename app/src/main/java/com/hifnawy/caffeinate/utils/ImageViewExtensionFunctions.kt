package com.hifnawy.caffeinate.utils

import android.content.res.Resources
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
     * @param drawableResId [Int] the [DrawableRes] id of the drawable to be set.
     * @param color [Int] the [ColorInt] color used to tint the drawable.
     *
     * @throws [UninitializedPropertyAccessException] if the view's context is not initialized.
     * @throws [android.content.res.Resources.NotFoundException] if the drawable resource ID is not valid.
     * @throws Resources.NotFoundException If the [color] param or [colorPrimary][R.color.colorPrimary] attribute is not defined in the current theme.
     */
    fun ImageView.setColoredImageDrawable(
            @DrawableRes
            drawableResId: Int,
            @ColorInt
            color: Int = ContextCompat.getColor(context, R.color.colorPrimary)
    ) = AppCompatResources.getDrawable(context, drawableResId).run {
        setImageDrawable(this)
        setColorFilter(color)
    }
}