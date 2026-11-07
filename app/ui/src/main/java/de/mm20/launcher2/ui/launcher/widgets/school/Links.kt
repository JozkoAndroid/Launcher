package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Where taps go. Device test: Schoolhelp opened the website instead of the app, articles left the launcher. */
internal object Links {
    const val SchoolhelpUrl = "https://lessons.labeltranslator.com/"

    /** The installed Schoolhelp app, found by its name (its package id is not public). */
    fun schoolhelpPackage(context: Context): String? = runCatching {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .firstOrNull { it.loadLabel(pm).toString().contains("schoolhelp", ignoreCase = true) }
            ?.activityInfo?.packageName
    }.getOrNull()

    /** Opens Schoolhelp in its app; the website only if the app is not installed. */
    fun openSchoolhelp(context: Context) {
        val pm = context.packageManager
        val pkg = schoolhelpPackage(context)
        val intent = when {
            pkg == null -> Intent(Intent.ACTION_VIEW, Uri.parse(SchoolhelpUrl))
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(SchoolhelpUrl)).setPackage(pkg)
                .takeIf { it.resolveActivity(pm) != null }
                ?: pm.getLaunchIntentForPackage(pkg)
        } ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Articles open in a browser sheet over the launcher (Custom Tabs), not in a separate browser. */
    fun openArticle(context: Context, url: String) {
        runCatching {
            val tab = CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_ON).build()
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(context, Uri.parse(url))
        }.onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
