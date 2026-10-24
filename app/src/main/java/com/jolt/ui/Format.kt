package com.jolt.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

data class ClockText(val time: String, val suffix: String?)

fun formatClock(context: Context, hour: Int, minute: Int): ClockText =
    if (DateFormat.is24HourFormat(context)) {
        ClockText("%02d:%02d".format(hour, minute), null)
    } else {
        val h = if (hour % 12 == 0) 12 else hour % 12
        ClockText("%d:%02d".format(h, minute), if (hour < 12) "AM" else "PM")
    }

const val EVERY_DAY = 0b1111111
const val WEEKDAYS = 0b0011111
const val WEEKENDS = 0b1100000

fun daysSummary(days: Int): String = when (days) {
    0 -> "Once"
    EVERY_DAY -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    else -> DayOfWeek.entries
        .filter { days and (1 shl (it.value - 1)) != 0 }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

/** "7 h 32 min", "45 min", "less than a minute". */
fun formatDuration(millis: Long): String {
    val minutes = (millis + 59_999) / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        minutes <= 1 -> "less than a minute"
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** Current wall-clock time, ticking over on each minute boundary while on screen. */
@Composable
fun minuteTicker(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(60_000 - System.currentTimeMillis() % 60_000)
        value = System.currentTimeMillis()
    }
}

@Composable
fun thumbnail(path: String?, maxSide: Int = 256): State<ImageBitmap?> = produceState<ImageBitmap?>(null, path) {
    value = path?.let { withContext(Dispatchers.IO) { decodeSampled(it, maxSide)?.asImageBitmap() } }
}

private fun decodeSampled(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
