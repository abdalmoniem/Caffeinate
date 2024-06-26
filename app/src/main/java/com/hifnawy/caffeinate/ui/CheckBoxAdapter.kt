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

data class CheckBoxItem(var text: String, var isChecked: Boolean, var isEnabled: Boolean = false, var duration: Duration) : Serializable

class CheckBoxAdapter(val timeoutCheckBoxes: List<CheckBoxItem>) :
        RecyclerView.Adapter<CheckBoxAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val binding = TimeoutCheckboxItemBinding.bind(itemView)
        val checkBox: CheckBox = binding.timeoutCheckBox
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.timeout_checkbox_item, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder) {
            val item = timeoutCheckBoxes[position].apply {
                checkBox.text = text
                checkBox.isChecked = isChecked
                checkBox.isEnabled = isEnabled
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                item.isChecked = isChecked

                timeoutCheckBoxes.filter { checkBoxItem -> checkBoxItem.isChecked }.apply {
                    when (size) {
                        1    -> timeoutCheckBoxes.firstOrNull { checkBoxItem -> checkBoxItem.isChecked }?.apply {
                            isEnabled = false
                            notifyItemChanged(timeoutCheckBoxes.indexOf(this))
                        }

                        else -> timeoutCheckBoxes.firstOrNull { checkBoxItem -> !checkBoxItem.isEnabled }?.apply {
                            isEnabled = true
                            notifyItemChanged(timeoutCheckBoxes.indexOf(this))
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount() = timeoutCheckBoxes.size
}
