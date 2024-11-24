package com.hifnawy.caffeinate.utils

import com.hifnawy.caffeinate.controller.Observer
import kotlin.reflect.KClass
import timber.log.Timber as Log

/**
 * Extension functions for [MutableList] to easily add and remove observers.
 *
 * @author AbdAlMoniem AlHifnawy
 */
object MutableListExtensionFunctions {

    /**
     * Retrieves the list of classes for the observers in this list.
     *
     * This property returns the Kotlin class type for each observer currently present in the list.
     * It provides an easy way to inspect the types of observers being managed.
     *
     * @return [List] A list of [KClass] objects representing the class types of the observers.
     */
    val <ObserverType : Observer> MutableList<ObserverType>.itemClasses: List<KClass<out ObserverType>>
        get() = this.map { it::class }

    /**
     * Adds an observer to the list if it is not already present.
     *
     * This function checks if the provided observer is already in the list. If not, it adds the observer
     * to the list and logs a debug message indicating the addition. If the observer is already present,
     * it logs a message indicating that the observer is already added.
     *
     * @param observer [ObserverType] the observer to be added to the list. The type must be a subtype of [Observer].
     */
    inline fun <reified ObserverType : Observer> MutableList<ObserverType>.addObserver(observer: ObserverType) {
        when {
            observer !in this -> {
                Log.d("adding ${observer::class.simpleName} to ${this::class.simpleName}<${ObserverType::class.simpleName}>...")
                add(observer)
                Log.d("${observer::class.simpleName} added to ${this::class.simpleName}<${ObserverType::class.simpleName}>!")
            }

            else              -> Log.d("${observer::class.simpleName} is already added to ${this::class.simpleName}<${ObserverType::class.simpleName}>!")
        }

        if (isNotEmpty()) {
            Log.d(
                    "Items in ${this::class.simpleName}<${ObserverType::class.simpleName}>:\n" +
                    "[${joinToString(", ") { "${it::class.simpleName.toString()}@${it.hashCode().toString(16).uppercase()}" }}]"
            )
        }
    }

    /**
     * Removes an observer from the list if it is present.
     *
     * This function checks if the provided observer is present in the list. If it is, it removes the observer
     * from the list and logs a debug message indicating the removal. If the observer is not present,
     * it logs a message indicating that the observer is not present in the list.
     *
     * @param observer [ObserverType] the observer to be removed from the list. The type must be a subtype of [Observer].
     */
    inline fun <reified ObserverType : Observer> MutableList<ObserverType>.removeObserver(observer: ObserverType) {
        when {
            observer in this -> {
                Log.d("removing ${observer::class.simpleName} from ${this::class.simpleName}<${ObserverType::class.simpleName}>...")
                // when (removeIf { it == observer }) {
                when (remove(observer)) {
                    true -> Log.d("${observer::class.simpleName} removed from ${this::class.simpleName}<${ObserverType::class.simpleName}>!")
                    else -> Log.d("${observer::class.simpleName} is not present in ${this::class.simpleName}<${ObserverType::class.simpleName}>!")
                }
            }

            else             -> Log.d("${observer::class.simpleName} is not present in ${this::class.simpleName}<${ObserverType::class.simpleName}>!")
        }

        if (isNotEmpty()) {
            Log.d(
                    "Items in ${this::class.simpleName}<${ObserverType::class.simpleName}>:\n" +
                    "[${joinToString(", ") { "${it::class.simpleName.toString()}@${it.hashCode().toString(16).uppercase()}" }}]"
            )
        }
    }
}