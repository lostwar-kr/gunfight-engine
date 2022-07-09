package kr.lostwar.vehicle.core

import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemData.Companion.getItemData
import kr.lostwar.util.item.ItemData.Companion.toItemData
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.getBukkitVector
import kr.lostwar.util.math.VectorUtil.toVectorString
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.core.VehicleHitbox.Companion.getVehicleHitbox
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector

class VehicleModelInfo(
    val key: String,
    val type: EquipmentSlot = EquipmentSlot.HEAD,
    private val offset: Vector = Vector(),
    val hitbox: VehicleHitbox = VehicleHitbox.emptyHitbox,
    val item: ItemData = ItemData(Material.AIR),
) {
    val localPosition: Vector; get() = offset.clone()

    companion object {
        val defaultModelInfo = VehicleModelInfo("default")
        fun ConfigurationSection.getModelInfo(key: String, default: VehicleModelInfo? = defaultModelInfo): VehicleModelInfo? {
            val section = getConfigurationSection(key) ?: return null

            val type = section.getString("type", default?.type?.toString() ?: EquipmentSlot.HEAD.toString())?.let {
                try { EquipmentSlot.valueOf(it) }
                catch (e: Exception){ null }
            }
            ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid type ${section.getString("type")}")
            val offset = section.getBukkitVector("offset") ?: default?.offset ?: VectorUtil.ZERO
            val hitbox = section.getVehicleHitbox("hitbox") ?: default?.hitbox ?: VehicleHitbox.emptyHitbox
            val item = section.getItemData("item", default?.item ?: ItemData(Material.AIR))!!
            return VehicleModelInfo(key, type, offset, hitbox, item)
        }
        fun ConfigurationSection.getModelInfoList(key: String, default: List<VehicleModelInfo> = emptyList()): List<VehicleModelInfo> {
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

            val rawOffset = (get("offset") ?: default?.offset?.toString() ?: VectorUtil.ZERO.toVectorString()) as? String
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid offset")
            val rawOffsetSplit = rawOffset.split(',').map { it.trim() }.takeIf { it.size >= 3 }
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid offset ${rawOffset}")
            val offsetX = rawOffsetSplit[0].toDoubleOrNull()
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid offset ${rawOffset}")
            val offsetY = rawOffsetSplit[1].toDoubleOrNull()
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid offset ${rawOffset}")
            val offsetZ = rawOffsetSplit[2].toDoubleOrNull()
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid offset ${rawOffset}")
            val offset = Vector(offsetX, offsetY, offsetZ)

            val rawHitbox = (get("hitbox") ?: default?.hitbox?.toString() ?: VehicleHitbox.emptyHitbox.toString()) as? String
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid hitbox")
            val rawHitboxSplit = rawOffset.split(',').map { it.trim() }.takeIf { it.size >= 2 }
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid hitbox ${rawHitbox}")
            val hitboxWidth = rawHitboxSplit[0].toFloatOrNull()
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid hitbox ${rawHitbox}")
            val hitboxHeight = rawHitboxSplit[1].toFloatOrNull()
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid hitbox ${rawHitbox}")
            val hitbox = VehicleHitbox(hitboxWidth, hitboxHeight)

            val rawItem = (get("item") ?: default?.item?.toString() ?: ItemData(Material.AIR).toString()) as? String
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid item")
            val item = toItemData(rawItem)
                ?: return VehicleEngine.logErrorNull("cannot parse ModelInfo: invalid item ${rawItem}")

            return VehicleModelInfo(key, type, offset, hitbox, item)
        }
    }
}