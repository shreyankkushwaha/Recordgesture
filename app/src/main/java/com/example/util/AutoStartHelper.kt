package com.example.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Utility helper to open manufacturer-specific background protection & AutoStart settings
 * (Xiaomi/MIUI/HyperOS, Oppo, Vivo, Samsung, Huawei, OnePlus) and manage Battery Optimization
 * so the system never force-stops or kills Quick Recorder in the background.
 */
object AutoStartHelper {
    private const val TAG = "AutoStartHelper"

    fun isXiaomiDevice(): Boolean {
        val manufacturer = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco")
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    /**
     * Opens OEM AutoStart management page. Falls back to App Info if OEM page is unavailable.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val intentList = mutableListOf<Intent>()

        // 1. Xiaomi / Redmi / POCO (MIUI / HyperOS)
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
        )
        intentList.add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))

        // 2. Oppo / Realme (ColorOS / RealmeUI)
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
        )
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startupapp.StartupAppListActivity"
                )
            }
        )
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            }
        )

        // 3. Vivo / iQOO (FuntouchOS / OriginOS)
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            }
        )
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
        )

        // 4. Samsung (OneUI)
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
        )
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
        )

        // 5. Huawei / Honor (EMUI / MagicOS)
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
        )
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
                )
            }
        )

        // 6. OnePlus (OxygenOS)
        intentList.add(
            Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            }
        )

        for (intent in intentList) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "Launched AutoStart via: ${intent.component ?: intent.action}")
                    return true
                }
            } catch (e: Exception) {
                // Try next intent
            }
        }

        // Generic fallback: Open App Details Settings
        return openAppDetailsSettings(context)
    }

    /**
     * Opens request dialog or battery optimization settings so user can set "No restrictions / Unrestricted"
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, fallback to general list: ${e.message}")
                try {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                    return true
                } catch (e2: Exception) {
                    Log.e(TAG, "Could not open battery optimization settings", e2)
                }
            }
        }
        return openAppDetailsSettings(context)
    }

    /**
     * Opens Xiaomi's "Other permissions" screen where "Display pop-up windows while running in the background" is toggled.
     */
    fun openXiaomiOtherPermissions(context: Context): Boolean {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open MIUI APP_PERM_EDITOR: ${e.message}")
        }

        // Fallback to overlay permission
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val overlayIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(overlayIntent)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed overlay permission fallback", e)
        }

        return openAppDetailsSettings(context)
    }

    /**
     * Fallback to the system App Details / App Info settings page.
     */
    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Application Details Settings", e)
            false
        }
    }
}
