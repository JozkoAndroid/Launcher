package de.mm20.launcher2.schoolbrief

import org.koin.dsl.module

val schoolBriefModule = module {
    single<BriefRepository> { SampleBriefRepository() }
}
