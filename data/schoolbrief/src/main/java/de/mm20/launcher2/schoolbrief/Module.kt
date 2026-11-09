package de.mm20.launcher2.schoolbrief

import de.mm20.launcher2.schoolbrief.live.LiveBriefRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val schoolBriefModule = module {
    single<BriefRepository> { LiveBriefRepository(androidContext()) }
    // Round 31: Claude when its key is set (with Schoolhelp's tools), else Gemini.
    single<SchoolChat> { ClaudeSchoolChat(androidContext(), get()) }
}
