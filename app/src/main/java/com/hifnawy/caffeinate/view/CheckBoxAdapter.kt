package com.hifnawy.caffeinate.view

import android.transition.Explode
import android.transition.TransitionManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.TimeoutCheckboxItemBinding
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
data class CheckBoxItem(
        /**
         * the text displayed in the CheckBox
         */
        var text: String,
        /**
         * the checked state of the CheckBox
         */
        var isChecked: Boolean,
        /**
         * the enabled state of the CheckBox. When the enabled state is false, the CheckBox is grayed out.
         */
        var isEnabled: Boolean = false,
        /**
         * the duration associated with the item, which is used to sort the items in ascending order.
         */
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
        private val animationDuration: Long = 300,
        private val onItemsChangedListener: OnItemsChangedListener? = null
) :
        RecyclerView.Adapter<CheckBoxAdapter.ViewHolder>() {

    /**
     * A mutable list of [CheckBoxItem]s that contains the same items as the original list provided in the constructor.
     *
     * This list is used to store the items of the adapter and is modified by the adapter's methods.
     *
     * @see CheckBoxAdapter
     */
    private val timeoutCheckBoxes: MutableList<CheckBoxItem> by lazy { timeoutCheckBoxes.map { it.copy() }.toMutableList() }

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
     * The RecyclerView instance that this adapter is attached to.
     *
     * This variable holds a reference to the RecyclerView that observes this adapter.
     * It is initialized when the adapter is attached to a RecyclerView.
     */
    private lateinit var recyclerView: RecyclerView

    /**
     * The list of [CheckBoxItem]s to be managed by the adapter.
     */
    val checkBoxItems: List<CheckBoxItem>
        get() = timeoutCheckBoxes

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
         * The [CheckBox] associated with the [ViewHolder].
         *
         * This [CheckBox] is the one that is bound to the [CheckBoxItem] associated with the [ViewHolder].
         * It is used to display the text and the checked state of the [CheckBoxItem].
         *
         * @see CheckBoxItem
         */
        val checkBox: CheckBox = binding.timeoutCheckBox
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
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder) {
            checkBox.setOnCheckedChangeListener(null)
            val item = timeoutCheckBoxes[adapterPosition].apply {
                checkBox.text = text
                checkBox.isChecked = isChecked
                checkBox.isEnabled = isEnabled
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                item.isChecked = isChecked

                timeoutCheckBoxes.updateFirstItem()

                onItemsChangedListener?.onItemChanged(timeoutCheckBoxes)
            }
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
     */
    fun addCheckBox(checkBoxItem: CheckBoxItem) {
        val isInList = timeoutCheckBoxes.firstOrNull { item -> item.duration == checkBoxItem.duration } != null

        if (!isInList && timeoutCheckBoxes.add(checkBoxItem)) {
            notifyAndAnimateItem(ITEM_INSERTED, timeoutCheckBoxes.indexOf(checkBoxItem))

            timeoutCheckBoxes.sortBy { checkBox -> checkBox.duration }

            notifyAndAnimateItem(ITEM_CHANGED_ALL)

            timeoutCheckBoxes.updateFirstItem()

            onItemsChangedListener?.onItemChanged(timeoutCheckBoxes)
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
     */
    fun removeCheckBox(checkBoxItem: CheckBoxItem) {
        val index = timeoutCheckBoxes.indexOf(checkBoxItem)

        if (timeoutCheckBoxes.remove(checkBoxItem)) {
            notifyAndAnimateItem(ITEM_REMOVED, index)

            timeoutCheckBoxes.firstOrNull()?.apply {
                isChecked = true
                isEnabled = false
            }

            notifyAndAnimateItem(ITEM_CHANGED_SINGLE, 0)

            onItemsChangedListener?.onItemChanged(timeoutCheckBoxes)
        }
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
    private fun notifyAndAnimateItem(modificationType: ModificationType, index: Int? = null) {
        TransitionManager.beginDelayedTransition(recyclerView, transition)

        when (modificationType) {
            ITEM_INSERTED       -> index?.let { notifyItemInserted(it) }
            ITEM_REMOVED        -> index?.let { notifyItemRemoved(it) }
            ITEM_CHANGED_SINGLE -> index?.let { notifyItemChanged(it) }
            ITEM_CHANGED_ALL    -> when {
                animationDuration > 0 -> CoroutineScope(Dispatchers.Main).launch {
                    timeoutCheckBoxes.forEachIndexed { itemIndex, _ ->
                        delay(50)
                        TransitionManager.beginDelayedTransition(recyclerView, transition)
                        notifyItemChanged(itemIndex)
                    }
                }

                else                  -> notifyItemRangeChanged(0, timeoutCheckBoxes.size)
            }
        }
    }

    /**
     * Updates the [firstOrNull] item of the list to be [enabled][CheckBoxItem.isEnabled] or [disabled][CheckBoxItem.isEnabled].
     *
     * - If the [size][List.size] is `1`, the [firstOrNull] item will be [disabled][CheckBoxItem.isEnabled].
     * - If the [size][List.size] is greater than `1`, the [firstOrNull] item will be [enabled][CheckBoxItem.isEnabled] if it is currently
     * [disabled][CheckBoxItem.isEnabled]
     */
    private fun List<CheckBoxItem>.updateFirstItem() {
        filter { checkBoxItem -> checkBoxItem.isChecked }.apply {
            when (size) {
                1    -> firstOrNull { checkBoxItem -> checkBoxItem.isChecked }?.apply { isEnabled = false }
                else -> firstOrNull { checkBoxItem -> !checkBoxItem.isEnabled }?.apply { isEnabled = true }
            }.apply { notifyAndAnimateItem(ITEM_CHANGED_SINGLE, this@updateFirstItem.indexOf(this)) }
        }
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
