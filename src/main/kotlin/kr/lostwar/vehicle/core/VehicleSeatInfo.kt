package kr.lostwar.vehicle.core

import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.toVectorString
import kr.lostwar.util.ui.text.console
import kr.lostwar.util.ui.text.consoleWarn
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.VehicleEngine.logErrorNull
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.util.Vector

class VehicleSeatInfo(
    val attachedWeapons: List<WeaponType?>?,
    val exitOffset: Vector,
) {
    companion object {

        fun Map<String, Any>.getSeatInfo(default: VehicleSeatInfo?): VehicleSeatInfo? {
            val attachedWeapons = (get("attachedWeapons") as? List<String> ?: emptyList()).map {
                if(it.isBlank()) null else (WeaponType[it] ?: run {
                    consoleWarn("invalid weapon type ${it} while loading seat info")
                    null
                })
            }.takeIf { it.size in 0 .. 9 } ?: return logErrorNull("invalid size on attached weapons")

            val rawOffset = (get("exitOffset") ?: default?.exitOffset?.toString() ?: VectorUtil.ZERO.toVectorString()) as? String
                ?: return VehicleEngine.logErrorNull("cannot parse VehicleSeatInfo: invalid exitOffset")
            val offset = VectorUtil.fromVectorString(rawOffset)
                ?: return VehicleEngine.logErrorNull("cannot parse VehicleSeatInfo: invalid exitOffset ${rawOffset}")
            return VehicleSeatInfo(
                attachedWeapons,
                offset,
            )
        }
    }
}