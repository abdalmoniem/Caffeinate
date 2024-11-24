package com.hifnawy.caffeinate.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hifnawy.caffeinate.view.MainActivity

/**
 * A [ViewModel] that provides data and functionality for the [MainActivity].
 *
 * This class holds the data and functionality that is used by the [MainActivity].
 * It is responsible for exposing the data that is needed by the [MainActivity] and
 * for providing the functionality that is needed to manipulate the data.
 *
 * @author AbdAlMoniem AlHifnawy
 */
class MainActivityViewModel : ViewModel() {

    /**
     * The vertical offset of the app bar.
     *
     * This field is used to store the vertical offset of the app bar. It is used to adjust the padding
     * of the scroll view based on the position of the app bar.
     *
     * This field is a [MutableLiveData] because it is used to communicate with the [MainActivity].
     * The [MainActivity] observes this field to update the padding of the scroll view.
     */
    val appBarVerticalOffset = MutableLiveData(0)

    /**
     * Indicates whether the restart button is enabled.
     *
     * This field is used to store the state of the restart button. It is used to enable or disable the
     * button based on the state of the service.
     *
     * This field is a [MutableLiveData] because it is used to communicate with the [MainActivity].
     * The [MainActivity] observes this field to update the state of the restart button.
     */
    val isRestartButtonEnabled = MutableLiveData(false)
}