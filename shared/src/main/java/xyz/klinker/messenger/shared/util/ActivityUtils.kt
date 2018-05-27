/*
 * Copyright (C) 2017 Luke Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger.shared.util

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.view.View

import xyz.klinker.messenger.shared.R
import xyz.klinker.messenger.shared.data.Settings

/**
 * Utils for helping with different activity tasks such as setting the task description.
 */
object ActivityUtils {

    val MESSENGER_ACTIVITY = ComponentName("xyz.klinker.messenger",
            "xyz.klinker.messenger" + ".activity.MessengerActivity")
    val COMPOSE_ACTIVITY = ComponentName("xyz.klinker.messenger",
            "xyz.klinker.messenger" + ".activity.compose.ComposeActivity")
    val QUICK_SHARE_ACTIVITY = ComponentName("xyz.klinker.messenger",
            "xyz.klinker.messenger" + ".activity.share.QuickShareActivity")
    val NOTIFICATION_REPLY = ComponentName("xyz.klinker.messenger",
            "xyz.klinker.messenger" + ".activity.notification.MarshmallowReplyActivity")

    fun buildForComponent(component: ComponentName): Intent {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.component = component
        return intent
    }

    fun setTaskDescription(activity: Activity?) {
        val bm = BitmapFactory.decodeResource(activity?.resources, R.mipmap.ic_launcher)
        val td = ActivityManager.TaskDescription(
                activity?.getString(R.string.app_name), bm, Settings.mainColorSet.color)

        activity?.setTaskDescription(td)
    }

    fun setTaskDescription(activity: Activity?, title: String, color: Int) {
        try {
            val td = ActivityManager.TaskDescription(title, null, color)
            activity?.setTaskDescription(td)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setStatusBarColor(activity: Activity?, color: Int) {
        activity?.window?.statusBarColor = color
        setUpLightStatusBar(activity, color)
    }

    fun setUpLightStatusBar(activity: Activity?, color: Int) {
        if (!ColorUtils.isColorDark(color)) {
            activateLightStatusBar(activity, true)
        } else {
            activateLightStatusBar(activity, false)
        }
    }

    fun setUpNavigationBarColor(activity: Activity?, color: Int) {
//        if (!AndroidVersionUtil.isAndroidO || activity == null || !Settings.adjustableNavBar) {
        if (!AndroidVersionUtil.isAndroidO_MR1 || activity == null) {
            return
        }

        when (color) {
            Color.WHITE -> {
                activity.window?.navigationBarColor = Color.WHITE
                activateLightNavigationBar(activity, true)
            }
            Color.BLACK -> {
                activity.window?.navigationBarColor = Color.BLACK
                activateLightNavigationBar(activity, false)
            }
            else -> {
                activity.window?.navigationBarColor = activity.resources.getColor(R.color.drawerBackground)
                activateLightNavigationBar(activity, false)
            }
        }
    }

    private fun activateLightStatusBar(activity: Activity?, activate: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || activity == null) {
            return
        }

        val oldSystemUiFlags = activity.window.decorView.systemUiVisibility
        var newSystemUiFlags = oldSystemUiFlags
        newSystemUiFlags = if (activate) {
            newSystemUiFlags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            newSystemUiFlags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        if (newSystemUiFlags != oldSystemUiFlags) {
            activity.window.decorView.systemUiVisibility = newSystemUiFlags
        }
    }

    private fun activateLightNavigationBar(activity: Activity?, activate: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity == null) {
            return
        }

        val oldSystemUiFlags = activity.window.decorView.systemUiVisibility
        var newSystemUiFlags = oldSystemUiFlags
        newSystemUiFlags = if (activate) {
            newSystemUiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            newSystemUiFlags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }

        if (newSystemUiFlags != oldSystemUiFlags) {
            activity.window.decorView.systemUiVisibility = newSystemUiFlags
        }
    }
}
