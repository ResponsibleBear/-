package ru.alicedom.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DevicePrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("alice_dom_device_prefs", Context.MODE_PRIVATE)

    fun getFavoriteIds(): List<String> = readIdList(KEY_FAVORITES)

    fun isFavorite(deviceId: String): Boolean = getFavoriteIds().contains(deviceId)

    fun toggleFavorite(deviceId: String): Boolean {
        val list = getFavoriteIds().toMutableList()
        val added = if (list.contains(deviceId)) {
            list.remove(deviceId)
            false
        } else {
            list.add(deviceId)
            true
        }
        writeIdList(KEY_FAVORITES, list)
        return added
    }

    fun getHiddenIds(): Set<String> = readIdList(KEY_HIDDEN).toSet()

    fun isHidden(deviceId: String): Boolean = getHiddenIds().contains(deviceId)

    fun toggleHidden(deviceId: String): Boolean {
        val list = readIdList(KEY_HIDDEN).toMutableList()
        val hidden = if (list.contains(deviceId)) {
            list.remove(deviceId)
            false
        } else {
            list.add(deviceId)
            true
        }
        writeIdList(KEY_HIDDEN, list)
        return hidden
    }

    fun unhideAll() {
        writeIdList(KEY_HIDDEN, emptyList())
    }

    fun getCustomOrderIds(): List<String> = readIdList(KEY_ORDER)

    fun moveDevice(deviceId: String, orderedIds: List<String>, delta: Int) {
        val poolSet = orderedIds.toSet()
        val reorderedPool = orderedIds.toMutableList()
        val idx = reorderedPool.indexOf(deviceId)
        if (idx < 0) return
        val target = (idx + delta).coerceIn(0, reorderedPool.lastIndex)
        if (target == idx) return
        val item = reorderedPool.removeAt(idx)
        reorderedPool.add(target, item)

        val existingOutsidePool = getCustomOrderIds().filter { !poolSet.contains(it) }
        writeIdList(KEY_ORDER, existingOutsidePool + reorderedPool)
    }

    fun getWidgetDeviceIds(): List<String> = readIdList(KEY_WIDGET_IDS)

    fun toggleWidgetDevice(deviceId: String): Boolean {
        val list = getWidgetDeviceIds().toMutableList()
        val added = if (list.contains(deviceId)) {
            list.remove(deviceId)
            false
        } else {
            if (list.size >= 4) list.removeAt(0)
            list.add(deviceId)
            true
        }
        writeIdList(KEY_WIDGET_IDS, list)
        return added
    }

    fun clearWidgetDevices() {
        writeIdList(KEY_WIDGET_IDS, emptyList())
    }

    fun getQuickTileDeviceId(slot: Int): String? {
        val key = if (slot == 1) KEY_QS_TILE_1 else KEY_QS_TILE_2
        return prefs.getString(key, null)
    }

    fun setQuickTileDeviceId(slot: Int, deviceId: String?) {
        val key = if (slot == 1) KEY_QS_TILE_1 else KEY_QS_TILE_2
        if (deviceId == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, deviceId).apply()
        }
    }

    fun saveCachedHome(snapshot: SmartHomeSnapshot) {
        val roomsArr = JSONArray()
        snapshot.rooms.forEach { r ->
            roomsArr.put(JSONObject().put("id", r.id).put("name", r.name))
        }
        val devsArr = JSONArray()
        snapshot.devices.forEach { d ->
            devsArr.put(JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("type", d.type)
                put("roomId", d.roomId ?: JSONObject.NULL)
                put("hasOnOff", d.hasOnOff)
                put("isOn", d.isOn)
                put("brightness", d.brightness ?: JSONObject.NULL)
                put("temperatureTarget", d.temperatureTarget ?: JSONObject.NULL)
                put("primarySensorText", d.primarySensorText ?: JSONObject.NULL)
            })
        }
        val root = JSONObject()
            .put("rooms", roomsArr)
            .put("devices", devsArr)
        prefs.edit().putString(KEY_CACHED_HOME, root.toString()).apply()
    }

    fun loadCachedHome(): SmartHomeSnapshot? {
        val raw = prefs.getString(KEY_CACHED_HOME, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val rooms = mutableListOf<SmartRoom>()
            val rArr = root.optJSONArray("rooms") ?: JSONArray()
            for (i in 0 until rArr.length()) {
                val r = rArr.getJSONObject(i)
                rooms.add(SmartRoom(r.getString("id"), r.getString("name")))
            }
            val devices = mutableListOf<SmartDevice>()
            val dArr = root.optJSONArray("devices") ?: JSONArray()
            for (i in 0 until dArr.length()) {
                val d = dArr.getJSONObject(i)
                devices.add(
                    SmartDevice(
                        id = d.getString("id"),
                        name = d.getString("name"),
                        type = d.optString("type"),
                        roomId = if (d.isNull("roomId")) null else d.optString("roomId"),
                        hasOnOff = d.optBoolean("hasOnOff", false),
                        isOn = d.optBoolean("isOn", false),
                        brightness = if (d.isNull("brightness")) null else d.optInt("brightness"),
                        temperatureTarget = if (d.isNull("temperatureTarget")) null else d.optInt("temperatureTarget"),
                        primarySensorText = if (d.isNull("primarySensorText")) null else d.optString("primarySensorText")
                    )
                )
            }
            SmartHomeSnapshot(rooms = rooms, devices = devices, scenarios = emptyList())
        }.getOrNull()
    }

    fun sortAndFilter(devices: List<SmartDevice>, showHidden: Boolean): List<SmartDevice> {
        val favs = getFavoriteIds()
        val hidden = if (showHidden) emptySet() else getHiddenIds()
        val order = getCustomOrderIds()

        return devices
            .filter { !hidden.contains(it.id) }
            .sortedWith(
                compareBy<SmartDevice>(
                    { !favs.contains(it.id) },
                    {
                        val ordIdx = order.indexOf(it.id)
                        if (ordIdx >= 0) ordIdx else Int.MAX_VALUE
                    },
                    { devices.indexOf(it) }
                )
            )
    }

    fun resolveWidgetDevices(allDevices: List<SmartDevice>): List<SmartDevice> {
        val explicit = getWidgetDeviceIds()
        if (explicit.isNotEmpty()) {
            val map = allDevices.associateBy { it.id }
            val resolved = explicit.mapNotNull { map[it] }
            if (resolved.isNotEmpty()) return resolved.take(4)
        }
        val favs = getFavoriteIds()
        val hidden = getHiddenIds()
        val switches = allDevices.filter { it.hasOnOff && !hidden.contains(it.id) }
        val sorted = switches.sortedWith(
            compareBy<SmartDevice>(
                { !it.type.startsWith("devices.types.switch") },
                { !favs.contains(it.id) }
            )
        )
        return sorted.take(4)
    }

    private fun readIdList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i) }
        }.getOrDefault(emptyList())
    }

    private fun writeIdList(key: String, ids: List<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorite_device_ids"
        private const val KEY_HIDDEN = "hidden_device_ids"
        private const val KEY_ORDER = "custom_order_ids"
        private const val KEY_WIDGET_IDS = "widget_device_ids"
        private const val KEY_QS_TILE_1 = "qs_tile_1_device_id"
        private const val KEY_QS_TILE_2 = "qs_tile_2_device_id"
        private const val KEY_CACHED_HOME = "cached_home_snapshot"
    }
}
