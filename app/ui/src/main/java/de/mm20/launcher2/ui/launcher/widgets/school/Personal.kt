package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Personal touches for "Prehľad dňa" (round 22, user: "greet me by my name, and on Saturday, Sunday,
 * Wednesday, Thursday, short on Friday, remind me to take care of my health"). Written here, not by Gemini,
 * so they appear every day, with or without a key. The name is the user's own entry in Settings > Škola.
 */
object Personal {
    private const val Prefs = "school_profile"

    fun name(context: Context): String =
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString("name", "").orEmpty()

    fun setName(context: Context, name: String) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putString("name", name.trim()).apply()
    }

    /** "Dobré ráno, Jozef" / "Ahoj, Jozef" / "Dobrý večer, Jozef"; without a name just the greeting. */
    fun greeting(name: String, now: LocalDateTime): String {
        val hello = when (now.hour) {
            in 4..9 -> "Dobré ráno"
            in 18..23 -> "Dobrý večer"
            else -> "Ahoj"
        }
        return if (name.isBlank()) hello else "$hello, $name"
    }

    /** The health line for today, or null (Monday, Tuesday). Varies by the week so it is not always the same. */
    fun health(now: LocalDateTime): String? {
        val week = now.toLocalDate().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        fun pick(vararg options: String) = options[week % options.size]
        return when (now.dayOfWeek) {
            DayOfWeek.WEDNESDAY -> pick(
                "Polovica týždňa: dnes aspoň 10 minút pohybu bez mobilu.",
                "Streda: vypi pohár vody navyše a daj si prestávku od obrazovky.",
            )
            DayOfWeek.THURSDAY -> pick(
                "Dnes choď spať skôr, zajtrajšok ti poďakuje.",
                "Štvrtok: krátka prechádzka po škole vyčistí hlavu.",
            )
            DayOfWeek.FRIDAY -> pick("Piatok: oddýchni si.", "Piatok: večer bez učenia.")
            DayOfWeek.SATURDAY -> pick(
                "Sobota: aspoň hodinu vonku, prechádzka alebo bicykel.",
                "Sobota: vyspi sa a niečo sa nauč len pre radosť.",
            )
            DayOfWeek.SUNDAY -> pick(
                "Nedeľa: pokojný deň, dosť spánku a taška pripravená na pondelok.",
                "Nedeľa: pohyb vonku a večer skôr do postele.",
            )
            else -> null
        }
    }
}
