package de.mm20.launcher2.ui.launcher.widgets.school.context

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import de.mm20.launcher2.ui.R

/** Health Connect requires an app to explain why it reads health data. Shown from Health Connect's own screens. */
class HealthRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        setContentView(ScrollView(this).apply {
            addView(TextView(this@HealthRationaleActivity).apply {
                setPadding(pad, pad, pad, pad)
                textSize = 16f
                text = getString(R.string.context_health_rationale)
            })
        })
    }
}
