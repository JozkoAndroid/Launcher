package de.mm20.launcher2.schoolbrief

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Source of the home screen feed. Step 1: sample data. Step 3: Schoolhelp + news feeds + Gemini. */
interface BriefRepository {
    val feed: Flow<Feed?>
}

internal class SampleBriefRepository : BriefRepository {
    override val feed: Flow<Feed?> = flowOf(SampleFeed.create())
}
