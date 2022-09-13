package kr.lostwar.vehicle.core

import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemData.Companion.getItemData
import kr.lostwar.util.item.ItemData.Companion.toItemData
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.getBukkitVector
import kr.lostwar.util.math.VectorUtil.toVectorString
import kr.lostwar.util.ui.text.console
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.core.VehicleHitbox.Companion.getVehicleHitbox
import kr.lostwar.vehicle.core.VehicleSeatInfo.Companion.getSeatInfo
import kr.lostwar.vehicle.core.VehicleTurretInfo.Companion.getTurretInfo
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector

class VehicleModelInfo(
    val key: String,
    val type: EquipmentSlot = EquipmentSlot.HEAD,
    val localPosition: Vector = Vector(),
    val hitbox: VehicleHitbox = VehicleHitbox.emptyHitbox,
    val item: ItemData = ItemData(Material.AIR),
    val isSmall: Boolean = false,
    val noMarker: Boolean = false,
    val parentKey: String? = null,
    val turretInfo: VehicleTurretInfo? = null,
    val seatInfo: VehicleSeatInfo? = null,
) {
    var parent: VehicleModelInfo? = null
    val isKinematicEntity = !hitbox.isEmpty()

    companion object {
        val defaultModelInfo = VehicleModelInfo("default")
        fun ConfigurationSection.getModelInfo(key: String, default: VehicleModelInfo? = defaultModelInfo): VehicleModelInfo? {
            val section = getConfigurationSection(key) ?: return null

            val type = section.getString("type", default?.type?.toString() ?: EquipmentSlot.HEAD.toString())?.let {
                try { EquipmentSlot.valueOf(it) }
                catch (e: Exception){ null }
            }
            ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid type ${section.getString("type")}")
            val offset = section.getBukkitVector("offset") ?: default?.localPosition ?: VectorUtil.ZERO
            val hitbox = section.getVehicleHitbox("hitbox") ?: default?.hitbox ?: VehicleHitbox.emptyHitbox
            val item = section.getItemData("item", default?.item ?: ItemData(Material.AIR))!!
            val small = section.getBoolean("small", default?.isSmall ?: false)
            val noMarker = section.getBoolean("noMarker", default?.noMarker ?: false)
            val parentKey = section.getString("parent")
            val turretInfo = section.getTurretInfo("turret")
            return VehicleModelInfo(
                key,
                type,
                offset,
                hitbox,
                item,
                small,
                noMarker,
                parentKey,
                turretInfo,
                null, // no seat info on model
            )
        }
        fun ConfigurationSection.getModelInfoList(key: String, default: List<VehicleModelInfo> = emptyList()): List<VehicleModelInfo> {
            if(!isList(key)) {
                return default
            }
            return getMapList(key)
                .mapIndexedNotNull { index, map -> map.getModelInfo(index.toString(), if(default.size <= index) null else default[index]) }
        }
        fun Map<*, *>.getModelInfo(key: String, default: VehicleModelInfo?): VehicleModelInfo? {
            val rawType = (get("type") ?: default?.type?.toString() ?: EquipmentSlot.HEAD.toString()) as? String
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid type")
            val type =
                try { EquipmentSlot.valueOf(rawType) }
                catch (e: Exception){ null }
            ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid type ${rawType}")

            val rawOffset = (get("offset") ?: default?.localPosition?.toString() ?: VectorUtil.ZERO.toVectorString()) as? String
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid offset")
            val offset = VectorUtil.fromVectorString(rawOffset)
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid offset ${rawOffset}")

            val rawHitbox = (get("hitbox") ?: default?.hitbox?.toString() ?: VehicleHitbox.emptyHitbox.toString()) as? String
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid hitbox")
            val hitbox = VehicleHitbox.parseFromString(rawHitbox)
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid hitbox ${rawHitbox}")
//            VehicleEngine.log("${key} - hitbox: ${hitbox}")

            val rawItem = (get("item") ?: default?.item?.toString() ?: ItemData(Material.AIR).toString()) as? String
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid item")
            val item = toItemData(rawItem)
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid item ${rawItem}")

            val small = (get("small") ?: default?.isSmall ?: false) as? Boolean
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid isSmall")
            val noMarker = (get("noMarker") ?: default?.noMarker ?: false) as? Boolean
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid noMarker")

            val parentKey = (get("parent") ?: default?.parentKey) as? String

//            console("seat: ${get("seat")}")
            val seatInfo = (get("seat") as? Map<String, Any>)?.getSeatInfo(default?.seatInfo) ?: default?.seatInfo

            return VehicleModelInfo(
                key,
                type,
                offset,
                hitbox,
                item,
                small,
                noMarker,
                parentKey,
                null, // no turret info at seat
                seatInfo,
            )
        }
    }
}