package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.block.Block

class WeaponBlockHitEvent(
    player: WeaponPlayer,
    val block: Block,
    var resistanceFactor: Double,
) : WeaponPlayerEvent(player) {

    var isBlockPierced = false
}