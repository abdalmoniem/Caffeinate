package com.hifnawy.caffeinate.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber as Log

class CircularDurationView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val Int.dp: Int
        get() = (this * Resources.getSystem().displayMetrics.density).roundToInt()

    private var mHoursIndicator: CircularProgressIndicator
    private var mMinutesIndicator: CircularProgressIndicator
    private var mSecondsIndicator: CircularProgressIndicator

    private val mTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mText = ""
    private var mTextColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.WHITE)
    private var mTextStyle = Typeface.NORMAL
    private var mTextAlign = Paint.Align.CENTER
    private var mTextPadding = 0.dp
    private var mTextFontFamily = 0
    private var mTypeFace = Typeface.DEFAULT

    private var mIndicatorSize = -1
    private var mIndicatorsGapSize = 5.dp
    private var mIndicatorsColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.WHITE)
    private var mIndicatorsTrackColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.WHITE)
    private var mIndicatorsTrackThickness = 15.dp
    private var mIndicatorsTrackCornerRadius = 10.dp
    private var mIndicatorsTrackGapSize = 10.dp
    private var mIsAnimated = true

    private var mHoursIndicatorMax = 24
    private var mHoursIndicatorProgress = 0
    private var mHoursIndicatorColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.WHITE)
    private var mHoursIndicatorTrackColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.WHITE)
    private var mHoursIndicatorTrackThickness = -1
    private var mHoursIndicatorTrackCornerRadius = -1
    private var mHoursIndicatorTrackGapSize = -1

    private var mMinutesIndicatorProgress = 0
    private var mMinutesIndicatorColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, Color.WHITE)
    private var mMinutesIndicatorTrackColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, Color.WHITE)
    private var mMinutesIndicatorTrackThickness = -1
    private var mMinutesIndicatorTrackCornerRadius = -1
    private var mMinutesIndicatorTrackGapSize = -1

    private var mSecondsIndicatorProgress = 0
    private var mSecondsIndicatorColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, Color.WHITE)
    private var mSecondsIndicatorTrackColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer, Color.WHITE)
    private var mSecondsIndicatorTrackThickness = -1
    private var mSecondsIndicatorTrackCornerRadius = -1
    private var mSecondsIndicatorTrackGapSize = -1

    var text
        get() = mText
        set(value) {
            mText = value
            invalidate()
        }

    var textColor
        get() = mTextColor
        set(value) {
            mTextColor = value
            invalidate()
        }

    var textStyle
        get() = mTextStyle
        set(value) {
            mTextStyle = value
            invalidate()
        }

    var textAlign
        get() = mTextAlign
        set(value) {
            mTextAlign = value
            invalidate()
        }

    var textPadding
        get() = mTextPadding
        set(value) {
            mTextPadding = value
            invalidate()
        }

    var textFontFamily
        get() = mTextFontFamily
        set(value) {
            mTextFontFamily = value
            invalidate()
        }

    var indicatorSize
        get() = mIndicatorSize
        set(value) {
            mIndicatorSize = value

            updateIndicators()
        }

    var indicatorsGapSize
        get() = mIndicatorsGapSize
        set(value) {
            mIndicatorsGapSize = value

            updateIndicators()
        }

    var indicatorsColor
        get() = mIndicatorsColor
        set(value) {
            mIndicatorsColor = value
            mHoursIndicatorColor = value
            mMinutesIndicatorColor = value
            mSecondsIndicatorColor = value
            updateIndicators()
        }

    var indicatorsTrackColor
        get() = mIndicatorsTrackColor
        set(value) {
            mIndicatorsTrackColor = value
            mHoursIndicatorTrackColor = value
            mMinutesIndicatorTrackColor = value
            mSecondsIndicatorTrackColor = value
            updateIndicators()
        }

    var indicatorsTrackThickness
        get() = mIndicatorsTrackThickness
        set(value) {
            mIndicatorsTrackThickness = value
            mHoursIndicatorTrackThickness = value
            mMinutesIndicatorTrackThickness = value
            mSecondsIndicatorTrackThickness = value
            updateIndicators()
        }

    var indicatorsTrackCornerRadius
        get() = mIndicatorsTrackCornerRadius
        set(value) {
            mIndicatorsTrackCornerRadius = value
            mHoursIndicatorTrackCornerRadius = value
            mMinutesIndicatorTrackCornerRadius = value
            mSecondsIndicatorTrackCornerRadius = value
            updateIndicators()
        }

    var indicatorsTrackGapSize
        get() = mIndicatorsTrackGapSize
        set(value) {
            mIndicatorsTrackGapSize = value
            mHoursIndicatorTrackGapSize = value
            mMinutesIndicatorTrackGapSize = value
            mSecondsIndicatorTrackGapSize = value
            updateIndicators()
        }

    var isAnimated
        get() = mIsAnimated
        set(value) {
            mIsAnimated = value
        }

    var hoursIndicatorMax
        get() = mHoursIndicatorMax
        set(value) {
            mHoursIndicatorMax = value
            mHoursIndicator.max = value
            invalidate()
        }

    var hoursIndicatorProgress
        get() = mHoursIndicatorProgress
        set(value) {
            mHoursIndicatorProgress = value
            mHoursIndicator.setProgressCompat(value, mIsAnimated)
        }

    var hoursIndicatorColor
        get() = mHoursIndicatorColor
        set(value) {
            mHoursIndicatorColor = value
        }

    var hoursIndicatorTrackColor
        get() = mHoursIndicatorTrackColor
        set(value) {
            mHoursIndicatorTrackColor = value
        }

    var hoursIndicatorTrackThickness
        get() = mHoursIndicatorTrackThickness
        set(value) {
            mHoursIndicatorTrackThickness = value
        }

    var hoursIndicatorTrackCornerRadius
        get() = mHoursIndicatorTrackCornerRadius
        set(value) {
            mHoursIndicatorTrackCornerRadius = value
        }

    var hoursIndicatorTrackGapSize
        get() = mHoursIndicatorTrackGapSize
        set(value) {
            mHoursIndicatorTrackGapSize = value
        }

    var minutesIndicatorColor
        get() = mMinutesIndicatorColor
        set(value) {
            mMinutesIndicatorColor = value
        }

    var minutesIndicatorProgress
        get() = mMinutesIndicatorProgress
        set(value) {
            mMinutesIndicatorProgress = value
            mMinutesIndicator.setProgressCompat(value, mIsAnimated)
        }

    var minutesIndicatorTrackColor
        get() = mMinutesIndicatorTrackColor
        set(value) {
            mMinutesIndicatorTrackColor = value
        }

    var minutesIndicatorTrackThickness
        get() = mMinutesIndicatorTrackThickness
        set(value) {
            mMinutesIndicatorTrackThickness = value
        }

    var minutesIndicatorTrackCornerRadius
        get() = mMinutesIndicatorTrackCornerRadius
        set(value) {
            mMinutesIndicatorTrackCornerRadius = value
        }

    var minutesIndicatorTrackGapSize
        get() = mMinutesIndicatorTrackGapSize
        set(value) {
            mMinutesIndicatorTrackGapSize = value
        }

    var secondsIndicatorProgress
        get() = mSecondsIndicatorProgress
        set(value) {
            mSecondsIndicatorProgress = value
            mSecondsIndicator.setProgressCompat(value, mIsAnimated)
        }

    var secondsIndicatorColor
        get() = mSecondsIndicatorColor
        set(value) {
            mSecondsIndicatorColor = value
        }

    var secondsIndicatorTrackColor
        get() = mSecondsIndicatorTrackColor
        set(value) {
            mSecondsIndicatorTrackColor = value
        }

    var secondsIndicatorTrackThickness
        get() = mSecondsIndicatorTrackThickness
        set(value) {
            mSecondsIndicatorTrackThickness = value
        }

    var secondsIndicatorTrackCornerRadius
        get() = mSecondsIndicatorTrackCornerRadius
        set(value) {
            mSecondsIndicatorTrackCornerRadius = value
        }

    var secondsIndicatorTrackGapSize
        get() = mSecondsIndicatorTrackGapSize
        set(value) {
            mSecondsIndicatorTrackGapSize = value
        }

    var progress: Duration
        get() = (hoursIndicatorProgress.toLong() * 3600 + minutesIndicatorProgress.toLong() * 60 + secondsIndicatorProgress.toLong()).seconds
        set(value) {
            // stagger indeterminate animations
            if (value.isInfinite()) findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                mHoursIndicator.isIndeterminate = true
                delay(300)
                mMinutesIndicator.isIndeterminate = true
                delay(300)
                mSecondsIndicator.isIndeterminate = true
            } else {
                value.toComponents { hours, minutes, seconds, _ ->
                    mHoursIndicator.isIndeterminate = false
                    mMinutesIndicator.isIndeterminate = false
                    mSecondsIndicator.isIndeterminate = false

                    if (hours == 0L) hoursIndicatorMax = 24
                    hoursIndicatorProgress = hours.toInt()
                    minutesIndicatorProgress = minutes
                    secondsIndicatorProgress = seconds
                }
            }

            invalidate()
        }

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.CircularDurationView, defStyleAttr, 0)
        mText = attributes.getString(R.styleable.CircularDurationView_text) ?: ""
        mTextColor = attributes.getColor(R.styleable.CircularDurationView_textColor, textColor)
        mTextStyle = when (val txtStyle = attributes.getInt(R.styleable.CircularDurationView_textStyle, -1)) {
            -1   -> textStyle
            else -> txtStyle
        }
        mTextAlign = when (val txtAlign = attributes.getInt(R.styleable.CircularDurationView_textAlign, -1)) {
            -1   -> textAlign
            else -> Paint.Align.entries[txtAlign]
        }
        mTextPadding = attributes.getDimension(R.styleable.CircularDurationView_textPadding, textPadding.toFloat()).roundToInt()
        mTextFontFamily = attributes.getResourceId(R.styleable.CircularDurationView_textFontFamily, textFontFamily)
        when (mTextFontFamily) {
            0    -> Log.e("Font resource not set or invalid")
            else -> {
                mTypeFace = ResourcesCompat.getFont(context, mTextFontFamily) ?: Typeface.DEFAULT
                Log.d("Font resource ID: $mTextFontFamily")
                Log.d("Font resource: $mTypeFace")
            }
        }
        mTypeFace = Typeface.create(mTypeFace, mTextStyle)

        mIndicatorSize = attributes.getDimension(R.styleable.CircularDurationView_indicatorSize, indicatorSize.toFloat()).roundToInt()
        mIndicatorsGapSize =
                attributes.getDimension(R.styleable.CircularDurationView_indicatorsGapSize, indicatorsGapSize.toFloat()).roundToInt()
        mIndicatorsColor = attributes.getColor(R.styleable.CircularDurationView_indicatorsColor, indicatorsColor)
        mIndicatorsTrackColor = attributes.getColor(R.styleable.CircularDurationView_indicatorsTrackColor, indicatorsTrackColor)
        mIndicatorsTrackThickness =
                attributes.getDimension(R.styleable.CircularDurationView_indicatorsTrackThickness, indicatorsTrackThickness.toFloat()).roundToInt()
        mIndicatorsTrackCornerRadius =
                attributes.getDimension(R.styleable.CircularDurationView_indicatorsTrackCornerRadius, indicatorsTrackCornerRadius.toFloat())
                    .roundToInt()
        mIndicatorsTrackGapSize =
                attributes.getDimension(R.styleable.CircularDurationView_indicatorsTrackGapSize, indicatorsTrackGapSize.toFloat()).roundToInt()
        mIsAnimated = attributes.getBoolean(R.styleable.CircularDurationView_animated, isAnimated)

        mHoursIndicatorMax = attributes.getInteger(R.styleable.CircularDurationView_hoursIndicatorMax, hoursIndicatorMax)
        mHoursIndicatorProgress = attributes.getInteger(R.styleable.CircularDurationView_hoursIndicatorProgress, hoursIndicatorProgress)
        mHoursIndicatorColor = attributes.getColor(R.styleable.CircularDurationView_hoursIndicatorColor, indicatorsColor)
        mHoursIndicatorTrackColor = attributes.getColor(R.styleable.CircularDurationView_hoursIndicatorTrackColor, indicatorsTrackColor)
        mHoursIndicatorTrackThickness =
                attributes.getDimension(R.styleable.CircularDurationView_hoursIndicatorTrackThickness, indicatorsTrackThickness.toFloat())
                    .roundToInt()
        mHoursIndicatorTrackCornerRadius =
                attributes.getDimension(R.styleable.CircularDurationView_hoursIndicatorTrackCornerRadius, indicatorsTrackCornerRadius.toFloat())
                    .roundToInt()
        mHoursIndicatorTrackGapSize =
                attributes.getDimension(R.styleable.CircularDurationView_hoursIndicatorTrackGapSize, indicatorsTrackGapSize.toFloat())
                    .roundToInt()

        mMinutesIndicatorProgress = attributes.getInteger(R.styleable.CircularDurationView_minutesIndicatorProgress, minutesIndicatorProgress)
        mMinutesIndicatorColor = attributes.getColor(R.styleable.CircularDurationView_minutesIndicatorColor, indicatorsColor)
        mMinutesIndicatorTrackColor = attributes.getColor(R.styleable.CircularDurationView_minutesIndicatorTrackColor, indicatorsTrackColor)
        mMinutesIndicatorTrackThickness =
                attributes.getDimension(R.styleable.CircularDurationView_minutesIndicatorTrackThickness, indicatorsTrackThickness.toFloat())
                    .roundToInt()
        mMinutesIndicatorTrackCornerRadius =
                attributes.getDimension(R.styleable.CircularDurationView_minutesIndicatorTrackCornerRadius, indicatorsTrackCornerRadius.toFloat())
                    .roundToInt()
        mMinutesIndicatorTrackGapSize =
                attributes.getDimension(R.styleable.CircularDurationView_minutesIndicatorTrackGapSize, indicatorsTrackGapSize.toFloat())
                    .roundToInt()

        mSecondsIndicatorProgress = attributes.getInteger(R.styleable.CircularDurationView_secondsIndicatorProgress, secondsIndicatorProgress)
        mSecondsIndicatorColor = attributes.getColor(R.styleable.CircularDurationView_secondsIndicatorColor, indicatorsColor)
        mSecondsIndicatorTrackColor = attributes.getColor(R.styleable.CircularDurationView_secondsIndicatorTrackColor, indicatorsTrackColor)
        mSecondsIndicatorTrackThickness =
                attributes.getDimension(R.styleable.CircularDurationView_secondsIndicatorTrackThickness, indicatorsTrackThickness.toFloat())
                    .roundToInt()
        mSecondsIndicatorTrackCornerRadius =
                attributes.getDimension(R.styleable.CircularDurationView_secondsIndicatorTrackCornerRadius, indicatorsTrackCornerRadius.toFloat())
                    .roundToInt()
        mSecondsIndicatorTrackGapSize =
                attributes.getDimension(R.styleable.CircularDurationView_secondsIndicatorTrackGapSize, indicatorsTrackGapSize.toFloat())
                    .roundToInt()
        attributes.recycle()

        mHoursIndicator = CircularProgressIndicator(context)
        mMinutesIndicator = CircularProgressIndicator(context)
        mSecondsIndicator = CircularProgressIndicator(context)

        addView(mHoursIndicator)
        addView(mMinutesIndicator)
        addView(mSecondsIndicator)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        val height = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val size = min(width, height)

        if (mIndicatorSize == -1) mIndicatorSize = size

        updateIndicators()

        val viewWidth = mIndicatorSize + paddingStart + paddingEnd
        val viewHeight = mIndicatorSize + paddingTop + paddingBottom

        setMeasuredDimension(viewWidth, viewHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (changed) updateIndicators()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        val textWidth = mSecondsIndicator.indicatorSize - mSecondsIndicator.trackThickness * 2
        val text = mText.takeIf { it.isNotEmpty() } ?: progress.toFormattedTime()
        mTextPaint.apply {
            color = mTextColor
            textAlign = mTextAlign
            textSize = textWidth * 0.5f
            typeface = mTypeFace
        }

        var measuredWidth = mTextPaint.measureText(text)
        while (measuredWidth + mTextPadding > textWidth) {
            mTextPaint.textSize -= 1f
            measuredWidth = mTextPaint.measureText(text)
        }

        val x = width / 2f
        val y = height / 2f - (mTextPaint.fontMetrics.descent + mTextPaint.fontMetrics.ascent) / 2
        canvas.drawText(text, x, y, mTextPaint)
    }

    private fun updateIndicators() {
        mHoursIndicator.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)

            isIndeterminate = false
            indicatorSize = mIndicatorSize
            trackColor = mHoursIndicatorTrackColor
            if (mHoursIndicatorTrackThickness != -1) trackThickness = mHoursIndicatorTrackThickness
            if (mHoursIndicatorTrackCornerRadius != -1) trackCornerRadius = mHoursIndicatorTrackCornerRadius
            if (mHoursIndicatorTrackGapSize != -1) indicatorTrackGapSize = mHoursIndicatorTrackGapSize
            setIndicatorColor(mHoursIndicatorColor)
            showAnimationBehavior = CircularProgressIndicator.SHOW_OUTWARD
            progress = mHoursIndicatorProgress
            max = mHoursIndicatorMax
        }

        mMinutesIndicator.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)

            isIndeterminate = false
            indicatorSize = mIndicatorSize - (mHoursIndicatorTrackThickness * 2) - mIndicatorsGapSize
            trackColor = mMinutesIndicatorTrackColor
            if (mMinutesIndicatorTrackThickness != -1) trackThickness = mMinutesIndicatorTrackThickness
            if (mMinutesIndicatorTrackCornerRadius != -1) trackCornerRadius = mMinutesIndicatorTrackCornerRadius
            if (mMinutesIndicatorTrackGapSize != -1) indicatorTrackGapSize = mMinutesIndicatorTrackGapSize
            setIndicatorColor(mMinutesIndicatorColor)
            showAnimationBehavior = CircularProgressIndicator.SHOW_OUTWARD
            progress = mMinutesIndicatorProgress
            max = 59
        }

        mSecondsIndicator.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)

            isIndeterminate = false
            indicatorSize = mIndicatorSize - (mHoursIndicatorTrackThickness * 2) - (mMinutesIndicatorTrackThickness * 2) - (mIndicatorsGapSize * 2)
            trackColor = mSecondsIndicatorTrackColor
            if (mSecondsIndicatorTrackThickness != -1) trackThickness = mSecondsIndicatorTrackThickness
            if (mSecondsIndicatorTrackCornerRadius != -1) trackCornerRadius = mSecondsIndicatorTrackCornerRadius
            if (mSecondsIndicatorTrackGapSize != -1) indicatorTrackGapSize = mSecondsIndicatorTrackGapSize
            setIndicatorColor(mSecondsIndicatorColor)
            showAnimationBehavior = CircularProgressIndicator.SHOW_OUTWARD
            progress = mSecondsIndicatorProgress
            max = 59
        }

        invalidate()
    }
}