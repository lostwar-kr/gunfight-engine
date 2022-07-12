package kr.lostwar.vehicle.core

import kr.lostwar.vehicle.core.VehicleEntity.Companion.asVehicleEntityOrNull
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player

class SeatEntity(
    val info: VehicleModelInfo,
    val entity: ArmorStand,
    val vehicle: VehicleEntity,
) : ArmorStand by entity {

    operator fun component1() = info
    operator fun component2() = entity

    var passenger: Player? = null
        set(value) {
            field = value
        }

    fun ride(player: Player): Boolean {
        if(passenger != null) {
            return false
        }
        // 이전에 타고 있던 차량이 있을 때 하차 처리
        val oldRiding = player.vehicle
        var reseat = false
        oldRiding?.asVehicleEntityOrNull?.let { oldVehicle ->
            if(oldVehicle == vehicle) {
                for(entity in vehicle.seatEntities){
                    if(entity.entityId == oldRiding.entityId) {
                        entity.exit(player)
                        reseat = true
                        break
                    }
                }
            }
        }
        passenger = player
        entity.addPassenger(player)
        return true
    }

    fun exit(player: Player, forced: Boolean = false): Boolean {
        if(!forced && (passenger == null)){
            return false
        }
        passenger = null
        entity.eject()
        return true
    }

}