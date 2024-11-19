package com.hifnawy.caffeinate.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.hifnawy.caffeinate.R

/**
 * A utility class for retrieving colors and determining the color theme.
 *
 * The class determines whether the device is using the Material You theme or not.
 * If the device is using Material You, it uses the colors defined in the theme.
 * Otherwise, it uses the colors defined in the [R.style.Theme_Caffeinate_Baseline] style.
 *
 * @param context [Context] the context to use for retrieving the colors.
 * @param sharedPreferences [SharedPrefsManager] the shared preferences manager to use for retrieving the Material You preference.
 *
 * @author AbdAlMoniem AlHifnawy
 */
@Suppress("unused")
class ColorUtil(private val context: Context, private val sharedPreferences: SharedPrefsManager) {

    /**
     * Returns the colorPrimary color, which is the primary color of the theme.
     *
     * If the device is using the Material You theme, this will return the colorPrimary color from the theme.
     * Otherwise, this will return the colorPrimaryStatic color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorPrimary color.
     */
    val colorPrimary: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorPrimaryDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorPrimary)
        }

    /**
     * Returns the colorPrimaryVariant color, which is a variant of the primary color of the theme.
     *
     * If the device is using the Material You theme, this will return the colorPrimaryVariant color from the theme.
     * Otherwise, this will return the colorPrimaryVariant color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorPrimaryVariant color.
     */
    val colorPrimaryVariant: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorPrimaryVariantDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorPrimaryVariant)
        }

    /**
     * Returns the colorSecondary color, which is the secondary color of the theme.
     *
     * If the device is using the Material You theme, this will return the colorSecondary color from the theme.
     * Otherwise, this will return the colorSecondary color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorSecondary color.
     */
    val colorSecondary: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorSecondaryDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorSecondary)
        }

    /**
     * Returns the colorSecondaryVariant color, which is a variant of the secondary color of the theme.
     *
     * If the device is using the Material You theme, this will return the colorSecondaryVariant color from the theme.
     * Otherwise, this will return the colorSecondaryVariant color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorSecondaryVariant color.
     */
    val colorSecondaryVariant: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorSecondaryVariantDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorSecondaryVariant)
        }

    /**
     * Returns the colorBackgroundFloating color, which is the floating background color of the theme.
     *
     * If the device is using the Material You theme, this will return the colorBackgroundFloating color from the theme.
     * Otherwise, this will return the colorBackground color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorBackgroundFloating color.
     */
    val colorBackgroundFloating: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorBackgroundDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorBackground)
        }

    /**
     * Returns the colorSurface color, which is the surface color of the theme.
     *
     * If the device is using the Material You theme, this will return the colorSurface color from the theme.
     * Otherwise, this will return the colorSurface color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorSurface color.
     */
    val colorSurface: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorSurfaceDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorSurface)
        }

    /**
     * Returns the colorOnPrimary color, which is the color used for text and icons displayed on primary-colored backgrounds.
     *
     * If the device is using the Material You theme, this will return the colorOnPrimary color from the theme.
     * Otherwise, this will return the colorOnPrimary color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorOnPrimary color.
     */
    val colorOnPrimary: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorOnPrimaryDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorOnPrimary)
        }

    /**
     * Returns the colorOnSecondary color, which is the color used for text and icons displayed on secondary-colored backgrounds.
     *
     * If the device is using the Material You theme, this will return the colorOnSecondary color from the theme.
     * Otherwise, this will return the colorOnSecondary color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorOnSecondary color.
     */
    val colorOnSecondary: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorOnSecondaryDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorOnSecondary)
        }

    /**
     * Returns the colorOnBackground color, which is the color used for text and icons displayed on backgrounds.
     *
     * If the device is using the Material You theme, this will return the colorOnBackground color from the theme.
     * Otherwise, this will return the colorOnBackground color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorOnBackground color.
     */
    val colorOnBackground: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorOnBackgroundDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorOnBackground)
        }

    /**
     * Returns the colorOnSurface color, which is the color used for text and icons displayed on surface-colored backgrounds.
     *
     * If the device is using the Material You theme, this will return the colorOnSurface color from the theme.
     * Otherwise, this will return the colorOnSurface color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorOnSurface color.
     */
    val colorOnSurface: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorOnSurfaceDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorOnSurface)
        }

    /**
     * Returns the colorError color, which is used to indicate errors in the UI.
     *
     * If the device is using the Material You theme, this will return the colorError color from the theme.
     * Otherwise, this will return the colorError color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorError color.
     */
    val colorError: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorErrorDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorError)
        }

    /**
     * Returns the colorOnError color, which is used to color the background of error indicators.
     *
     * If the device is using the Material You theme, this will return the colorOnError color from the theme.
     * Otherwise, this will return the colorOnError color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorOnError color.
     */
    val colorOnError: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorOnErrorDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorOnError)
        }

    /**
     * Returns the colorAppBarIcon color, which is the icon color of the app bar.
     *
     * If the device is using the Material You theme, this will return the colorAppBarIcon color from the theme.
     * Otherwise, this will return the colorAppBarIcon color from the [R.style.Theme_Caffeinate_Baseline] style.
     *
     * @return [Int] the colorAppBarIcon color.
     */
    val colorAppBarIcon: Int
        get() = when {
            sharedPreferences.isMaterialYouEnabled -> context.getColor(R.color.colorAppBarIconDynamic)
            else                                   -> context.getAttrColor(R.attr.themeColorAppBarIcon)
        }

    /**
     * Gets the color value for the given attribute from the current theme.
     *
     * @param attr the attribute to get the color value for.
     * @return the color value for the given attribute.
     */
    private fun Context.getAttrColor(
            @AttrRes
            attr: Int
    ): Int = TypedValue().apply {
        theme.resolveAttribute(attr, this, true)
    }.data
}