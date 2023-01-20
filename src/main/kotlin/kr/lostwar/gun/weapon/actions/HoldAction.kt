package kr.lostwar.gun.weapon.actions

import kr.lostwar.gun.weapon.Weapon

class HoldAction(
    weapon: Weapon,
) : DelayAction(weapon, weapon.type.item.holdingDuration) {

    override fun onStart() {
        weapon.type.item.holdingAnimation.play(player, weapon.type)
    }

}