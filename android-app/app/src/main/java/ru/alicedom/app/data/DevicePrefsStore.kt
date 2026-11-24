package ru.alicedom.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

sealed interface SwitchGridItem {
    val stableKey: String

    data class Single(val device: SmartDevice) : SwitchGridItem {
        override val stableKey: String get() = "s:${device.id}"
    }

    data class Double(
        val pairKey: String,
        val left: SmartDevice,
        val right: SmartDevice,
        val isAuto: Boolean,
        val commonTitle: String
    ) : SwitchGridItem {
        override val stableKey: String get() = "d:$pairKey"
    }
}

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

    fun getManualSwitchPairs(): List<Pair<String, String>> {
        val raw = prefs.getString(KEY_MANUAL_PAIRS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val pairArr = arr.optJSONArray(i) ?: continue
                    if (pairArr.length() == 2) {
                        add(pairArr.getString(0) to pairArr.getString(1))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getBlockedAutoPairs(): Set<String> = readIdList(KEY_BLOCKED_PAIRS).toSet()

    fun addManualSwitchPair(idA: String, idB: String) {
        if (idA == idB) return
        val pKey = makeSortedPairKey(idA, idB)
        val blocked = getBlockedAutoPairs().filter { it != pKey }
        writeIdList(KEY_BLOCKED_PAIRS, blocked)

        val current = getManualSwitchPairs().filter { (a, b) ->
            a != idA && b != idA && a != idB && b != idB
        }.toMutableList()
        current.add(idA to idB)
        writeManualPairs(current)
    }

    fun splitSwitchPair(idA: String, idB: String) {
        val pKey = makeSortedPairKey(idA, idB)
        val current = getManualSwitchPairs().filter { (a, b) ->
            !((a == idA && b == idB) || (a == idB && b == idA))
        }
        writeManualPairs(current)

        val blocked = getBlockedAutoPairs().toMutableSet()
        blocked.add(pKey)
        writeIdList(KEY_BLOCKED_PAIRS, blocked.toList())
    }

    private fun writeManualPairs(pairs: List<Pair<String, String>>) {
        val arr = JSONArray()
        pairs.forEach { (a, b) ->
            arr.put(JSONArray().put(a).put(b))
        }
        prefs.edit().putString(KEY_MANUAL_PAIRS, arr.toString()).apply()
    }

    fun groupDoubleSwitches(devices: List<SmartDevice>): List<SwitchGridItem> {
        val byId = devices.associateBy { it.id }
        val manualPairs = getManualSwitchPairs()
        val blocked = getBlockedAutoPairs()
        val pairedPartner = mutableMapOf<String, Pair<SmartDevice, Boolean>>() // id -> (partner, isAuto)

        // 1. Manual pairs first
        for ((idA, idB) in manualPairs) {
            if (idA == idB) continue
            val devA = byId[idA] ?: continue
            val devB = byId[idB] ?: continue
            if (pairedPartner.containsKey(idA) || pairedPartner.containsKey(idB)) continue
            pairedPartner[idA] = devB to false
            pairedPartner[idB] = devA to false
        }

        // 2. Auto-detect by external_id stem within same room + skill
        val extBuckets = linkedMapOf<String, MutableList<SmartDevice>>()
        for (dev in devices) {
            if (pairedPartner.containsKey(dev.id)) continue
            val stem = extractExternalIdStem(dev.externalId) ?: continue
            val key = "${dev.roomId.orEmpty()}|${dev.skillId.orEmpty()}|$stem"
            extBuckets.getOrPut(key) { mutableListOf() }.add(dev)
        }
        for (group in extBuckets.values) {
            if (group.size == 2) {
                val (a, b) = group
                val pKey = makeSortedPairKey(a.id, b.id)
                if (!blocked.contains(pKey) && !pairedPartner.containsKey(a.id) && !pairedPartner.containsKey(b.id)) {
                    pairedPartner[a.id] = b to true
                    pairedPartner[b.id] = a to true
                }
            }
        }

        // 3. Auto-detect by name stem within same room + skill
        val nameBuckets = linkedMapOf<String, MutableList<SmartDevice>>()
        for (dev in devices) {
            if (pairedPartner.containsKey(dev.id)) continue
            val (stem, _) = extractNameStemAndSide(dev.name)
            if (stem == null) continue
            if (dev.roomId == null && stem.length < 2) continue
            val key = "${dev.roomId.orEmpty()}|${dev.skillId.orEmpty()}|$stem"
            nameBuckets.getOrPut(key) { mutableListOf() }.add(dev)
        }
        for (group in nameBuckets.values) {
            if (group.size == 2) {
                val (a, b) = group
                val pKey = makeSortedPairKey(a.id, b.id)
                if (!blocked.contains(pKey) && !pairedPartner.containsKey(a.id) && !pairedPartner.containsKey(b.id)) {
                    pairedPartner[a.id] = b to true
                    pairedPartner[b.id] = a to true
                }
            }
        }

        val emitted = mutableSetOf<String>()
        val result = mutableListOf<SwitchGridItem>()
        for (dev in devices) {
            if (emitted.contains(dev.id)) continue
            val pairInfo = pairedPartner[dev.id]
            if (pairInfo != null) {
                val (partner, isAuto) = pairInfo
                emitted.add(dev.id)
                emitted.add(partner.id)
                val (left, right) = if (isAuto) orderPairDevices(dev, partner) else (dev to partner)
                result.add(
                    SwitchGridItem.Double(
                        pairKey = makeSortedPairKey(left.id, right.id),
                        left = left,
                        right = right,
                        isAuto = isAuto,
                        commonTitle = computeCommonTitle(left, right)
                    )
                )
            } else {
                emitted.add(dev.id)
                result.add(SwitchGridItem.Single(dev))
            }
        }
        return result
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
                put("externalId", d.externalId ?: JSONObject.NULL)
                put("skillId", d.skillId ?: JSONObject.NULL)
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
                        primarySensorText = if (d.isNull("primarySensorText")) null else d.optString("primarySensorText"),
                        externalId = if (d.isNull("externalId")) null else d.optString("externalId").takeIf { it.isNotBlank() },
                        skillId = if (d.isNull("skillId")) null else d.optString("skillId").takeIf { it.isNotBlank() }
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
        private const val KEY_MANUAL_PAIRS = "manual_switch_pairs"
        private const val KEY_BLOCKED_PAIRS = "blocked_auto_pairs"

        fun makeSortedPairKey(idA: String, idB: String): String =
            if (idA < idB) "$idA|$idB" else "$idB|$idA"

        private fun cleanStem(s: String): String =
            s.trim().trim { it in " -_.:/()[]" }.trim()

        private val EXT_KEYWORD_REGEX = Regex(
            "^(.+?)[-_.:/](?:switch|channel|ch|gang|button|btn|ep|key|relay)[-_.:/]?(?:[1-4]|left|right|l|r)$",
            RegexOption.IGNORE_CASE
        )
        private val EXT_SIDE_REGEX = Regex("^(.+?)[-_.:/](?:left|right)$", RegexOption.IGNORE_CASE)
        private val EXT_NUM_REGEX = Regex("^(.+?)[-_.:/]([12])$")

        fun extractExternalIdStem(externalId: String?): String? {
            val trimmed = externalId?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            EXT_KEYWORD_REGEX.matchEntire(trimmed)?.let { m ->
                val stem = m.groupValues[1]
                if (stem.length >= 3) return stem.lowercase()
            }
            EXT_SIDE_REGEX.matchEntire(trimmed)?.let { m ->
                val stem = m.groupValues[1]
                if (stem.length >= 4) return stem.lowercase()
            }
            EXT_NUM_REGEX.matchEntire(trimmed)?.let { m ->
                val stem = m.groupValues[1]
                if (stem.length >= 5) return stem.lowercase()
            }
            return null
        }

        private val NAME_SIDE_PATTERNS: List<Pair<Regex, Int>> = listOf(
            Regex("^(.*?)(?:\\s*[-–—(/]?\\s*(?:левый|левая|левое|слева|left)\\s*\\)?)$", RegexOption.IGNORE_CASE) to 0,
            Regex("^(.*?)(?:\\s*[-–—(/]?\\s*(?:правый|правая|правое|справа|right)\\s*\\)?)$", RegexOption.IGNORE_CASE) to 1,
            Regex("^(.*?)(?:\\s*[-–—(/]?\\s*(?:клавиша|кнопка|канал|линия|switch|key|button|gang|ch)?\\s*#?\\s*(?:1|I|L)\\s*\\)?)$", RegexOption.IGNORE_CASE) to 0,
            Regex("^(.*?)(?:\\s*[-–—(/]?\\s*(?:клавиша|кнопка|канал|линия|switch|key|button|gang|ch)?\\s*#?\\s*(?:2|II|R)\\s*\\)?)$", RegexOption.IGNORE_CASE) to 1
        )

        fun extractNameStemAndSide(name: String): Pair<String?, Int> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return null to 2
            for ((regex, rank) in NAME_SIDE_PATTERNS) {
                val m = regex.matchEntire(trimmed)
                if (m != null) {
                    val stem = cleanStem(m.groupValues[1])
                    return stem.lowercase() to rank
                }
            }
            return null to 2
        }

        private fun orderPairDevices(a: SmartDevice, b: SmartDevice): Pair<SmartDevice, SmartDevice> {
            val sideA = extractNameStemAndSide(a.name).second
            val sideB = extractNameStemAndSide(b.name).second
            if (sideA != sideB) {
                return if (sideA < sideB) (a to b) else (b to a)
            }
            val extA = a.externalId.orEmpty()
            val extB = b.externalId.orEmpty()
            if (extA.isNotEmpty() && extB.isNotEmpty() && extA != extB) {
                return if (extA <= extB) (a to b) else (b to a)
            }
            return if (a.name.lowercase() <= b.name.lowercase()) (a to b) else (b to a)
        }

        private fun computeCommonTitle(left: SmartDevice, right: SmartDevice): String {
            val sL = extractNameStemAndSide(left.name).first
            val sR = extractNameStemAndSide(right.name).first
            if (!sL.isNullOrEmpty() && sL == sR && sL.length >= 2) {
                val prefix = cleanStem(left.name.take(minOf(left.name.length, sL.length + 2)))
                if (prefix.length >= 2) return prefix
            }
            return "Двухклавишный выключатель"
        }
    }
}
