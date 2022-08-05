package kr.lostwar.vehicle.core.animation

import kr.lostwar.vehicle.VehicleEngine.logErrorNull
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemData.Companion.getItemData
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.configuration.ConfigurationSection

class VehicleModelAnimation(
    val event: String,
    val itemMap: Map<String, ItemData>
) {
    companion object {
        val default = VehicleModelAnimation("", emptyMap())
        fun ConfigurationSection.getAnimation(info: VehicleInfo, eventKey: String, default: VehicleModelAnimation? = VehicleModelAnimation.default): VehicleModelAnimation? {
            val section = getConfigurationSection(eventKey) ?: return default

            val itemMap = buildMap {
                default?.let { putAll(it.itemMap) }
                for(modelKey in section.getKeys(false)) {
                    info.models[modelKey]
                        ?: return logErrorNull("failed to load vehicle animation ${eventKey} on ${info.key}: invalid model key ${modelKey}")
                    val item = section.getItemData(modelKey, default?.itemMap?.get(modelKey))
                        ?: return logErrorNull("failed to load vehicle animation ${eventKey} on ${info.key}: invalid item")
                    put(modelKey, item)
                }
            }
            return VehicleModelAnimation(eventKey, itemMap)
        }
    }
}