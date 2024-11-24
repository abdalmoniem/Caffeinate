package com.hifnawy.caffeinate.utils

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hifnawy.caffeinate.view.WidgetConfiguration
import java.io.Serializable

/**
 * Provides extension functions for [SharedPreferences] that provide a more convenient API for storing and retrieving lists of
 * [Serializable] objects.
 *
 * @author AbdAlMoniem AlHifnawy
 */
object SharedPreferencesExtensionFunctions {

    /**
     * Retrieves a list of serializable objects from the [SharedPreferences] associated with the given [key].
     *
     * @param key [String] The key whose associated list is to be returned.
     *
     * @return [List] The list of serializable objects, or an empty list if no mapping of the desired type exists for the key.
     */
    inline fun <reified ObjectType : List<Serializable>> SharedPreferences.getSerializableList(key: String): ObjectType =
            Gson().fromJson(getString(key, "[]"), object : TypeToken<ObjectType>() {}.type)

    /**
     * Saves a list of serializable objects to the [SharedPreferences] associated with the given [key].
     *
     * @param key [String] The key with which the list is to be associated.
     * @param value [List] The list of serializable objects to be saved.
     *
     * @return [SharedPreferences.Editor] The [SharedPreferences.Editor] with which the saving operation was performed.
     */
    inline fun <reified ObjectType : List<Serializable>> SharedPreferences.Editor.putSerializableList(
            key: String,
            value: ObjectType
    ): SharedPreferences.Editor =
            putString(key, Gson().toJson(value.toTypedArray()))

    /**
     * Retrieves a map of serializable objects from the [SharedPreferences] associated with the given [key].
     *
     * @param key [String] The key whose associated map is to be returned.
     *
     * @return [MutableMap] The map of serializable objects, or an empty map if no mapping of the desired type exists for the key.
     */
    inline fun <reified ObjectType : MutableMap<Int, WidgetConfiguration>> SharedPreferences.getSerializableMap(key: String): ObjectType =
            Gson().fromJson(getString(key, "{}"), object : TypeToken<ObjectType>() {}.type)

    /**
     * Saves a map of serializable objects to the [SharedPreferences] associated with the given [key].
     *
     * @param key [String] The key with which the map is to be associated.
     * @param value [MutableMap] The map of serializable objects to be saved.
     *
     * @return [SharedPreferences.Editor] The [SharedPreferences.Editor] with which the saving operation was performed.
     */
    inline fun <reified ObjectType : MutableMap<Int, WidgetConfiguration>?> SharedPreferences.Editor.putSerializableMap(
            key: String,
            value: ObjectType
    ): SharedPreferences.Editor =
            putString(key, Gson().toJson(value))
}