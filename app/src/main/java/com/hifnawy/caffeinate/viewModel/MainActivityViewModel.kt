package com.hifnawy.caffeinate.viewModel

import androidx.lifecycle.ViewModel

/**
 * A [ViewModel] used to store state related to the main activity.
 *
 * The main activity uses this view model to store state related to the app bar's vertical offset.
 * This state is used to adjust the padding of the scroll view based on the position of the app bar.
 *
 * @author AbdAlMoniem AlHifnawy
 */
class MainActivityViewModel : ViewModel() {

    /**
     * The vertical offset of the app bar.
     *
     * This field is used to store the vertical offset of the app bar. It is used to adjust the
     * padding of the scroll view based on the position of the app bar.
     */
    var appBarVerticalOffset = 0

    /**
     * Indicates whether the restart button animation has started.
     *
     * This field is used to track the state of the restart button animation.
     * It is set to `true` when the animation begins and `false` when it ends.
     */
    var isRestartButtonAnimationStarted = false
}