package de.thomaskuenneth.benice

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

const val MIME_TYPE_URL = "text/uri-list"
const val MIME_TYPE_IMAGE = "image/*"

const val EXTRA_APP_PAIR_TOP = "top"
const val EXTRA_APP_PAIR_BOTTOM = "bottom"

private const val ACTION_LAUNCH_APP = "de.thomaskuenneth.benice.intent.action.ACTION_LAUNCH_APP"
private const val PACKAGE_NAME = "packageName"
private const val CLASS_NAME = "className"
private const val DELAY = "delay"

private const val ACTION_LAUNCH_APP_PAIR =
    "de.thomaskuenneth.benice.intent.action.ACTION_LAUNCH_APP_PAIR"
private const val PACKAGE_NAME_FIRST_APP = "packageNameFirstApp"
private const val CLASS_NAME_FIRST_APP = "classNameFirstApp"
private const val PACKAGE_NAME_SECOND_APP = "packageNameSecondApp"
private const val CLASS_NAME_SECOND_APP = "classNameSecondApp"

private data class AppPairLaunch(
    val firstPackageName: String,
    val firstClassName: String,
    val secondPackageName: String,
    val secondClassName: String,
    val delay: Long,
)

class BeNiceActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        when (val appPairLaunch = intent?.resolveAppPairLaunch(this)) {
            null -> {
                when {
                    intent?.hasShellAppPairExtras() == true -> {
                        Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_LONG).show()
                        finish()
                    }

                    intent?.action == ACTION_LAUNCH_APP_PAIR -> Unit

                    else -> {
                        Intent(this, AppChooserActivity::class.java).run {
                            addFlags(FLAG_ACTIVITY_NEW_TASK)
                            startActivityCatchExceptions(this)
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            launchApp(intent = intent)
                            finish()
                        }, 500L)
                    }
                }
            }

            else -> launchAppPair(appPairLaunch)
        }
    }

    private fun launchAppPair(appPairLaunch: AppPairLaunch) {
        with(appPairLaunch) {
            launchApp(
                packageName = firstPackageName,
                className = firstClassName,
                launchAdjacent = false,
            )
            Handler(Looper.getMainLooper()).postDelayed({
                launchApp(
                    packageName = secondPackageName,
                    className = secondClassName,
                    launchAdjacent = true,
                )
                finish()
            }, delay)
        }
    }

    private fun launchApp(intent: Intent) {
        lifecycleScope.launch {
            if (ACTION_LAUNCH_APP == intent.action) {
                intent.getStringExtra(PACKAGE_NAME)?.let { packageName ->
                    intent.getStringExtra(CLASS_NAME)?.let { className ->
                        launchApp(
                            packageName = packageName, className = className, launchAdjacent = true
                        )
                    }
                }
            }
        }
    }
}

private fun Intent.hasShellAppPairExtras(): Boolean =
    getStringExtra(EXTRA_APP_PAIR_TOP) != null && getStringExtra(EXTRA_APP_PAIR_BOTTOM) != null

private fun Intent.resolveAppPairLaunch(context: Context): AppPairLaunch? {
    val delay = getLongExtra(DELAY, 500L)
    if (action == ACTION_LAUNCH_APP_PAIR) {
        val firstPackageName = getStringExtra(PACKAGE_NAME_FIRST_APP)
        val firstClassName = getStringExtra(CLASS_NAME_FIRST_APP)
        val secondPackageName = getStringExtra(PACKAGE_NAME_SECOND_APP)
        val secondClassName = getStringExtra(CLASS_NAME_SECOND_APP)
        if (firstPackageName != null && firstClassName != null &&
            secondPackageName != null && secondClassName != null
        ) {
            return AppPairLaunch(
                firstPackageName = firstPackageName,
                firstClassName = firstClassName,
                secondPackageName = secondPackageName,
                secondClassName = secondClassName,
                delay = delay,
            )
        }
    }
    val topPackageName = getStringExtra(EXTRA_APP_PAIR_TOP)
    val bottomPackageName = getStringExtra(EXTRA_APP_PAIR_BOTTOM)
    if (topPackageName != null && bottomPackageName != null) {
        val topClassName = context.resolveLauncherClassName(topPackageName)
        val bottomClassName = context.resolveLauncherClassName(bottomPackageName)
        if (topClassName != null && bottomClassName != null) {
            return AppPairLaunch(
                firstPackageName = topPackageName,
                firstClassName = topClassName,
                secondPackageName = bottomPackageName,
                secondClassName = bottomClassName,
                delay = delay,
            )
        }
    }
    return null
}

fun Activity.launchApp(
    packageName: String, className: String, launchAdjacent: Boolean
) {
    Intent().run {
        when (packageName) {
            MIME_TYPE_URL -> {
                action = Intent.ACTION_VIEW
                data = className.toUri()
            }

            MIME_TYPE_IMAGE -> {
                component = ComponentName(this@launchApp, ImageViewerActivity::class.java)
                action = Intent.ACTION_VIEW
                setDataAndType(className.toUri(), MIME_TYPE_IMAGE)
            }

            else -> {
                component = ComponentName(
                    packageName, className
                )
            }
        }
        addFlags(FLAG_ACTIVITY_NEW_TASK)
        if (launchAdjacent) {
            addFlags(
                FLAG_ACTIVITY_LAUNCH_ADJACENT or FLAG_ACTIVITY_TASK_ON_HOME
            )
        }
        startActivityCatchExceptions(this)
    }
}

fun Context.createBeNiceLaunchIntent(appInfo: AppInfo) =
    Intent(this, BeNiceActivity::class.java).also { intent ->
        intent.action = ACTION_LAUNCH_APP
        intent.addFlags(
            FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
        )
        intent.putExtra(PACKAGE_NAME, appInfo.packageName)
        intent.putExtra(CLASS_NAME, appInfo.className)
    }

fun Context.createLaunchAppPairIntent(
    firstApp: AppInfo, secondApp: AppInfo, delay: Long
) = Intent(this, BeNiceActivity::class.java).also { intent ->
    intent.action = ACTION_LAUNCH_APP_PAIR
    intent.addFlags(
        FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
    )
    intent.putExtra(PACKAGE_NAME_FIRST_APP, firstApp.packageName)
    intent.putExtra(CLASS_NAME_FIRST_APP, firstApp.className)
    intent.putExtra(PACKAGE_NAME_SECOND_APP, secondApp.packageName)
    intent.putExtra(CLASS_NAME_SECOND_APP, secondApp.className)
    intent.putExtra(DELAY, delay)
}
