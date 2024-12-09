package com.hifnawy.caffeinate.view

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.FragmentPrivacyPolicyBinding
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * A [BottomSheetDialogFragment] that displays the privacy policy of the application.
 *
 * This fragment provides a bottom sheet dialog that shows the privacy policy content.
 * It is responsible for rendering HTML content as text and managing the dialog's behavior
 * and appearance.
 *
 * The privacy policy content is extracted from a raw HTML resource file and displayed
 * within the fragment's view. The fragment also handles window insets for proper layout.
 *
 * @constructor Creates a new instance of [PrivacyPolicyFragment].
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see BottomSheetDialogFragment
 * @see FragmentPrivacyPolicyBinding
 */
class PrivacyPolicyFragment : BottomSheetDialogFragment() {

    /**
     * A lazy delegate that inflates the layout of this fragment and stores the result in a
     * [FragmentPrivacyPolicyBinding] instance.
     *
     * This property is initialized the first time it is accessed, and the result is reused
     * for all subsequent calls.
     *
     * @return [FragmentPrivacyPolicyBinding] The inflated layout of the fragment, wrapped in a [FragmentPrivacyPolicyBinding] instance.
     */
    private val binding by lazy { FragmentPrivacyPolicyBinding.inflate(layoutInflater) }

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
        ViewCompat.setOnApplyWindowInsetsListener(root) { root, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            val params = root.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin = systemBars.bottom + dragHandle.measuredHeight
            root.layoutParams = params

            insets
        }

        val html = getString(R.string.privacy_policy_content)
        val body = extractBodyContent(html)

        privacyPolicyContent.text = Html.fromHtml(body, HtmlCompat.FROM_HTML_OPTION_USE_CSS_COLORS)
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
            val dialog = (dialogInterface as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            dialog?.layoutParams?.height = windowHeight

            behavior.apply {
                isFitToContents = true
                dismissWithAnimation = true
                peekHeight = windowHeight * 2 / 5
                state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    /**
     * Reads the content of a raw resource file and returns it as a string.
     *
     * This method reads the content of a raw resource file using the provided
     * [Context] and [Int] resource ID. It returns the content of the file
     * as a string.
     *
     * @param context [Context] The context in which to read the raw resource.
     * @param rawResId [Int] The resource ID of the raw resource file to read.
     *
     * @return [String] The content of the raw resource file.
     */
    private fun readRawFileContent(context: Context, rawResId: Int): String {
        val inputStream = context.resources.openRawResource(rawResId)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val content = StringBuilder()
        reader.use { bufferedReader ->
            var line = bufferedReader.readLine()
            while (line != null) {
                content.append(line).append("\n")
                line = bufferedReader.readLine()
            }
        }

        return content.toString().trim()
    }

    /**
     * Extracts the content of the `<body>` element from the provided HTML string.
     *
     * This method takes an HTML string and extracts the content of the `<body>`
     * element from it. It returns the extracted content as a string.
     *
     * @param html [String] The HTML string to extract the content from.
     *
     * @return [String] The extracted content of the `<body>` element.
     */
    private fun extractBodyContent(html: String): String {
        val bodyRegex = Regex("<body.*?>(.*?)</body>", RegexOption.DOT_MATCHES_ALL)
        val matchResult = bodyRegex.find(html)
        return matchResult?.groups?.get(1)?.value?.trim() ?: ""
    }

    /**
     * Companion object for the [PrivacyPolicyFragment] class.
     *
     * Provides a static way to create a new instance of [PrivacyPolicyFragment] and
     * provides a way to access the fragment's static properties and methods.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    companion object {

        /**
         * Creates a new instance of [PrivacyPolicyFragment] and returns it.
         *
         * @return [PrivacyPolicyFragment] The newly created instance.
         */
        val newInstance
            get() = PrivacyPolicyFragment()
    }
}