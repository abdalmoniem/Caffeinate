package com.hifnawy.caffeinate.utils

import timber.log.Timber as Log

object MutableListExtensionFunctions {

    @Suppress("NON_PUBLIC_CALL_FROM_PUBLIC_INLINE")
    inline fun <reified ObserverType : Any> MutableList<ObserverType>.addObserver(listName: String, observer: ObserverType) {
        val methodName = object {}.javaClass.enclosingMethod?.name

        if (!this.contains(observer)) {
            Log.d("adding ${observer::class.simpleName} to $listName<${ObserverType::class.simpleName}>...")
            this.add(observer)
            Log.d("${observer::class.simpleName} added to $listName<${ObserverType::class.simpleName}>!")
        } else {
            Log.d("${observer::class.simpleName} already added to $listName<${ObserverType::class.simpleName}>!")
        }
    }
}