package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import java.util.UUID

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
