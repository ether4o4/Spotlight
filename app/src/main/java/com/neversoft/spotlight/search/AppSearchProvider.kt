package com.neversoft.spotlight.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.neversoft.spotlight.model.LaunchAction
import com.neversoft.spotlight.model.ResultType
import com.neversoft.spotlight.model.SearchResult
import java.util.Locale

/** Finds installed, launchable apps whose name or package matches the query. */
class AppSearchProvider(private val context: Context) {

    fun search(query: String, limit: Int): List<SearchResult> {
        val pm = context.packageManager
        val q = query.lowercase(Locale.getDefault())
        val results = ArrayList<SearchResult>()

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }

        for (info in activities) {
            val label = info.loadLabel(pm).toString()
            val pkg = info.activityInfo.packageName
            if (label.lowercase(Locale.getDefault()).contains(q) ||
                pkg.lowercase(Locale.getDefault()).contains(q)
            ) {
                results += SearchResult(
                    id = "app:$pkg/${info.activityInfo.name}",
                    title = label,
                    subtitle = pkg,
                    type = ResultType.APP,
                    launch = LaunchAction.LaunchApp(pkg),
                    appPackage = pkg,
                )
                if (results.size >= limit) break
            }
        }
        // De-duplicate apps that expose multiple launcher activities.
        return results.distinctBy { it.subtitle }
    }
}
