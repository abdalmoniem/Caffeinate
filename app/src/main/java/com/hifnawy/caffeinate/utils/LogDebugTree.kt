package com.hifnawy.caffeinate.utils

import android.util.Log
import com.hifnawy.caffeinate.utils.LogDebugTree.StackTraceObject
import timber.log.Timber

/**
 * A custom implementation of [Timber.DebugTree] for logging debug messages.
 *
 * This class is responsible for logging messages with a specified package name.
 * It overrides the default behavior of [Timber.DebugTree] to include additional
 * information such as the calling method's class and method name.
 *
 * @constructor Creates a new instance of [LogDebugTree] with the specified package name.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see Timber.DebugTree
 * @see StackTraceObject
 */
class LogDebugTree : Timber.DebugTree() {

    /**
     * Represents a single stack trace element from the thread's stack trace.
     *
     * This class is used to extract information about the calling method from the stack trace.
     * It provides the class name and method name of the calling method.
     *
     * @property className [String] the name of the class of the calling method.
     * @property methodName [String] the name of the calling method.
     *
     * @constructor Creates a new instance of [StackTraceObject].
     *
     * @author AbdAlMoniem AlHifnawy
     */
    private data class StackTraceObject(val className: String = "UnknownClass", val methodName: String = "UnknownMethod") {

        /**
         * Converts the [StackTraceObject] to a string representation in the format "className.methodName".
         *
         * @return [String] a string representation of the object.
         */
        override fun toString() = "${className.split(".").lastOrNull()}.$methodName"
    }

    /**
     * Logs a message with a [priority] and an optional [tag][Timber.tag].
     *
     * This method is called by the various [log functions][Timber.log] in [Timber].
     * It logs the message to the console by writing to [System.out][System.out].
     * The message is prefixed with the [tag] and the [priority] is used to determine the color
     * of the output.
     *
     * @param priority [Int] the logging priority.
     * @param tag [String] the tag for the log message. If not provided, the tag defaults to the [Timber.tag].
     * @param message [String] the log message.
     * @param t [Throwable] the throwable to log, if any.
     *
     * @author AbdAlMoniem AlHifnawy
     *
     * @see Timber.tag
     * @see Timber.log
     * @see System.out
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val logMethodName = when (priority) {
            Log.INFO    -> "i"
            Log.DEBUG   -> "d"
            Log.VERBOSE -> "v"
            Log.WARN    -> "w"
            Log.ERROR   -> "e"
            Log.ASSERT  -> "wtf"
            else        -> return
        }
        val stackTraceObjects = Thread.currentThread().stackTrace.map { StackTraceObject(it.className, it.methodName) }
        val callerObject = stackTraceObjects.lastOrNull { it.methodName == logMethodName || it.methodName == "log" }?.let {
            stackTraceObjects[stackTraceObjects.indexOf(it) + 1]
        } ?: StackTraceObject()

        super.log(priority, tag, "$callerObject: $message", t)
    }
}