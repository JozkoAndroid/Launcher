package de.mm20.launcher2.ui.launcher.widgets.school.context

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Process
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Device data for the context cards. Every source fails soft: no permission or error = no card. */
internal object ContextSources {

    const val InstagramPackage = "com.instagram.android"
    const val SamsungHealthPackage = "com.sec.android.app.shealth"
    val StepsPermission: String = HealthPermission.getReadPermission(StepsRecord::class)

    // --- Usage access (Instagram minutes and mobile data) ---

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun instagramMinutes(context: Context, nowMs: Long, zone: ZoneId): Long? = withContext(Dispatchers.IO) {
        if (!hasUsageAccess(context)) return@withContext null
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val events = usm.queryEvents(dayStart, nowMs)
            val e = UsageEvents.Event()
            val list = mutableListOf<Pair<Long, Boolean>>()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.packageName != InstagramPackage) continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> list += e.timeStamp to true
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> list += e.timeStamp to false
                }
            }
            ContextRules.foregroundMinutes(list, nowMs, dayStart)
        }.getOrNull()
    }

    suspend fun mobileBytes(context: Context, fromMs: Long, toMs: Long): Long? = withContext(Dispatchers.IO) {
        if (!hasUsageAccess(context)) return@withContext null
        runCatching {
            val nsm = context.getSystemService(NetworkStatsManager::class.java)
            val b: NetworkStats.Bucket = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, fromMs, toMs)
            b.rxBytes + b.txBytes
        }.getOrNull()
    }

    // --- Contacts (birthdays) ---

    fun hasContacts(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    suspend fun birthdays(context: Context, today: LocalDate): List<ContextCard.Birthday> = withContext(Dispatchers.IO) {
        if (!hasContacts(context)) return@withContext emptyList()
        runCatching {
            val out = mutableListOf<ContextCard.Birthday>()
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val date = c.getString(2) ?: continue
                    if (ContextRules.isBirthdayToday(date, today)) {
                        out += ContextCard.Birthday(c.getString(1) ?: continue, c.getLong(0))
                    }
                }
            }
            out.distinctBy { it.contactId }
        }.getOrDefault(emptyList())
    }

    // --- Name days (official Slovak calendar, bundled) ---

    @Volatile private var nameDays: Map<String, List<String>>? = null

    fun nameDays(context: Context): Map<String, List<String>> = nameDays ?: runCatching {
        val json = context.assets.open("namedays_sk.json").bufferedReader().use { it.readText() }
        kotlinx.serialization.json.Json.decodeFromString<Map<String, List<String>>>(json)
    }.getOrDefault(emptyMap()).also { nameDays = it }

    /** First contact whose first name has its name day today: (display name, contact id). */
    suspend fun nameDayContact(context: Context, names: List<String>): Pair<String, Long>? = withContext(Dispatchers.IO) {
        if (names.isEmpty() || !hasContacts(context)) return@withContext null
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (ContextRules.firstNameMatches(name, names)) return@runCatching name to c.getLong(0)
                }
            }
            null
        }.getOrNull()
    }

    /** Installed apps whose name looks like a lunch app (also apps without a launcher icon). */
    fun lunchAppCandidates(context: Context): List<Pair<String, String>> = runCatching {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .map { it.packageName to it.loadLabel(pm).toString() }
            .filter { ContextRules.looksLikeLunchApp(it.second) }
            .distinctBy { it.first }
    }.getOrDefault(emptyList())

    fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrDefault(pkg)

    // --- Health Connect (steps, synced from Samsung Health) ---

    fun healthConnectAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasStepsPermission(context: Context): Boolean = runCatching {
        healthConnectAvailable(context) &&
            StepsPermission in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    }.getOrDefault(false)

    suspend fun stepsToday(context: Context, zone: ZoneId): Long? {
        if (!hasStepsPermission(context)) return null
        return runCatching {
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val res = HealthConnectClient.getOrCreate(context).aggregate(
                AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, Instant.now()))
            )
            res[StepsRecord.COUNT_TOTAL] ?: 0L
        }.getOrNull()
    }
}

/** Plan size, cycle start and steps goal; defaults from the spec. */
internal class ContextSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("schoolbrief_context", Context.MODE_PRIVATE)

    /** Package of the EduPage food app; null = detect by name. */
    var lunchPackage: String?
        get() = prefs.getString("lunch_package", null)
        set(v) { prefs.edit().putString("lunch_package", v).apply() }

    /** Today's lunch, kept after the notification is dismissed: "yyyy-MM-dd|pkg|text". */
    fun storedLunch(today: LocalDate): Pair<String, String>? {
        val raw = prefs.getString("lunch_today", null) ?: return null
        val parts = raw.split('|', limit = 3)
        return if (parts.size == 3 && parts[0] == today.toString()) parts[1] to parts[2] else null
    }

    fun storeLunch(today: LocalDate, pkg: String, text: String) {
        prefs.edit().putString("lunch_today", "$today|$pkg|$text").apply()
    }

    fun load(): ContextSettings {
        val d = ContextSettings()
        return ContextSettings(
            planBytes = prefs.getLong("plan_bytes", d.planBytes),
            cycleAnchor = prefs.getString("cycle_anchor", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: d.cycleAnchor,
            stepsGoal = prefs.getLong("steps_goal", d.stepsGoal),
        )
    }

    fun save(s: ContextSettings) {
        prefs.edit()
            .putLong("plan_bytes", s.planBytes)
            .putString("cycle_anchor", s.cycleAnchor.toString())
            .putLong("steps_goal", s.stepsGoal)
            .apply()
    }
}
