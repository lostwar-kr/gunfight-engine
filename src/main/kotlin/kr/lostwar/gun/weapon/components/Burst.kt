package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponType
import org.bukkit.configuration.ConfigurationSection

class Burst(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Burst?,
) : WeaponComponent(config, weapon, parent) {

    val amount: Int = getInt("amount", parent?.amount, 1)
    val triggerDelay: Int = getInt("triggerDelay", parent?.triggerDelay, 1)
    val shootDelay: Int = getInt("shootDelay", parent?.shootDelay, 1)

}