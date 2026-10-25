package com.jolt.mission

import com.jolt.data.Alarm

/** What has to be scanned to dismiss an alarm. */
sealed interface Mission {
    /** Your own photographed object. */
    data class Own(val objectId: Long) : Mission

    /** Any object of a category, e.g. any sink. */
    data class Generic(val category: Category) : Mission

    companion object {
        fun of(alarm: Alarm): Mission? =
            alarm.objectId?.let(::Own) ?: Category.fromName(alarm.category)?.let(::Generic)
    }
}
