package io.github.unidentified24.spock.features

import java.time.*

object ThemeSchedule {
    fun select(options:ComfortOptions,available:List<String>,now:ZonedDateTime):String? {
        if(options.dayTheme in available&&options.nightTheme in available){
            val minute=now.hour*60+now.minute
            val day=if(options.dayMinute<=options.nightMinute)minute>=options.dayMinute&&minute<options.nightMinute else minute>=options.dayMinute||minute<options.nightMinute
            return if(day)options.dayTheme else options.nightTheme
        }
        val favorites=available.filter{it in options.favoriteThemes}
        if(options.rotateThemes&&favorites.isNotEmpty())return favorites[Math.floorMod(now.toLocalDate().toEpochDay(),favorites.size.toLong()).toInt()]
        return null
    }
}
