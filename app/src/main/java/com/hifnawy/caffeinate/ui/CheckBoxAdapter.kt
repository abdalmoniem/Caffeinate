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
 * @property text the text displayed in the CheckBox
 * @property isChecked the checked state of the CheckBox
 * @property isEnabled the enabled state of the CheckBox. When the enabled state is false, the CheckBox is grayed out.
 * @property duration the duration associated with the item, which is used to sort the items in ascending order.
 *
 * This class implements [Serializable] to enable it to be stored in SharedPreferences.
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
 * This adapter provides functionality to add, remove, and update [CheckBoxItem]s, as well as callbacks to notify changes in the checked states of
 * the items.
 *
 * @property timeoutCheckBoxes The list of [CheckBoxItem]s to be managed by the adapter.
 * @property onCheckedChangeListener Optional listener to handle checked state changes of the items.
 */
class CheckBoxAdapter(timeoutCheckBoxes: MutableList<CheckBoxItem>, private val onCheckedChangeListener: OnCheckedChangeListener? = null) :
        RecyclerView.Adapter<CheckBoxAdapter.ViewHolder>() {

    fun interface OnCheckedChangeListener {

        fun onCheckedChanged(checkBoxItems: List<CheckBoxItem>)
    }

    private val timeoutCheckBoxes: MutableList<CheckBoxItem> = timeoutCheckBoxes.map { it.copy() }.toMutableList()
    val checkBoxItems: List<CheckBoxItem>
        get() = timeoutCheckBoxes

    /**
     * A ViewHolder for the adapter's items.
     *
     * @param itemView The view of the ViewHolder.
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val binding = TimeoutCheckboxItemBinding.bind(itemView)
        val checkBox: CheckBox = binding.timeoutCheckBox
    }

    /**
     * Creates a new ViewHolder for the specified view type.
     *
     * @param parent The parent ViewGroup that the new View will be added to after it is bound to an adapter position.
     * @param viewType The view type of the new View.
     * @return A new ViewHolder that holds a View representing a single item in the adapter.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.timeout_checkbox_item, parent, false)
    )

    /**
     * Binds the data to the ViewHolder at the specified position.
     *
     * @param holder The ViewHolder to bind the data to.
     * @param position The position of the item within the adapter's data set.
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

                onCheckedChangeListener?.onCheckedChanged(timeoutCheckBoxes)

                timeoutCheckBoxes.updateFirstItem()
            }
        }
    }

    /**
     * Returns the size of the list of [CheckBoxItem]s.
     *
     * @return the size of the list of [CheckBoxItem]s
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
     * @param checkBoxItem the item to be added
     */
    fun addCheckBox(checkBoxItem: CheckBoxItem) {
        val isInList = timeoutCheckBoxes.firstOrNull { item -> item.duration == checkBoxItem.duration } != null

        if (!isInList && timeoutCheckBoxes.add(checkBoxItem)) {
            timeoutCheckBoxes.sortBy { checkBox -> checkBox.duration }
            notifyItemRangeChanged(0, timeoutCheckBoxes.size)

            timeoutCheckBoxes.updateFirstItem()
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
     * @param checkBoxItem the item to be removed
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
