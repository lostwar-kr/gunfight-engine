package kr.lostwar.vehicle.core

import kr.lostwar.util.nms.NMSUtil.setEntitySize
import kr.lostwar.vehicle.VehicleEngine
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Entity

class VehicleHitbox(
    val width: Float,
    val height: Float,
) {
    companion object {
        val emptyHitbox = VehicleHitbox(0f, 0f)
        fun ConfigurationSection.getVehicleHitbox(key: String): VehicleHitbox? {
            val raw = getString(key) ?: return null
            return parseFromString(raw)
        }
        fun parseFromString(raw: String?): VehicleHitbox? {
            if(raw == null) return null
            val split = raw.split(',').map { it.trim() }
            if(split.size < 2) return VehicleEngine.logErrorNull("failed to parse hitbox ${raw}: too few arguments")
            val width = split[0].toFloatOrNull()
                ?: return VehicleEngine.logErrorNull("failed to parse hitbox ${raw}: invalid width")
            val height = split[1].toFloatOrNull()
                ?: return VehicleEngine.logErrorNull("failed to parse hitbox ${raw}: invalid height")
            return VehicleHitbox(width, height)
        }
    }

    override fun toString(): String {
        return "$width, $height"
    }
    fun apply(entity: Entity) {
        entity.setEntitySize(width, height)
    }
}