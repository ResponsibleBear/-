package ru.alicedom.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.service.quicksettings.TileService
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.alicedom.app.R
import ru.alicedom.app.data.DevicePrefsStore
import ru.alicedom.app.data.SecureTokenStore
import ru.alicedom.app.data.SmartDevice
import ru.alicedom.app.data.SmartHomeSnapshot
import ru.alicedom.app.data.YandexSmartHomeClient
import ru.alicedom.app.tiles.QuickAllOffTileService
import ru.alicedom.app.tiles.QuickSwitchTileService1
import ru.alicedom.app.tiles.QuickSwitchTileService2
import ru.alicedom.app.ui.MainActivity

class AliceSwitchesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = DevicePrefsStore(context)
        val token = SecureTokenStore(context).getToken()
        renderWidgets(context, appWidgetManager, appWidgetIds, prefs.loadCachedHome(), token != null)

        if (token != null) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    YandexSmartHomeClient().fetchHome(token).onSuccess { fresh ->
                        prefs.saveCachedHome(fresh)
                        renderWidgets(context, appWidgetManager, appWidgetIds, fresh, true)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_DEVICE -> {
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
                val token = SecureTokenStore(context).getToken()
                if (token == null) {
                    launchApp(context)
                    return
                }
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val prefs = DevicePrefsStore(context)
                        val api = YandexSmartHomeClient()
                        val cached = prefs.loadCachedHome()
                        val target = cached?.devices?.find { it.id == deviceId }
                        val nextOn = !(target?.isOn ?: false)

                        if (cached != null) {
                            val optimistic = cached.copy(
                                devices = cached.devices.map { if (it.id == deviceId) it.copy(isOn = nextOn) else it }
                            )
                            prefs.saveCachedHome(optimistic)
                            updateAllWidgets(context, optimistic)
                        }

                        api.togglePower(token, deviceId, nextOn)
                            .onFailure {
                                if (cached != null) {
                                    prefs.saveCachedHome(cached)
                                    updateAllWidgets(context, cached)
                                }
                            }

                        api.fetchHome(token).onSuccess { fresh ->
                            prefs.saveCachedHome(fresh)
                            updateAllWidgets(context, fresh)
                        }
                        requestTilesRefresh(context)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_ALL_OFF -> {
                val token = SecureTokenStore(context).getToken()
                if (token == null) {
                    launchApp(context)
                    return
                }
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val prefs = DevicePrefsStore(context)
                        val api = YandexSmartHomeClient()
                        val current = api.fetchHome(token).getOrNull() ?: prefs.loadCachedHome()
                        val activeIds = current?.devices
                            ?.filter { it.hasOnOff && it.isOn && it.type.startsWith("devices.types.switch") }
                            ?.map { it.id }
                            .orEmpty()
                        val fallbackIds = if (activeIds.isNotEmpty()) activeIds else current?.devices
                            ?.filter { it.hasOnOff && it.isOn }
                            ?.map { it.id }
                            .orEmpty()

                        if (current != null && fallbackIds.isNotEmpty()) {
                            val optimistic = current.copy(
                                devices = current.devices.map {
                                    if (fallbackIds.contains(it.id)) it.copy(isOn = false) else it
                                }
                            )
                            prefs.saveCachedHome(optimistic)
                            updateAllWidgets(context, optimistic)
                            api.turnOffDevices(token, fallbackIds)
                        }
                        api.fetchHome(token).onSuccess { fresh ->
                            prefs.saveCachedHome(fresh)
                            updateAllWidgets(context, fresh)
                        }
                        requestTilesRefresh(context)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_REFRESH -> {
                val token = SecureTokenStore(context).getToken()
                if (token == null) {
                    launchApp(context)
                    return
                }
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val prefs = DevicePrefsStore(context)
                        YandexSmartHomeClient().fetchHome(token).onSuccess { fresh ->
                            prefs.saveCachedHome(fresh)
                            updateAllWidgets(context, fresh)
                        }
                        requestTilesRefresh(context)
                    } finally {
                        pending.finish()
                    }
                }
            }

            else -> super.onReceive(context, intent)
        }
    }

    private fun launchApp(context: Context) {
        val i = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }

    companion object {
        const val ACTION_TOGGLE_DEVICE = "ru.alicedom.app.widget.ACTION_TOGGLE_DEVICE"
        const val ACTION_ALL_OFF = "ru.alicedom.app.widget.ACTION_ALL_OFF"
        const val ACTION_REFRESH = "ru.alicedom.app.widget.ACTION_REFRESH"
        const val EXTRA_DEVICE_ID = "extra_device_id"

        fun updateAllWidgets(context: Context, snapshot: SmartHomeSnapshot? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AliceSwitchesWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val prefs = DevicePrefsStore(context)
            val data = snapshot ?: prefs.loadCachedHome()
            val hasToken = SecureTokenStore(context).getToken() != null
            renderWidgets(context, manager, ids, data, hasToken)
        }

        fun requestTilesRefresh(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, QuickSwitchTileService1::class.java))
                TileService.requestListeningState(context, ComponentName(context, QuickSwitchTileService2::class.java))
                TileService.requestListeningState(context, ComponentName(context, QuickAllOffTileService::class.java))
            }
        }

        private fun renderWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: SmartHomeSnapshot?,
            hasToken: Boolean
        ) {
            val prefs = DevicePrefsStore(context)
            val roomMap = snapshot?.rooms?.associateBy { it.id } ?: emptyMap()
            val allDevices = snapshot?.devices ?: emptyList()
            val widgetDevices = prefs.resolveWidgetDevices(allDevices)
            val activeSwitchesCount = allDevices.count { it.hasOnOff && it.isOn && it.type.startsWith("devices.types.switch") }

            val openAppIntent = PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val allOffIntent = PendingIntent.getBroadcast(
                context,
                11,
                Intent(context, AliceSwitchesWidgetProvider::class.java).apply { action = ACTION_ALL_OFF },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val refreshIntent = PendingIntent.getBroadcast(
                context,
                12,
                Intent(context, AliceSwitchesWidgetProvider::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val tileIds = intArrayOf(R.id.widget_tile_0, R.id.widget_tile_1, R.id.widget_tile_2, R.id.widget_tile_3)
            val titleIds = intArrayOf(R.id.widget_tile_0_title, R.id.widget_tile_1_title, R.id.widget_tile_2_title, R.id.widget_tile_3_title)
            val subIds = intArrayOf(R.id.widget_tile_0_sub, R.id.widget_tile_1_sub, R.id.widget_tile_2_sub, R.id.widget_tile_3_sub)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_alice_dom)
                views.setOnClickPendingIntent(R.id.widget_header, openAppIntent)
                views.setOnClickPendingIntent(R.id.widget_all_off_btn, allOffIntent)
                views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshIntent)

                if (!hasToken || widgetDevices.isEmpty()) {
                    views.setTextViewText(
                        R.id.widget_subtitle,
                        if (!hasToken) "Нужен вход" else "Нет устройств"
                    )
                    views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_row_1, View.GONE)
                    views.setViewVisibility(R.id.widget_row_2, View.GONE)
                    views.setOnClickPendingIntent(R.id.widget_empty_text, openAppIntent)
                } else {
                    views.setTextViewText(R.id.widget_subtitle, "$activeSwitchesCount вкл.")
                    views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                    views.setViewVisibility(R.id.widget_row_1, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_row_2, if (widgetDevices.size > 2) View.VISIBLE else View.GONE)

                    for (i in 0 until 4) {
                        val dev: SmartDevice? = widgetDevices.getOrNull(i)
                        if (dev == null) {
                            views.setViewVisibility(tileIds[i], View.INVISIBLE)
                            continue
                        }
                        views.setViewVisibility(tileIds[i], View.VISIBLE)

                        val isOn = dev.hasOnOff && dev.isOn
                        val isLight = dev.type.startsWith("devices.types.light")
                        val roomName = dev.roomId?.let { roomMap[it]?.name }
                        val statusWord = if (isOn) "ВКЛ" else "ВЫКЛ"
                        val primaryText = listOfNotNull(roomName, statusWord).joinToString(" · ")

                        views.setTextViewText(titleIds[i], primaryText)
                        views.setTextViewText(subIds[i], dev.name)

                        val isSwitchOrPower = dev.type.startsWith("devices.types.switch") || dev.type.startsWith("devices.types.socket")
                        when {
                            isOn && isLight -> {
                                views.setInt(tileIds[i], "setBackgroundResource", R.drawable.widget_tile_on_amber)
                                views.setTextColor(titleIds[i], Color.parseColor("#0F172A"))
                                views.setTextColor(subIds[i], Color.parseColor("#332100"))
                            }
                            isOn && isSwitchOrPower -> {
                                views.setInt(tileIds[i], "setBackgroundResource", R.drawable.widget_tile_on_emerald)
                                views.setTextColor(titleIds[i], Color.WHITE)
                                views.setTextColor(subIds[i], Color.parseColor("#ECFDF5"))
                            }
                            isOn -> {
                                views.setInt(tileIds[i], "setBackgroundResource", R.drawable.widget_tile_on_blue)
                                views.setTextColor(titleIds[i], Color.WHITE)
                                views.setTextColor(subIds[i], Color.parseColor("#EFF6FF"))
                            }
                            else -> {
                                views.setInt(tileIds[i], "setBackgroundResource", R.drawable.widget_tile_off)
                                views.setTextColor(titleIds[i], Color.parseColor("#F3F6FC"))
                                views.setTextColor(subIds[i], Color.parseColor("#9AA5BA"))
                            }
                        }

                        val toggleIntent = PendingIntent.getBroadcast(
                            context,
                            100 + i,
                            Intent(context, AliceSwitchesWidgetProvider::class.java).apply {
                                action = ACTION_TOGGLE_DEVICE
                                putExtra(EXTRA_DEVICE_ID, dev.id)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(tileIds[i], toggleIntent)
                    }
                }

                manager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
