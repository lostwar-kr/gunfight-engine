package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.AnimationClip
import org.bukkit.configuration.ConfigurationSection

class FullAuto(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: FullAuto?,
) : WeaponComponent(config, weapon, parent) {

    val delay: Int = getInt("delay", parent?.delay, 1)

    val shootAnimationLoop: AnimationClip = getAnimationClip("shootAnimationLoop", parent?.shootAnimationLoop)
    val shootAnimationLoopStop: AnimationClip = getAnimationClip("shootAnimationLoopStop", parent?.shootAnimationLoopStop)

}