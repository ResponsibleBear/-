package ru.alicedom.app.tiles

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.alicedom.app.data.DevicePrefsStore
import ru.alicedom.app.data.SecureTokenStore
import ru.alicedom.app.data.SmartDevice
import ru.alicedom.app.data.SmartHomeSnapshot
import ru.alicedom.app.data.YandexSmartHomeClient
import ru.alicedom.app.ui.MainActivity
import ru.alicedom.app.widget.AliceSwitchesWidgetProvider

abstract class BaseQuickSwitchTileService(private val slot: Int) : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val token = SecureTokenStore(applicationContext).getToken()
        val prefs = DevicePrefsStore(applicationContext)
        updateTileFromSnapshot(prefs.loadCachedHome(), token != null)

        if (token != null) {
            scope?.launch {
                YandexSmartHomeClient().fetchHome(token).onSuccess { fresh ->
                    prefs.saveCachedHome(fresh)
                    updateTileFromSnapshot(fresh, true)
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val token = SecureTokenStore(applicationContext).getToken()
        if (token == null) {
            openApp()
            return
        }

        val prefs = DevicePrefsStore(applicationContext)
        val cached = prefs.loadCachedHome()
        val target = resolveTargetDevice(cached, prefs)
        if (target == null) {
            openApp()
            return
        }

        val nextOn = !target.isOn
        qsTile?.let { tile ->
            tile.state = if (nextOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val api = YandexSmartHomeClient()
            if (cached != null) {
                val optimistic = cached.copy(
                    devices = cached.devices.map { if (it.id == target.id) it.copy(isOn = nextOn) else it }
                )
                prefs.saveCachedHome(optimistic)
                AliceSwitchesWidgetProvider.updateAllWidgets(applicationContext, optimistic)
            }

            api.togglePower(token, target.id, nextOn)
            api.fetchHome(token).onSuccess { fresh ->
                prefs.saveCachedHome(fresh)
                updateTileFromSnapshot(fresh, true)
                AliceSwitchesWidgetProvider.updateAllWidgets(applicationContext, fresh)
            }
        }
    }

    private fun resolveTargetDevice(snapshot: SmartHomeSnapshot?, prefs: DevicePrefsStore): SmartDevice? {
        val all = snapshot?.devices ?: return null
        val boundId = prefs.getQuickTileDeviceId(slot)
        if (boundId != null) {
            val found = all.find { it.id == boundId }
            if (found != null) return found
        }
        val favs = prefs.getFavoriteIds()
        val switches = all.filter { it.hasOnOff && it.type.startsWith("devices.types.switch") }
            .sortedBy { !favs.contains(it.id) }
        val pool = if (switches.isNotEmpty()) switches else all.filter { it.hasOnOff }
        return pool.getOrNull(slot - 1)
    }

    private fun updateTileFromSnapshot(snapshot: SmartHomeSnapshot?, hasToken: Boolean) {
        val tile = qsTile ?: return
        if (!hasToken) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Свет $slot"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Войдите в Алису"
            }
            tile.updateTile()
            return
        }

        val prefs = DevicePrefsStore(applicationContext)
        val device = resolveTargetDevice(snapshot, prefs)
        if (device == null) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Свет $slot"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Не назначен"
            }
            tile.updateTile()
            return
        }

        val roomName = snapshot?.rooms?.find { it.id == device.roomId }?.name
        tile.state = if (device.isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = roomName ?: device.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "${device.name} · ${if (device.isOn) "ВКЛ" else "ВЫКЛ"}"
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, slot, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class QuickSwitchTileService1 : BaseQuickSwitchTileService(1)
class QuickSwitchTileService2 : BaseQuickSwitchTileService(2)

class QuickAllOffTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val token = SecureTokenStore(applicationContext).getToken()
        val prefs = DevicePrefsStore(applicationContext)
        updateTile(prefs.loadCachedHome(), token != null)

        if (token != null) {
            scope?.launch {
                YandexSmartHomeClient().fetchHome(token).onSuccess { fresh ->
                    prefs.saveCachedHome(fresh)
                    updateTile(fresh, true)
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val token = SecureTokenStore(applicationContext).getToken()
        if (token == null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val prefs = DevicePrefsStore(applicationContext)
            val api = YandexSmartHomeClient()
            val current = api.fetchHome(token).getOrNull() ?: prefs.loadCachedHome()
            val switchIds = current?.devices
                ?.filter { it.hasOnOff && it.isOn && it.type.startsWith("devices.types.switch") }
                ?.map { it.id }
                .orEmpty()
            val targetIds = if (switchIds.isNotEmpty()) switchIds else current?.devices
                ?.filter { it.hasOnOff && it.isOn }
                ?.map { it.id }
                .orEmpty()

            if (current != null && targetIds.isNotEmpty()) {
                val optimistic = current.copy(
                    devices = current.devices.map { if (targetIds.contains(it.id)) it.copy(isOn = false) else it }
                )
                prefs.saveCachedHome(optimistic)
                updateTile(optimistic, true)
                AliceSwitchesWidgetProvider.updateAllWidgets(applicationContext, optimistic)
                api.turnOffDevices(token, targetIds)
            }

            api.fetchHome(token).onSuccess { fresh ->
                prefs.saveCachedHome(fresh)
                updateTile(fresh, true)
                AliceSwitchesWidgetProvider.updateAllWidgets(applicationContext, fresh)
            }
        }
    }

    private fun updateTile(snapshot: SmartHomeSnapshot?, hasToken: Boolean) {
        val tile = qsTile ?: return
        if (!hasToken) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Выкл всё"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Войдите в Алису"
            }
            tile.updateTile()
            return
        }
        val activeSwitches = snapshot?.devices?.count {
            it.hasOnOff && it.isOn && it.type.startsWith("devices.types.switch")
        } ?: 0
        tile.state = if (activeSwitches > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Выкл всё"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (activeSwitches > 0) "$activeSwitches вкл." else "Всё выкл."
        }
        tile.updateTile()
    }
}
