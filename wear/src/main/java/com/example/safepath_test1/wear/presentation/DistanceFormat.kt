package com.example.safepath_test1.wear.presentation

import java.util.Locale
import kotlin.math.roundToInt

/** 1km 미만은 10m 단위 "430m", 이상은 소수 첫째 자리 "1.2km". */
internal fun formatDistance(meters: Double): String =
    if (meters < 1000.0) {
        "${(meters / 10).roundToInt() * 10}m"
    } else {
        String.format(Locale.US, "%.1fkm", meters / 1000.0)
    }
