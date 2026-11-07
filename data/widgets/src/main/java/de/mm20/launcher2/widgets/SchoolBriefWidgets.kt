package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Current and next lesson, computed live from the timetable. */
data class LessonWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_lesson)

    companion object {
        const val Type = "school_lesson"
    }
}

/** AI brief and three headlines. */
data class BriefWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_brief)

    companion object {
        const val Type = "school_brief"
    }
}

/** Homework, tests, projects and Termine, overdue first. */
data class TasksWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_school_tasks)

    companion object {
        const val Type = "school_tasks"
    }
}

/** One-line cards that appear only when their rule is true. */
data class ContextWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_context)

    companion object {
        const val Type = "school_context"
    }
}

/** The home screen's one main action: opens the default assistant (Gemini). */
data class AssistantWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_assistant)

    companion object {
        const val Type = "school_assistant"
    }
}

/** App folders (Kvaesitso tags) as tiles on the home screen; create and edit them right there. */
data class FoldersWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_folders)

    companion object {
        const val Type = "school_folders"
    }
}

@Serializable
data class ChipWidgetConfig(
    /** Name day: show only when a contact celebrates. */
    val onlyContacts: Boolean = false,
)

/**
 * One context chip as its own block, so it can be added, moved and removed like any block.
 * [kind]: test, chats, nameday, birthday, lunch, music, steps, data, instagram.
 */
data class ChipWidget(
    override val id: UUID,
    val kind: String,
    val config: ChipWidgetConfig = ChipWidgetConfig(),
) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = TypePrefix + kind, config = Json.encodeToString(ChipWidgetConfig.serializer(), config))
    override fun getLabel(context: Context): String = context.getString(labelRes(kind))

    companion object {
        const val TypePrefix = "school_chip_"
        val Kinds = listOf("test", "chats", "lunch", "nameday", "birthday", "music", "steps", "data", "instagram")

        fun labelRes(kind: String): Int = when (kind) {
            "test" -> R.string.chip_name_test
            "chats" -> R.string.chip_name_chats
            "lunch" -> R.string.chip_name_lunch
            "nameday" -> R.string.chip_name_nameday
            "birthday" -> R.string.chip_name_birthday
            "music" -> R.string.chip_name_music
            "steps" -> R.string.chip_name_steps
            "data" -> R.string.chip_name_data
            else -> R.string.chip_name_instagram
        }
    }
}

/** The next school days with lessons and what is due (side panel by default). */
data class WeekWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_week)
    companion object { const val Type = "school_week" }
}

/** Adaptive: mornings the day ahead and what to bring, evenings tomorrow and a bag checklist. */
data class MyDayWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_myday)
    companion object { const val Type = "school_myday" }
}

/** Nearest test with a "practise" button that quizzes you in the chat. */
data class TestPrepWidget(override val id: UUID) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(id = id, type = Type, config = null)
    override fun getLabel(context: Context): String = context.getString(R.string.widget_name_testprep)
    companion object { const val Type = "school_testprep" }
}
