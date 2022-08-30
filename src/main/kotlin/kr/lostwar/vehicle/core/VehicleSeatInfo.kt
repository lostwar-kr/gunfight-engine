package kr.lostwar.vehicle.core

import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.ui.text.consoleWarn
import kr.lostwar.vehicle.VehicleEngine.logErrorNull
import org.bukkit.configuration.ConfigurationSection

class VehicleSeatInfo(
    val attachedWeapons: List<WeaponType?>?
) {
    companion object {

        fun Map<String, Any>.getSeatInfo(): VehicleSeatInfo? {
            val attachedWeapons = (get("attachedWeapons") as? List<String> ?: emptyList()).map {
                if(it.isBlank()) null else (WeaponType[it] ?: run {
                    consoleWarn("invalid weapon type ${it} while loading seat info")
                    null
                })
            }.takeIf { it.size in 1 .. 9 } ?: return logErrorNull("invalid size on attached weapons")

            return VehicleSeatInfo(
                attachedWeapons,
            )
        }
    }
}