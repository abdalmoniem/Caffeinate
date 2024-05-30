package com.hifnawy.caffeinate.utils

import android.util.Log

object MutableListExtensionFunctions {

    private val LOG_TAG = MutableListExtensionFunctions::class.simpleName

    @Suppress("NON_PUBLIC_CALL_FROM_PUBLIC_INLINE")
    inline fun <reified ObserverType : Any> MutableList<ObserverType>.addObserver(listName: String, observer: ObserverType) {
        val methodName = object {}.javaClass.enclosingMethod?.name

        if (!this.contains(observer)) {
            Log.d(LOG_TAG, "${methodName}() -> adding ${observer::class.simpleName} to $listName<${ObserverType::class.simpleName}>...")
            this.add(observer)
            Log.d(LOG_TAG, "${methodName}() -> ${observer::class.simpleName} added to $listName<${ObserverType::class.simpleName}>!")
        } else {
            Log.d(LOG_TAG, "${methodName}() -> ${observer::class.simpleName} already added to $listName<${ObserverType::class.simpleName}>!")
        }
    }
}