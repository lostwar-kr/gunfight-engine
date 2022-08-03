package kr.lostwar.vehicle.core

import kr.lostwar.util.math.VectorUtil.getBukkitVector
import kr.lostwar.vehicle.VehicleEngine
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.util.Vector

class VehicleTurretInfo(
    val seat: Int,
    val slotIndexes: Set<Int>,
    val localShootPosition: Vector,
    val rotateLerpSpeed: Double = 1.0,
    val usePitchRotation: Boolean = false,
    val disableShootModify: Boolean = false,
) {

    companion object {
        private fun ConfigurationSection.getIntList(key: String): List<Int> {
            val raw = get(key) ?: return emptyList()
            if(raw is Int) {
                return listOf(raw)
            }
            if(raw is String) {
                return raw
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
            }
            return getIntegerList(key)
        }

        fun ConfigurationSection.getTurretInfo(key: String): VehicleTurretInfo? {
            val section = getConfigurationSection(key) ?: return null

            val seat = section.getInt("seat", 0)
            val seatIndexes = section.getIntList("slot")
            if(seatIndexes.isEmpty()) {
                return VehicleEngine.logErrorNull("invalid turret info ${key}: empty seat indexes")
            }
            val localShootPosition = section.getBukkitVector("shootPosition") ?: Vector()

            return VehicleTurretInfo(
                seat,
                seatIndexes.toHashSet(),
                localShootPosition,
            )
        }
    }
}