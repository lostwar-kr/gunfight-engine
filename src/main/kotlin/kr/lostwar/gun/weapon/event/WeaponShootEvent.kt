package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.actions.ShootAction

class WeaponShootEvent(
    player: WeaponPlayer,
    val action: ShootAction,
) : WeaponPlayerEvent(player) {
}