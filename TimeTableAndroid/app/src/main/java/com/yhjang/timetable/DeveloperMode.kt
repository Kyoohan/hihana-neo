package com.yhjang.timetable

import android.content.Context

/** 개발자 모드 — 설정의 "버전 x.y" 줄을 5번 누르면 켜집니다. 릴리스 빌드에서도 쓸 수 있는 기기별 스위치. */
object DeveloperMode {
    private const val PREFS = "developer"
    private const val KEY = "enabled"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY, false)
    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY, enabled).apply()
}
