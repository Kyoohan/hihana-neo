package com.yhjang.timetable.widget

import androidx.annotation.DrawableRes
import com.yhjang.timetable.R

/** [com.yhjang.timetable.Block.iconKey] 값을 실제 벡터 드로어블 리소스로 매핑합니다. */
@DrawableRes
fun iconResFor(key: String): Int = when (key) {
    "casino" -> R.drawable.ic_casino
    "functions" -> R.drawable.ic_functions
    "science" -> R.drawable.ic_science
    "bolt" -> R.drawable.ic_bolt
    "smart_toy" -> R.drawable.ic_smart_toy
    "query_stats" -> R.drawable.ic_query_stats
    "translate" -> R.drawable.ic_translate
    "sports_soccer" -> R.drawable.ic_sports_soccer
    "free_breakfast" -> R.drawable.ic_free_breakfast
    "meeting_room" -> R.drawable.ic_meeting_room
    "chair" -> R.drawable.ic_chair
    "local_library" -> R.drawable.ic_local_library
    "hotel" -> R.drawable.ic_hotel
    "school" -> R.drawable.ic_school
    "directions_run" -> R.drawable.ic_directions_run
    "place" -> R.drawable.ic_place
    "bedtime" -> R.drawable.ic_bedtime
    else -> R.drawable.ic_menu_book
}
