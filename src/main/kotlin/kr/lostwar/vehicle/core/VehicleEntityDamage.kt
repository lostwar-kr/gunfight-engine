package kr.lostwar.vehicle.core

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.ExtraUtil.joinToString
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.event.entity.EntityDamageEvent.DamageCause

data class VehicleEntityDamage(
    val vehicle: VehicleEntity<*>,
    var amount: Double,
    val cause: DamageCause,
    val victim: ArmorStand?,
    var damager: Entity?,
    val weaponType: WeaponType?,
) {
    override fun hashCode(): Int {
        var hash = cause.hashCode()
//        hash = hash * 31 + (victim?.hashCode() ?: 0)
        hash = hash * 31 + (damager?.hashCode() ?: 0)
        return hash
    }

    override fun toString(): String {
        return mutableListOf(
            vehicle.toString(),
            cause.toString(),
            amount.toString(),
        ).apply {
            if(victim != null) add("victim=${victim}")
            if(damager != null) add("damager=${damager}")
            if(weaponType != null) add("weapon=${weaponType}")
        }.joinToString(", ", "VehicleEntityDamage[", "]") { it }
    }
}