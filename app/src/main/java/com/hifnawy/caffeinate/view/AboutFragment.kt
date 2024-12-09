package com.hifnawy.caffeinate.view

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.transition.Explode
import android.transition.TransitionManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.AnticipateOvershootInterpolator
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.hifnawy.caffeinate.BuildConfig
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.FragmentAboutBinding
import com.hifnawy.caffeinate.utils.ViewExtensionFunctions.isVisible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.material.R as materialR

/**
 * A [BottomSheetDialogFragment] that displays information about the application.
 *
 * This fragment provides a bottom sheet dialog that shows details about the application,
 * including its version, author, and other relevant information.
 *
 * @author AbdAlMoniem AlHifnawy
 */
class AboutFragment : BottomSheetDialogFragment() {

    /**
     * A lazy delegate that inflates the layout of this fragment and stores the result in a
     * [FragmentAboutBinding] instance.
     *
     * This property is initialized the first time it is accessed, and the result is reused
     * for all subsequent calls.
     *
     * @return [FragmentAboutBinding] The inflated layout of the fragment, wrapped in a [FragmentAboutBinding] instance.
     */
    private val binding: FragmentAboutBinding by lazy { FragmentAboutBinding.inflate(layoutInflater) }

    /**
     * A lazy delegate that initializes the bottom sheet callback.
     *
     * This property is initialized the first time it is accessed, and the result is reused
     * for all subsequent calls.
     *
     * @return [BottomSheetBehavior.BottomSheetCallback] The initialized bottom sheet callback.
     */
    private val bottomSheetCallback by lazy {
        object : BottomSheetBehavior.BottomSheetCallback() {
            /**
             * Called when the bottom sheet changes its state.
             *
             * @param bottomSheet The bottom sheet view.
             * @param newState The new state. This will be one of:
             * - [BottomSheetBehavior.STATE_EXPANDED]
             * - [BottomSheetBehavior.STATE_HALF_EXPANDED].
             * - [BottomSheetBehavior.STATE_COLLAPSED]
             * - [BottomSheetBehavior.STATE_DRAGGING]
             * - [BottomSheetBehavior.STATE_SETTLING]
             * - [BottomSheetBehavior.STATE_HIDDEN]
             */
            override fun onStateChanged(bottomSheet: View, newState: Int) = Unit

            /**
             * Called when the bottom sheet is being dragged.
             *
             * @param bottomSheet The bottom sheet view.
             * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset increases
             * as this bottom sheet is moving upward. From 0 to 1 the sheet is between collapsed and
             * expanded states and from -1 to 0 it is between hidden and collapsed states.
             */
            override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
        }
    }

    /**
     * A utility property that converts a given integer value to a size in display pixels (DP).
     *
     * This property multiplies the given integer value by the device's display density and
     * returns the result as an integer. It is a convenience method for converting a size
     * in DP to a size in pixels.
     *
     * @return [Int] The size in DP, as an integer.
     */
    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    /**
     * A utility property that returns the height of the window in display pixels (DP).
     *
     * This property calculates the height of the window by taking into account the display height
     * and the size of the top and bottom system bars. It takes into account the display density
     * and returns the result as an integer.
     *
     * @return [Int] The height of the window in DP, as an integer.
     */
    private val windowHeight: Int
        get() {
            val height = resources.displayMetrics.heightPixels

            with(requireView().rootWindowInsets) {
                @Suppress("DEPRECATION")
                val topInset = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top
                    else                                           -> systemWindowInsetTop
                }

                @Suppress("DEPRECATION")
                val bottomInset = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).bottom
                    else                                           -> systemWindowInsetBottom
                }

                return height + topInset + bottomInset
            }
        }

    /**
     * Called to have the fragment instantiate its user interface view.
     * This is optional, and non-graphical fragments can return null. This will be called between
     * [onCreate] and [onViewCreated].
     *
     * A default View can be returned by calling [Fragment][androidx.fragment.app.Fragment]
     * in your constructor. Otherwise, this method returns null.
     *
     * It is recommended to **only** inflate the layout in this method and move
     * logic that operates on the returned View to [.onViewCreated].
     *
     * If you return a View from here, you will later be called in
     * [.onDestroyView] when the view is being released.
     *
     * @param inflater The LayoutInflater object that can be used to inflate
     * any views in the fragment,
     * @param container If non-null, this is the parent view that the fragment's
     * UI should be attached to.  The fragment should not add the view itself,
     * but this can be used to generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     *
     * @return Return the View for the fragment's UI, or null.
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = binding.root

    /**
     * Called immediately after [onCreateView]
     * has returned, but before any saved state has been restored in to the view.
     * This gives subclasses a chance to initialize themselves once
     * they know their view hierarchy has been completely created.  The fragment's
     * view hierarchy is not however attached to its parent at this point.
     *
     * @param view The View returned by [onCreateView].
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        @SuppressLint("SetTextI18n")
        versionCode.text = getString(R.string.about_version_code, BuildConfig.VERSION_CODE.toString())
        versionName.text = getString(R.string.about_version_name, BuildConfig.VERSION_NAME)

        content.children.forEach { childView ->
            (childView as? MaterialButton)?.setOnClickListener { button ->
                button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(button.tag as? String)))
            }
        }
    }

    /**
     * Called to create a dialog for the fragment.
     *
     * This method creates a new instance of [BottomSheetDialog] using the current context
     * and applies configuration settings to customize its behavior and appearance.
     *
     * @param savedInstanceState [Bundle] The last saved instance state of the Fragment, or null if this
     * is a freshly created Fragment.
     *
     * @return [BottomSheetDialog] The created dialog instance with applied custom settings.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(requireContext()).apply {
        setOnShowListener { dialogInterface ->
            val dialog = (dialogInterface as BottomSheetDialog).findViewById<View>(materialR.id.design_bottom_sheet)
            // dialog?.layoutParams?.height = windowHeight * 3 / 5
            dialog?.layoutParams?.height = (binding.root.height + binding.root.height * 0.3f).toInt()

            with(behavior) {
                skipCollapsed = true
                isFitToContents = true
                dismissWithAnimation = true
                peekHeight = dialog?.layoutParams?.height ?: (windowHeight * 3 / 5)
                state = BottomSheetBehavior.STATE_EXPANDED

                addBottomSheetCallback(bottomSheetCallback)
            }

            animateContent()
        }
    }

    private fun animateContent(animationDuration: Long = 150): Unit = with(binding) {
        val transition = Explode().apply {
            duration = animationDuration
            startDelay = animationDuration / 2
            interpolator = AnticipateOvershootInterpolator(0.7f)
        }

        contentCard.isVisible = false
        content.children.forEach { it.isVisible = false }

        TransitionManager.beginDelayedTransition(root, transition)
        contentCard.isVisible = true

        lifecycleScope.launch {
            content.children.forEach { childView ->
                delay(transition.startDelay)

                TransitionManager.beginDelayedTransition(root, transition)
                childView.isVisible = true
            }
        }
    }

    /**
     * Companion object for the [AboutFragment] class.
     *
     * Provides a static way to create a new instance of [AboutFragment] and
     * provides a way to access the fragment's static properties and methods.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    companion object {

        /**
         * Creates a new instance of [AboutFragment] and returns it.
         *
         * @return [AboutFragment] The newly created instance.
         */
        val newInstance
            get() = AboutFragment()
    }
}