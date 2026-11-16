package de.mm20.launcher2.ui.launcher.search.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.services.tags.TagsService
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.first

/**
 * Default categories of the app list. They are created once as ordinary tags,
 * so the user can rename them, move apps around or delete them.
 */
private enum class DefaultAppTag(@StringRes val label: Int) {
    Communication(R.string.app_category_communication),
    PhotosVideos(R.string.app_category_photos_videos),
    Music(R.string.app_category_music),
    Games(R.string.app_category_games),
    News(R.string.app_category_news),
    Maps(R.string.app_category_maps),
    Productivity(R.string.app_category_productivity),
    Internet(R.string.app_category_internet),
    Tools(R.string.app_category_tools),
}

/**
 * Packages that handle a well known role (dialer, browser, camera…), with the tag of that role.
 * The first matching role wins. Only sees the apps of the current user.
 */
private fun roleTags(context: Context): Map<String, DefaultAppTag> {
    fun main(category: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
    val roles = listOf(
        DefaultAppTag.Communication to Intent(Intent.ACTION_DIAL),
        DefaultAppTag.Communication to main(Intent.CATEGORY_APP_MESSAGING),
        DefaultAppTag.Communication to main(Intent.CATEGORY_APP_EMAIL),
        DefaultAppTag.Communication to main(Intent.CATEGORY_APP_CONTACTS),
        DefaultAppTag.PhotosVideos to main(Intent.CATEGORY_APP_GALLERY),
        DefaultAppTag.PhotosVideos to Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        DefaultAppTag.Music to main(Intent.CATEGORY_APP_MUSIC),
        DefaultAppTag.Maps to main(Intent.CATEGORY_APP_MAPS),
        DefaultAppTag.Productivity to main(Intent.CATEGORY_APP_CALENDAR),
        DefaultAppTag.Productivity to main(Intent.CATEGORY_APP_CALCULATOR),
        DefaultAppTag.Productivity to main(Intent.CATEGORY_APP_FILES),
        DefaultAppTag.Internet to main(Intent.CATEGORY_APP_BROWSER),
        DefaultAppTag.Tools to main(Intent.CATEGORY_APP_MARKET),
        DefaultAppTag.Tools to main(Intent.CATEGORY_APP_WEATHER),
        DefaultAppTag.Tools to Intent(Settings.ACTION_SETTINGS),
        DefaultAppTag.Tools to Intent(AlarmClock.ACTION_SHOW_ALARMS),
    )
    val tags = mutableMapOf<String, DefaultAppTag>()
    for ((tag, intent) in roles) {
        for (info in context.packageManager.queryIntentActivities(intent, 0)) {
            tags.putIfAbsent(info.activityInfo.packageName, tag)
        }
    }
    return tags
}

/**
 * The category the app declares in its manifest (android:appCategory).
 */
private fun declaredTag(category: Int): DefaultAppTag? = when (category) {
    ApplicationInfo.CATEGORY_SOCIAL -> DefaultAppTag.Communication
    ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_VIDEO -> DefaultAppTag.PhotosVideos
    ApplicationInfo.CATEGORY_AUDIO -> DefaultAppTag.Music
    ApplicationInfo.CATEGORY_GAME -> DefaultAppTag.Games
    ApplicationInfo.CATEGORY_NEWS -> DefaultAppTag.News
    ApplicationInfo.CATEGORY_MAPS -> DefaultAppTag.Maps
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> DefaultAppTag.Productivity
    ApplicationInfo.CATEGORY_ACCESSIBILITY -> DefaultAppTag.Tools
    else -> null
}

private fun defaultTag(
    app: Application,
    roles: Map<String, DefaultAppTag>,
    launcherApps: LauncherApps?,
): DefaultAppTag? {
    val packageName = app.componentName.packageName
    roles[packageName]?.let { return it }
    val info = try {
        launcherApps?.getApplicationInfo(packageName, 0, app.user)
    } catch (e: Exception) {
        null
    } ?: return null
    declaredTag(info.category)?.let { return it }
    // ponytail: preinstalled apps without a category are mostly system tools; the rest goes to "Other apps"
    return if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) DefaultAppTag.Tools else null
}

/**
 * Puts every app that has no tag yet in its default tag, keeping the items already in that tag.
 */
internal suspend fun createDefaultAppTags(
    context: Context,
    apps: List<Application>,
    tagsByKey: Map<String, List<String>>,
    tagsService: TagsService,
) {
    val roles = roleTags(context)
    val launcherApps = context.getSystemService<LauncherApps>()
    val untagged = apps.filter { tagsByKey[it.key].isNullOrEmpty() }
    for ((tag, tagApps) in untagged.groupBy { defaultTag(it, roles, launcherApps) }) {
        if (tag == null) continue
        val name = context.getString(tag.label)
        val existing = tagsService.getTaggedItems(name).first()
        tagsService.createTag(name, existing + tagApps)
    }
}
