package com.parakey.keyboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dp(value: Int): Int = dp(value.toFloat())
fun Context.sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

fun colorWithAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

fun roundedDrawable(
    context: Context,
    color: Int,
    radiusDp: Float,
    strokeColor: Int? = null,
    strokeWidthDp: Float = 0f,
): GradientDrawable {
    val drawable = GradientDrawable()
    drawable.shape = GradientDrawable.RECTANGLE
    drawable.cornerRadius = context.dp(radiusDp).toFloat()
    drawable.setColor(color)
    if (strokeColor != null && strokeWidthDp > 0f) {
        drawable.setStroke(context.dp(strokeWidthDp).coerceAtLeast(1), strokeColor)
    }
    return drawable
}

fun rippleDrawable(background: GradientDrawable, rippleColor: Int = 0x3312B886): RippleDrawable {
    return RippleDrawable(ColorStateList.valueOf(rippleColor), background, null)
}

fun View.setRoundedBackground(
    color: Int,
    radiusDp: Float,
    rippleColor: Int? = null,
    strokeColor: Int? = null,
    strokeWidthDp: Float = 0f,
) {
    val bg = roundedDrawable(context, color, radiusDp, strokeColor, strokeWidthDp)
    background = if (rippleColor != null) rippleDrawable(bg, rippleColor) else bg
}

fun TextView.styleButton(
    context: Context,
    background: Int,
    textColor: Int,
    textSizeSp: Float = 14f,
    radiusDp: Float = 12f,
    horizontalPaddingDp: Float = 16f,
    verticalPaddingDp: Float = 11f,
    bold: Boolean = true,
) {
    setTextColor(textColor)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
    gravity = Gravity.CENTER
    setPadding(
        context.dp(horizontalPaddingDp),
        context.dp(verticalPaddingDp),
        context.dp(horizontalPaddingDp),
        context.dp(verticalPaddingDp),
    )
    if (bold) {
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    setRoundedBackground(background, radiusDp, rippleColor = colorWithAlpha(textColor, 40))
    isClickable = true
    isFocusable = true
}
