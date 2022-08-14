package kr.lostwar.vehicle.core

import org.bukkit.World

class SavedVehicleEntity private constructor(
    val world: World,
    val base: VehicleInfo,
    val transform: VehicleTransform,
    val health: Double,
    val decoration: Boolean,
    private val map: HashMap<String, Any>
) : MutableMap<String, Any> by map {

    fun spawn(): VehicleEntity<out VehicleInfo> {
        return base.spawn(transform.toLocation(world), decoration).apply {
            health = this@SavedVehicleEntity.health
            apply(this@SavedVehicleEntity)
        }
    }

    fun <T : Any> getAs(key: String): T? {
        return get(key) as? T
    }

    companion object {
        fun save(vehicleEntity: VehicleEntity<*>, block: MutableMap<String, Any>.() -> Unit): SavedVehicleEntity {
            val base = vehicleEntity.base
            val transform = vehicleEntity.transform
            val health = vehicleEntity.health
            val decoration = vehicleEntity.decoration
            val map = hashMapOf<String, Any>()
            block(map)
            return SavedVehicleEntity(vehicleEntity.world, base, transform, health, decoration, map)
        }
    }

}