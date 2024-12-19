package com.hifnawy.caffeinate.view

import android.transition.Explode
import android.transition.TransitionManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.CheckBox
import android.widget.ImageView
import androidx.annotation.Keep
import androidx.recyclerview.widget.RecyclerView
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.TimeoutCheckboxItemBinding
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.IntExtensionFunctions.dp
import com.hifnawy.caffeinate.utils.ViewExtensionFunctions.isVisible
import com.hifnawy.caffeinate.view.CheckBoxAdapter.ModificationType.ITEM_CHANGED_ALL
import com.hifnawy.caffeinate.view.CheckBoxAdapter.ModificationType.ITEM_CHANGED_SINGLE
import com.hifnawy.caffeinate.view.CheckBoxAdapter.ModificationType.ITEM_INSERTED
import com.hifnawy.caffeinate.view.CheckBoxAdapter.ModificationType.ITEM_REMOVED
import com.hifnawy.caffeinate.view.CheckBoxAdapter.OnItemsChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Serializable
import kotlin.time.Duration

/**
 * Data class representing a single item in the RecyclerView list.
 *
 * Each item in the list has a text, a checked state, an enabled state, and a duration.
 *
 * This class implements [Serializable] to enable it to be stored in SharedPreferences.
 *
 * @property text [String] the text displayed in the CheckBox
 * @property isChecked [Boolean] the checked state of the CheckBox
 * @property isEnabled [Boolean] the enabled state of the CheckBox. When the enabled state is false, the CheckBox is grayed out.
 * @property duration [Duration] the duration associated with the item, which is used to sort the items in ascending order.
 *
 * @author AbdAlMoniem AlHifnawy
 */
@Keep
data class CheckBoxItem(
        /**
         * the text displayed in the CheckBox
         */
        @Keep
        var text: String,
        /**
         * the checked state of the CheckBox
         */
        @Keep
        var isChecked: Boolean,
        /**
         * the enabled state of the CheckBox. When the enabled state is false, the CheckBox is grayed out.
         */
        @Keep
        var isEnabled: Boolean = false,
        /**
         * the duration associated with the item, which is used to sort the items in ascending order.
         */
        @Keep
        var duration: Duration,
) : Serializable

/**
 * Adapter class for managing a list of [CheckBoxItem] in a RecyclerView.
 *
 * This adapter provides functionality to add, remove, and update [CheckBoxItem]s, as well as callbacks to notify changes in the [CheckBoxItem]s.
 *
 * @param onItemsChangedListener [OnItemsChangedListener] Optional listener to handle changes in the [CheckBoxItem]s.
 *
 * @property checkBoxItems [List] The list of [CheckBoxItem]s to be managed by the adapter.
 *
 * @author AbdAlMoniem AlHifnawy
 */
