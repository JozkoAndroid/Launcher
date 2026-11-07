package de.mm20.launcher2.schoolbrief.live

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import de.mm20.launcher2.schoolbrief.BriefRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Hands the login code to the repository and goes straight back to the home screen. */
class OAuthCallbackActivity : Activity(), KoinComponent {
    private val repository: BriefRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { repository.handleLoginCallback(it) }
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
