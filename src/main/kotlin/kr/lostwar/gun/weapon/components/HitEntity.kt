package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.event.WeaponHitEntityEvent
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity

class HitEntity(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: HitEntity?,
) : WeaponComponent(config, weapon, parent, true) {

    val damage: Double = getDouble("damage", parent?.damage)
    val headShotDamageAdd: Double = getDouble("headShot.damageAdd", parent?.headShotDamageAdd, 0.0)
    val headShotDamageMultiply: Double = getDouble("headShot.damageMultiply", parent?.headShotDamageMultiply, 2.0)

    fun WeaponPlayer.hit(
        victim: LivingEntity,
        damage: Double = this@HitEntity.damage,
        damageSource: Entity? = null,
        location: Location? = null,
        isHeadShot: Boolean = false,
        isPiercing: Boolean = false,
        damageModifier: (Double) -> Double = { it },
    ): WeaponHitEntityEvent.DamageResult {
        val originalDamage = (if(isHeadShot) damage * headShotDamageMultiply else damage) + (if(isHeadShot) headShotDamageAdd else 0.0)
        val finalDamage = damageModifier(originalDamage)
        val event = WeaponHitEntityEvent(this,
            victim,
            finalDamage,
            damageSource,
            location,
            isHeadShot,
            isPiercing
        )
            .callEventOnHoldingWeapon(true)
        if(event.isCancelled) {
            return event.result
        }
        val damage = event.damage
        if(victim.health - damage <= 0) {
            victim.killer = player
        }
        victim.damage(damage)
        return event.result
    }

}