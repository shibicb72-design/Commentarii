package com.shibi.commentarii.ui.lock

import java.time.LocalDate

/**
 * Remembers the navigation back stack (concrete routes such as `daily/2026-10-18` or `note/5`) while the app is locked,
 * so unlocking returns to the same screen. Only route strings are kept; no screen stays composed while locked.
 */
object NavRestore {
    private const val SEP = "\n"

    /** Routes worth restoring, in back-stack order; unknown or malformed ones are dropped. */
    fun sanitize(routes: List<String>): List<String> = routes.filter(::isValid)

    /** Steps to replay on a fresh NavHost whose start destination is today's daily page. */
    fun plan(routes: List<String>): List<RestoreStep> {
        val valid = sanitize(routes)
        return valid.mapIndexed { i, r -> RestoreStep(r, replaceStart = i == 0 && r.startsWith("daily/")) }
    }

    fun encode(routes: List<String>): String = sanitize(routes).joinToString(SEP)

    fun decode(saved: String): List<String> = sanitize(saved.split(SEP))

    private fun isValid(route: String): Boolean = when {
        route == "notes" || route == "search" || route == "settings" -> true
        route.startsWith("daily/") -> runCatching { LocalDate.parse(route.removePrefix("daily/")) }.isSuccess
        route.startsWith("note/") -> route.removePrefix("note/").toLongOrNull() != null
        else -> false
    }
}

/** [replaceStart] = true: navigate with the start destination popped (replacing it) rather than pushing. */
data class RestoreStep(val route: String, val replaceStart: Boolean)
