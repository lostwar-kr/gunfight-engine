package kr.lostwar.vehicle.core

import kr.lostwar.gun.weapon.Weapon
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.event.entity.EntityDamageEvent.DamageCause

data class VehicleEntityDamage(
    val vehicle: VehicleEntity<*>,
    var amount: Double,
    val cause: DamageCause,
    val victim: ArmorStand?,
    var damager: Entity?,
    var weapon: Weapon?,
) {
    override fun hashCode(): Int {
        var hash = cause.hashCode()
//        hash = hash * 31 + (victim?.hashCode() ?: 0)
        hash = hash * 31 + (damager?.hashCode() ?: 0)
        return hash
    }
}