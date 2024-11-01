package com.hifnawy.caffeinate.ui

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.TimeoutCheckboxItemBinding
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
        var duration: Duration
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
class CheckBoxAdapter(timeoutCheckBoxes: List<CheckBoxItem>, private val onItemsChangedListener: OnItemsChangedListener? = null) :
        RecyclerView.Adapter<CheckBoxAdapter.ViewHolder>() {

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

    private val timeoutCheckBoxes: MutableList<CheckBoxItem> = timeoutCheckBoxes.map { it.copy() }.toMutableList()

    /**
     * The list of [CheckBoxItem]s to be managed by the adapter.
     */
    val checkBoxItems: List<CheckBoxItem>
        get() = timeoutCheckBoxes

    /**
     * A ViewHolder for the adapter's items.
     *
     * @param itemView [View] The view of the ViewHolder.
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val binding = TimeoutCheckboxItemBinding.bind(itemView)
        val checkBox: CheckBox = binding.timeoutCheckBox
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
                itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

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
            timeoutCheckBoxes.sortBy { checkBox -> checkBox.duration }

            notifyItemRangeChanged(0, timeoutCheckBoxes.size)

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
            notifyItemRemoved(index)

            timeoutCheckBoxes.firstOrNull()?.apply {
                isChecked = true
                isEnabled = false
            }

            notifyItemChanged(0)

            onItemsChangedListener?.onItemChanged(timeoutCheckBoxes)
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
            }.apply { notifyItemChanged(this@updateFirstItem.indexOf(this)) }
        }
    }
}
