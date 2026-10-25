package com.jolt.mission

/**
 * Generic missions: any object of the type passes, not just yours. [label] is the
 * object detector's COCO class name.
 */
enum class Category(val displayName: String, val label: String) {
    Sink("Sink", "sink"),
    Toilet("Toilet", "toilet"),
    Fridge("Fridge", "refrigerator"),
    Microwave("Microwave", "microwave"),
    Oven("Oven", "oven"),
    Toaster("Toaster", "toaster"),
    TV("TV", "tv");

    /** "a sink", "a TV". */
    val withArticle: String get() = "a ${displayName.lowercaseUnlessAcronym()}"

    companion object {
        fun fromName(name: String?): Category? = entries.firstOrNull { it.name == name }
    }
}

/** "Sink" -> "sink", but "TV" stays "TV". */
fun String.lowercaseUnlessAcronym(): String = if (all { it.isUpperCase() }) this else lowercase()
