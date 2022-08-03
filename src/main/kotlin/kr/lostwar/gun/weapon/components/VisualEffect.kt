package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponType
import org.bukkit.configuration.ConfigurationSection

class VisualEffect(
    config: ConfigurationSection,
    weapon: WeaponType,
    parent: VisualEffect?,
) : WeaponComponent(config, weapon, parent) {
    
}