class CheckBoxAdapter(
        timeoutCheckBoxes: List<CheckBoxItem>,
        private val animationDuration: Long = 50,
        private val onItemsChangedListener: OnItemsChangedListener? = null
) : RecyclerView.Adapter<CheckBoxAdapter.ViewHolder>() {

    /**
     * A mutable list of [CheckBoxItem]s that contains the same items as the original list provided in the constructor.
     *
     * This list is used to store the items of the adapter and is modified by the adapter's methods.
     *
     * @see CheckBoxAdapter
     */
    private val timeoutCheckBoxes: MutableList<CheckBoxItem> by lazy { timeoutCheckBoxes.map { it.copy() }.toMutableList() }

    /**
     * A set of [CheckBoxItem]s that contains the items that are currently selected by the user.
     *
     * This set is used to store the items that are currently selected by the user. The set is modified by the adapter's methods
     * when the user selects or deselects items.
     *
     * @see CheckBoxAdapter
     */
    private val selectedCheckBoxes = mutableSetOf<CheckBoxItem>()

    /**
     * Returns whether all items in the list are selected.
     *
     * This property is a convenience to check if all items in the list are selected.
     *
     * @return [Boolean] `true` if all items in the list are selected, `false` otherwise.
     */
    private val isAllSelected: Boolean get() = itemCount == selectedCheckBoxes.size

    /**
     * A transition that is used to animate the appearance and disappearance of the [RecyclerView] items.
     *
     * This transition is used to animate the appearance and disappearance of the [RecyclerView] items when the
     * list of [CheckBoxItem]s changes. The transition is an instance of [Explode] which is a built-in transition in the
     * [androidx.transition] library. The transition is configured with a duration and an interpolator.
     *
     * @see Explode
     * @see RecyclerView
     * @see TransitionManager
     */
    private val transition = Explode().apply {
        duration = animationDuration
        interpolator = AnticipateOvershootInterpolator()
    }

    /**
     * A [CoroutineScope] that is used to launch coroutines on the main dispatcher.
     *
     * This scope is lazily initialized and provides a context to execute coroutines on the main thread,
     * which is useful for updating the UI. The [Dispatchers.Main] is used to ensure that the coroutines
     * are launched on the main thread.
     */
    private val mainCoroutineScope by lazy { CoroutineScope(Dispatchers.Main) }

    /**
     * The RecyclerView instance that this adapter is attached to.
     *
     * This variable holds a reference to the RecyclerView that observes this adapter.
     * It is initialized when the adapter is attached to a RecyclerView.
     */
    private lateinit var recyclerView: RecyclerView

    /**
     * Returns whether the user has started selecting items.
     *
     * This flag is used to track whether the user has started selecting items. When the user selects an item, the flag is set to {@code true}.
     * When the user finishes selecting items, the flag is set to {@code false}.
     *
     * @return [Boolean] `true` if the user is currently selecting items, `false` otherwise.
     *
     * @see [changeCheckBoxSelection]
     * @see [onBindViewHolder]
     */
    private var isSelecting = false

    /**
     * The list of [CheckBoxItem]s to be managed by the adapter.
     */
    val checkBoxItems: List<CheckBoxItem>
        get() = timeoutCheckBoxes

    /**
     * Listener for changes in item selection.
     *
     * This listener is triggered whenever there is a change in the selection state of [CheckBoxItem]s.
     * Implement this listener to handle any actions that should occur when the selection changes.
     *
     * @see OnItemsSelectionChangedListener
     */
    var onItemsSelectionChangedListener: OnItemsSelectionChangedListener? = null

    /**
     * Listener interface for observing changes to the list of [CheckBoxItem]s.
     *
     * This interface should be implemented by classes that wish to be notified when the list of [CheckBoxItem]s changes.
     * The implementing class can register itself as a listener and respond to changes in the list by overriding the
     * methods provided in this interface.
     */
    fun interface OnItemsChangedListener {

        /**
         * Called when the list of [CheckBoxItem]s changes.
         *
         * This method is called whenever the list of [CheckBoxItem]s changes, such as when an item is added or removed.
         * The implementing class should override this method to perform any necessary actions, such as updating the UI.
         *
         * @param checkBoxItems [List] The new list of [CheckBoxItem]s after the change.
         */
        fun onItemChanged(checkBoxItems: List<CheckBoxItem>)
    }

    /**
     * Listener interface for observing changes in the selection of [CheckBoxItem]s.
     *
     * This interface should be implemented by classes that wish to be notified when the selection state of the [CheckBoxItem]s changes.
     * The implementing class can register itself as a listener and respond to changes in the selection by overriding the method provided
     * in this interface.
     *
     * @see onItemsSelectionChanged
     */
    fun interface OnItemsSelectionChangedListener {

        /**
         * Called when the selection of [CheckBoxItem]s changes.
         *
         * This method is called whenever the selection state of one or more [CheckBoxItem]s changes.
         * The implementing class should override this method to perform any necessary actions, such as updating the UI or processing the
         * newly selected items.
         *
         * @param selectedItems [List] The list of currently selected [CheckBoxItem]s.
         * @param isSelecting [Boolean] `true` if the user is currently selecting items, `false` otherwise.
         * @param isAllSelected [Boolean] `true` if all [CheckBoxItem]s are selected, `false` otherwise.
         */
        fun onItemsSelectionChanged(selectedItems: List<CheckBoxItem>, isSelecting: Boolean, isAllSelected: Boolean)
    }

    /**
     * A ViewHolder for the adapter's items.
     *
     * @param itemView [View] The view of the ViewHolder.
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        /**
         * Binds the [View] of the [ViewHolder] to the [TimeoutCheckboxItemBinding].
         *
         * This field is a [TimeoutCheckboxItemBinding] that is generated by the Android Data Binding library.
         * It is used to bind the [View] of the [ViewHolder] to the [CheckBoxItem] object associated with the
         * [ViewHolder].
         *
         * @see TimeoutCheckboxItemBinding
         */
        private val binding = TimeoutCheckboxItemBinding.bind(itemView)

        /**
         * The root view of the [ViewHolder].
         *
         * This is the root view of the [ViewHolder] that is inflated from the layout file.
         * It is used to bind the [View] of the [ViewHolder] to the [CheckBoxItem] associated with the
         * [ViewHolder].
         *
         * @see TimeoutCheckboxItemBinding
         */
        val rootView = binding.root

        /**
         * The [CheckBox] associated with the [ViewHolder].
         *
         * This [CheckBox] is the one that is bound to the [CheckBoxItem] associated with the [ViewHolder].
         * It is used to display the text and the checked state of the [CheckBoxItem].
         *
         * @see CheckBoxItem
         */
        val checkBox: CheckBox = binding.timeoutCheckBox

        /**
         * The [ImageView] associated with the [ViewHolder] that is used to indicate
         * whether the [CheckBoxItem] associated with the [ViewHolder] is selected or not.
         *
         * This [ImageView] is the one that is bound to the [CheckBoxItem] associated with the
         * [ViewHolder]. It is used to display the checkmark icon when the [CheckBoxItem] is
         * selected and hide it when the [CheckBoxItem] is not selected.
         *
         * @see CheckBoxItem
         */
        val selectedCheckMark = binding.selectedCheckMark

        /**
         * The delete button associated with the [ViewHolder].
         *
         * This button is the one that is bound to the [CheckBoxItem] associated with the [ViewHolder].
         * It is used to delete the [CheckBoxItem] from the list of available timeouts.
         *
         * @see CheckBoxItem
         */
        val deleteButton = binding.deleteTimeoutButton
    }

    /**
     * Called by RecyclerView when it starts observing this Adapter.
     *
     *
     * Keep in mind that same adapter may be observed by multiple RecyclerViews.
     *
     * @param recyclerView The RecyclerView instance which started observing this adapter.
     * @see onDetachedFromRecyclerView
     */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    /**
     * Creates a new ViewHolder for the specified view type.
     *
     * @param parent [ViewGroup] The parent ViewGroup that the new View will be added to after it is bound to an adapter position.
     * @param viewType [Int] The view type of the new View.
     *
     * @return [ViewHolder] A new [ViewHolder] that holds a View representing a single item in the adapter.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.timeout_checkbox_item, parent, false)
    )

    /**
     * Binds the data to the ViewHolder at the specified position.
     *
     * @param holder [ViewHolder] The ViewHolder to bind the data to.
     * @param position [Int] The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder) {
        val item = timeoutCheckBoxes[adapterPosition].apply {
            checkBox.text = duration.toLocalizedFormattedTime(itemView.context)
            checkBox.isChecked = isChecked
            checkBox.isEnabled = isEnabled && !isSelecting

            rootView.strokeWidth = when {
                this in selectedCheckBoxes -> 3.dp
                else                       -> 0.dp
            }

            deleteButton.isVisible = isEnabled && !isSelecting
            selectedCheckMark.isVisible = isEnabled && isSelecting && this in selectedCheckBoxes
        }
        val itemClickListener = View.OnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

            if (!isSelecting) {
                item.isChecked = !item.isChecked
                checkBox.isChecked = item.isChecked

                timeoutCheckBoxes.updateFirstItem()

                onItemsChangedListener?.onItemChanged(timeoutCheckBoxes)
            } else {
                changeCheckBoxSelection(item, false)
            }
        }

        rootView.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            changeCheckBoxSelection(item, false)

            true
        }

        rootView.setOnClickListener(itemClickListener)
        checkBox.setOnClickListener(itemClickListener)

        deleteButton.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

            removeCheckBox(item)
        }
    }

    /**
     * Returns the size of the list of [CheckBoxItem]s.
     *
     * @return [Int] the [size][List.size] of the list of [CheckBoxItem]s
     */
    override fun getItemCount(): Int = timeoutCheckBoxes.size

    /**
     * Adds the specified [checkBoxItem] to the list if it doesn't already exist.
     *
     * - If the list doesn't already contain the specified [checkBoxItem], it will be added to the list.
     * - The list will be sorted by the [duration][CheckBoxItem.duration] in ascending order after the new item is added.
     * - The [notifyDataSetChanged][RecyclerView.Adapter.notifyDataSetChanged] method will be called after the list is modified.
     * - The [notifyItemRangeChanged][RecyclerView.Adapter.notifyItemRangeChanged] method will be called with the start index of 0 and the item count
     * of [timeoutCheckBoxes.size][List.size] after the list is modified.
     * - The [updateFirstItem][List.updateFirstItem] method will be called after the list is modified.
     *
     * @param checkBoxItem [CheckBoxItem] the item to be added
     * @param animate [Boolean] whether to animate the addition of the item
     */
    fun addCheckBox(checkBoxItem: CheckBoxItem, animate: Boolean = animationDuration > 0) = with(timeoutCheckBoxes) {
        mainCoroutineScope.launch {
            clearAllCheckBoxesSelection(animate)
            delay(animationDuration)

            if (!add(checkBoxItem)) return@launch
            notifyAndAnimateItem(ITEM_INSERTED, indexOf(checkBoxItem))
            delay(animationDuration)

            sortBy { checkBox -> checkBox.duration }
            notifyAndAnimateItem(ITEM_CHANGED_ALL)
            updateFirstItem()

            onItemsChangedListener?.onItemChanged(this@with)
        }
    }

    /**
     * Removes the specified [checkBoxItem] from the list if it exists.
     *
     * - If the list has only one item and it is the specified item, the item will be removed and the list will be empty.
     * - If the list has more than one item and the specified item is the first item in the list, the item will be removed and the new first item will
     * be enabled and checked.
     * - If the list has more than one item and the specified item is not the first item in the list, the item will be removed and the list will remain
     * unchanged.
     *
     * @param checkBoxItem [CheckBoxItem] the item to be removed
     * @param animate [Boolean] `true` to animate the removal of the item, `false` otherwise
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun removeCheckBox(checkBoxItem: CheckBoxItem, animate: Boolean = animationDuration > 0) = mainCoroutineScope.launch {
        with(timeoutCheckBoxes) {
            clearAllCheckBoxesSelection(animate)
            delay(animationDuration)

            val index = indexOf(checkBoxItem)
            if (!remove(checkBoxItem)) return@launch
            notifyAndAnimateItem(ITEM_REMOVED, index)
            delay(animationDuration)

            val isAllUnchecked = all { checkBox -> !checkBox.isChecked }
            if (isAllUnchecked || size == 1) firstOrNull()?.apply {
                isChecked = true
                isEnabled = size != 1

                notifyAndAnimateItem(ITEM_CHANGED_SINGLE, 0)
                delay(animationDuration)
            }

            onItemsChangedListener?.onItemChanged(this@with)
        }
    }

    /**
     * Removes all checked [CheckBoxItem]s from the list.
     *
     * This method removes all checked [CheckBoxItem]s from the list.
     * It is called by the [removeTimeouts][TimeoutsSelectionFragment.removeTimeouts] method in the [TimeoutsSelectionFragment].
     *
     * @param animate [Boolean] `true` to animate the removal of the checked items, `false` otherwise.
     *
     * @see removeCheckBox
     */
    fun removeSelectedCheckBoxes(animate: Boolean = animationDuration > 0) = selectedCheckBoxes.forEach { removeCheckBox(it, animate) }

    /**
     * Toggles the selection state of all [CheckBoxItem]s in the list.
     *
     * - If not all items are selected, this method selects all items.
     * - If all items are selected, this method deselects all items.
     *
     * This method also notifies the [RecyclerView] of the modifications and animates the changes if [animate] is `true`.
     *
     * @param animate [Boolean] `true` to animate the selection changes, `false` otherwise.
     */
    fun changeAllCheckBoxesSelection(animate: Boolean = animationDuration > 0) = mainCoroutineScope.launch {
        isSelecting = !isAllSelected

        when {
            !isAllSelected -> timeoutCheckBoxes.forEachIndexed { index, checkBoxItem ->
                selectedCheckBoxes.add(checkBoxItem)

                if (animate) {
                    delay(animationDuration)
                    notifyAndAnimateItem(ITEM_CHANGED_SINGLE, index)
                } else notifyItemChanged(index)
            }

            isAllSelected  -> timeoutCheckBoxes.forEachIndexed { index, checkBoxItem ->
                selectedCheckBoxes.remove(checkBoxItem)

                if (animate) {
                    delay(animationDuration)
                    notifyAndAnimateItem(ITEM_CHANGED_SINGLE, index)
                } else notifyItemChanged(index)
            }
        }

        onItemsSelectionChangedListener?.onItemsSelectionChanged(selectedCheckBoxes.toList(), isSelecting, isAllSelected)
    }

    /**
     * Toggles the selection state of the specified [checkBoxItem] in the list.
     *
     * - If the item is not selected, it is added to the [selectedCheckBoxes] list.
     * - If the item is selected, it is removed from the [selectedCheckBoxes] list.
     *
     * This method also notifies the [RecyclerView] of the modification and animates the change.
     * It is called by the [onBindViewHolder][CheckBoxAdapter.onBindViewHolder] and [onItemLongClick][View.setOnLongClickListener] methods.
     *
     * @param checkBoxItem [CheckBoxItem] the item to be toggled
     * @param animate [Boolean] `true` to animate the selection change, `false` otherwise
     */
    private fun changeCheckBoxSelection(checkBoxItem: CheckBoxItem, animate: Boolean = animationDuration > 0) = mainCoroutineScope.launch {
        when (checkBoxItem) {
            !in selectedCheckBoxes -> selectedCheckBoxes.add(checkBoxItem)
            else                   -> selectedCheckBoxes.remove(checkBoxItem)
        }

        isSelecting = selectedCheckBoxes.isNotEmpty()
        onItemsSelectionChangedListener?.onItemsSelectionChanged(selectedCheckBoxes.toList(), isSelecting, isAllSelected)

        if (animate) {
            delay(animationDuration)
            if (selectedCheckBoxes.size in 0..1) notifyAndAnimateItem(ITEM_CHANGED_ALL)
            else notifyAndAnimateItem(ITEM_CHANGED_SINGLE, timeoutCheckBoxes.indexOf(checkBoxItem))
        } else {
            if (selectedCheckBoxes.size in 0..1) notifyItemRangeChanged(0, itemCount)
            else notifyItemChanged(timeoutCheckBoxes.indexOf(checkBoxItem))
        }
    }

    /**
     * Clears all selected [CheckBoxItem]s from the [selectedCheckBoxes] list.
     *
     * This method is called whenever the list of [CheckBoxItem]s is modified to ensure that the selection state is consistent with the list.
     * It is also called when the user selects or deselects all [CheckBoxItem]s in the list.
     *
     * @param animate [Boolean] `true` to animate the selection change, `false` otherwise
     *
     * @see [selectedCheckBoxes]
     * @see [isSelecting]
     * @see [addCheckBox]
     * @see [removeCheckBox]
     * @see [removeSelectedCheckBoxes]
     */
    private fun clearAllCheckBoxesSelection(animate: Boolean = animationDuration > 0) = mainCoroutineScope.launch {
        if (selectedCheckBoxes.isEmpty()) return@launch

        selectedCheckBoxes.clear()
        isSelecting = selectedCheckBoxes.isNotEmpty()
        onItemsSelectionChangedListener?.onItemsSelectionChanged(selectedCheckBoxes.toList(), isSelecting, isAllSelected)

        if (animate) {
            delay(animationDuration)
            notifyAndAnimateItem(ITEM_CHANGED_ALL)
        } else notifyItemRangeChanged(0, itemCount)
    }

    /**
     * Notifies the [RecyclerView] of the specified modification and animates the change.
     *
     * This method is used to notify the [RecyclerView] of the specified modification and to animate the change.
     * It is called by the [addCheckBox][CheckBoxAdapter.addCheckBox] and [removeCheckBox][CheckBoxAdapter.removeCheckBox] methods.
     *
     * @param modificationType [ModificationType] the type of modification that has occurred
     * @param index [Int] the index of the item that has been modified. It is optional and can be null.
     */
    private fun notifyAndAnimateItem(modificationType: ModificationType, index: Int? = null) =
            TransitionManager.beginDelayedTransition(recyclerView, transition).also {
                when (modificationType) {
                    ITEM_INSERTED       -> index?.let { notifyItemInserted(it) }
                    ITEM_REMOVED        -> index?.let { notifyItemRemoved(it) }
                    ITEM_CHANGED_SINGLE -> index?.let { notifyItemChanged(it) }
                    ITEM_CHANGED_ALL    -> notifyItemsRangeChanged(0, itemCount)
                }
            }

    /**
     * Notifies the [RecyclerView] that a range of items has changed and animates the change if required.
     *
     * This method determines whether to animate the change based on the [animationDuration]. If the duration is greater than zero,
     * it will animate the change; otherwise, it will simply notify the change without animation.
     *
     * @param position [Int] The starting position of the range of items that has changed.
     * @param itemCount [Int] The number of items in the range that has changed.
     */
    private fun notifyItemsRangeChanged(position: Int, itemCount: Int) = when {
        animationDuration > 0 -> notifyAndAnimateAll(position, itemCount)
        else                  -> mainCoroutineScope.launch { notifyItemRangeChanged(position, itemCount) }
    }

    /**
     * Notifies the [RecyclerView] that a range of items has changed and animates the change over time.
     *
     * This method notifies the [RecyclerView] that a range of items has changed and animates the change over time.
     * The animation is done by notifying the [RecyclerView] of the change one item at a time with a delay in between.
     * The delay is specified by the [animationDuration] divided by the number of items in the range.
     *
     * @param positionStart [Int] The starting position of the range of items that has changed.
     * @param itemCount [Int] The number of items in the range that has changed.
     */
    private fun notifyAndAnimateAll(positionStart: Int, itemCount: Int) = mainCoroutineScope.launch {
        (positionStart..itemCount).forEach { index ->
            delay(animationDuration)
            TransitionManager.beginDelayedTransition(recyclerView, transition)
            notifyItemChanged(index)
        }
    }

    /**
     * Updates the [first] item of the list to be [enabled][CheckBoxItem.isEnabled] or [disabled][CheckBoxItem.isEnabled].
     *
     * - If the [size][List.size] is `1`, the [first] item will be [disabled][CheckBoxItem.isEnabled].
     * - If the [size][List.size] is greater than `1`, the [first] item will be [enabled][CheckBoxItem.isEnabled] if it is currently
     * [disabled][CheckBoxItem.isEnabled]
     */
    private fun List<CheckBoxItem>.updateFirstItem() = filter { checkBoxItem -> checkBoxItem.isChecked }.run {
        when (size) {
            1    -> find { checkBoxItem -> checkBoxItem.isChecked }?.apply { isEnabled = false }
            else -> find { checkBoxItem -> !checkBoxItem.isEnabled }?.apply { isEnabled = true }
        }.run { notifyAndAnimateItem(ITEM_CHANGED_SINGLE, this@updateFirstItem.indexOf(this)) }
    }

    /**
     * Enum representing the type of modification made to a list of items.
     *
     * This enum is used to specify the type of change that has occurred in the list, allowing appropriate
     * actions to be taken based on the modification type.
     *
     * @property ITEM_INSERTED Indicates that a new item has been inserted into the list.
     * @property ITEM_REMOVED Indicates that an item has been removed from the list.
     * @property ITEM_CHANGED_SINGLE Indicates that a single item in the list has been changed.
     * @property ITEM_CHANGED_ALL Indicates that all items in the list have been changed.
     */
    private enum class ModificationType {

        /**
         * Represents the insertion of a new item into the list.
         */
        ITEM_INSERTED,

        /**
         * Represents the removal of an item from the list.
         */
        ITEM_REMOVED,

        /**
         * Represents a change to a single item within the list.
         */
        ITEM_CHANGED_SINGLE,

        /**
         * Represents a change applied to all items within the list.
         */
        ITEM_CHANGED_ALL
    }
}
