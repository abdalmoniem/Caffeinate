package com.hifnawy.caffeinate.utils

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable

object SharedPreferencesExtensionFunctions {

    inline fun <reified ObjectType : List<Serializable>> SharedPreferences.getSerializableList(key: String): ObjectType =
            Gson().fromJson(getString(key, "[]"), object : TypeToken<ObjectType>() {}.type)

    inline fun <reified ObjectType : List<Serializable>> SharedPreferences.Editor.putSerializableList(key: String, value: ObjectType): SharedPreferences.Editor =
            putString(key, Gson().toJson(value.toTypedArray()))
}