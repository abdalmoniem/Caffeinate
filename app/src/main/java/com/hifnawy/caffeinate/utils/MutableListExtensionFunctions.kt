package com.hifnawy.caffeinate.utils

import timber.log.Timber as Log

/**
 * Extension functions for [MutableList] to easily add and remove observers.
 *
 * @author AbdAlMoniem AlHifnawy
 */
object MutableListExtensionFunctions {

    /**
     * Adds the given [observer] to the [MutableList] if it is not already present.
     *
     * Logs the process of adding the observer to the specified [listName].
     *
     * @param listName [String] The name of the list to which the observer is being added.
     * @param observer [ObserverType] The observer instance to be added to the list.
     */
    @Suppress("NON_PUBLIC_CALL_FROM_PUBLIC_INLINE")
    inline fun <reified ObserverType : Any> MutableList<ObserverType>.addObserver(listName: String, observer: ObserverType) = when {
        !contains(observer) -> {
            Log.d("adding ${observer::class.simpleName} to $listName<${ObserverType::class.simpleName}>...")
            add(observer)
            Log.d("${observer::class.simpleName} added to $listName<${ObserverType::class.simpleName}>!")
        }

        else                -> Log.d("${observer::class.simpleName} already added to $listName<${ObserverType::class.simpleName}>!")
    }
}