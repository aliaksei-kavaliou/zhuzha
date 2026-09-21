package com.inkfirst.core

/**
 * Which measurement system a book writes its examples in.
 *
 * Not a detail. A generated chapter is full of concrete examples — that is
 * the whole pedagogy — and an example measured in degrees Fahrenheit is one
 * the reader has to convert before they can picture it. The model defaults to
 * US customary because most of its training text does, so left unsaid the
 * book quietly assumes an American reader.
 *
 * Stored per course rather than per device, alongside [Course.language] and
 * for the same reason: it is a property of the BOOK, fixed when the book was
 * commissioned, so a chapter generated next month matches the ones already
 * written.
 */
data class MeasurementSystem(
    val key: String,
    val label: String,
    /** Appended to every generation prompt verbatim. */
    val rule: String,
)

val MEASUREMENT_SYSTEMS: List<MeasurementSystem> = listOf(
    MeasurementSystem(
        key = "metric",
        label = "Metric — °C, km, kg",
        rule = "Measurements: metric throughout. Degrees Celsius, millimetres / " +
            "metres / kilometres, grams / kilograms / tonnes, millilitres / litres, " +
            "24-hour clock. Never Fahrenheit, miles, feet, inches, pounds, ounces, " +
            "pints or gallons — not in prose, not in an exercise, not in a figure's " +
            "alt text. The one exception is a unit that is part of the thing's name " +
            "rather than a measurement of it (a 19-inch rack, 35 mm film); write " +
            "those as they are called.",
    ),
    MeasurementSystem(
        key = "imperial",
        label = "US customary — °F, miles, lb",
        rule = "Measurements: US customary throughout. Degrees Fahrenheit, inches / " +
            "feet / miles, ounces / pounds, cups / pints / gallons, 12-hour clock.",
    ),
)

const val DEFAULT_MEASUREMENT_SYSTEM: String = "metric"

/** The rule line for a stored [Course.units] key, falling back to metric. */
fun measurementRule(key: String): String =
    (MEASUREMENT_SYSTEMS.firstOrNull { it.key == key } ?: MEASUREMENT_SYSTEMS.first()).rule
