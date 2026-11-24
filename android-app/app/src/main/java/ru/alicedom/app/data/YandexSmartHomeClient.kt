package ru.alicedom.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SmartRoom(val id: String, val name: String)
data class SmartScenario(val id: String, val name: String, val isActive: Boolean)
data class SmartDevice(
    val id: String,
    val name: String,
    val type: String,
    val roomId: String?,
    val hasOnOff: Boolean,
    val isOn: Boolean,
    val brightness: Int?,
    val temperatureTarget: Int?,
    val primarySensorText: String?,
    val externalId: String? = null,
    val skillId: String? = null
)
data class SmartHomeSnapshot(
    val rooms: List<SmartRoom>,
    val devices: List<SmartDevice>,
    val scenarios: List<SmartScenario>
)

class YandexSmartHomeClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchHome(token: String): Result<SmartHomeSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/user/info")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            http.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    if (res.code == 401 || res.code == 403) {
                        error("Токен отклонён Яндексом (${res.code}). Проверьте права iot:view и iot:control.")
                    }
                    error("Ошибка Яндекс API: ${res.code}")
                }
                parseUserInfo(JSONObject(body))
            }
        }
    }

    suspend fun togglePower(token: String, deviceId: String, turnOn: Boolean): Result<Unit> =
        sendCapability(
            token = token,
            deviceId = deviceId,
            type = "devices.capabilities.on_off",
            instance = "on",
            value = turnOn
        )

    suspend fun turnOffDevices(token: String, deviceIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        if (deviceIds.isEmpty()) return@withContext Result.success(Unit)
        runCatching {
            val devicesArray = JSONArray()
            for (id in deviceIds) {
                devicesArray.put(JSONObject().apply {
                    put("id", id)
                    put("actions", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "devices.capabilities.on_off")
                            put("state", JSONObject().apply {
                                put("instance", "on")
                                put("value", false)
                            })
                        })
                    })
                })
            }
            val payload = JSONObject().put("devices", devicesArray)
            val req = Request.Builder()
                .url("$BASE_URL/devices/actions")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("Команда отклонена (${res.code})")
            }
        }
    }

    suspend fun setBrightness(token: String, deviceId: String, value: Int): Result<Unit> =
        sendCapability(
            token = token,
            deviceId = deviceId,
            type = "devices.capabilities.range",
            instance = "brightness",
            value = value.coerceIn(1, 100)
        )

    suspend fun runScenario(token: String, scenarioId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/scenarios/$scenarioId/actions")
                .header("Authorization", "Bearer $token")
                .post("".toRequestBody(jsonMediaType))
                .build()

            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("Не удалось запустить сценарий (${res.code})")
            }
        }
    }

    private suspend fun sendCapability(
        token: String,
        deviceId: String,
        type: String,
        instance: String,
        value: Any
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("devices", JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", deviceId)
                        put("actions", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", type)
                                put("state", JSONObject().apply {
                                    put("instance", instance)
                                    put("value", value)
                                })
                            })
                        })
                    })
                })
            }

            val req = Request.Builder()
                .url("$BASE_URL/devices/actions")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("Команда отклонена (${res.code})")
            }
        }
    }

    private fun parseUserInfo(root: JSONObject): SmartHomeSnapshot {
        val rooms = mutableListOf<SmartRoom>()
        val roomsJson = root.optJSONArray("rooms") ?: JSONArray()
        for (i in 0 until roomsJson.length()) {
            val r = roomsJson.getJSONObject(i)
            rooms.add(SmartRoom(r.getString("id"), r.getString("name")))
        }

        val scenarios = mutableListOf<SmartScenario>()
        val scJson = root.optJSONArray("scenarios") ?: JSONArray()
        for (i in 0 until scJson.length()) {
            val s = scJson.getJSONObject(i)
            scenarios.add(SmartScenario(s.getString("id"), s.getString("name"), s.optBoolean("is_active", true)))
        }

        val devices = mutableListOf<SmartDevice>()
        val devJson = root.optJSONArray("devices") ?: JSONArray()
        for (i in 0 until devJson.length()) {
            val d = devJson.getJSONObject(i)
            val caps = d.optJSONArray("capabilities") ?: JSONArray()
            var hasOnOff = false
            var isOn = false
            var brightness: Int? = null
            var tempTarget: Int? = null

            for (j in 0 until caps.length()) {
                val cap = caps.getJSONObject(j)
                val capType = cap.optString("type")
                val state = cap.optJSONObject("state")
                val params = cap.optJSONObject("parameters")
                if (capType == "devices.capabilities.on_off") {
                    hasOnOff = true
                    isOn = state?.optBoolean("value", false) ?: false
                } else if (capType == "devices.capabilities.range") {
                    val inst = state?.optString("instance") ?: params?.optString("instance")
                    if (inst == "brightness" && state?.has("value") == true) {
                        brightness = state.optInt("value")
                    } else if (inst == "temperature" && state?.has("value") == true) {
                        tempTarget = state.optInt("value")
                    }
                }
            }

            val props = d.optJSONArray("properties") ?: JSONArray()
            var sensorText: String? = null
            if (props.length() > 0) {
                val p = props.getJSONObject(0)
                val st = p.optJSONObject("state")
                val inst = st?.optString("instance")
                if (st != null && st.has("value")) {
                    val v = st.get("value")
                    sensorText = when (inst) {
                        "temperature" -> "$v °C"
                        "humidity" -> "$v%"
                        "power" -> "$v Вт"
                        else -> v.toString()
                    }
                }
            }

            devices.add(
                SmartDevice(
                    id = d.getString("id"),
                    name = d.getString("name"),
                    type = d.optString("type"),
                    roomId = if (d.isNull("room")) null else d.optString("room"),
                    hasOnOff = hasOnOff,
                    isOn = isOn,
                    brightness = brightness,
                    temperatureTarget = tempTarget,
                    primarySensorText = sensorText,
                    externalId = if (d.isNull("external_id")) null else d.optString("external_id").takeIf { it.isNotBlank() },
                    skillId = if (d.isNull("skill_id")) null else d.optString("skill_id").takeIf { it.isNotBlank() }
                )
            )
        }

        return SmartHomeSnapshot(rooms = rooms, devices = devices, scenarios = scenarios)
    }

    companion object {
        private const val BASE_URL = "https://api.iot.yandex.net/v1.0"
    }
}
