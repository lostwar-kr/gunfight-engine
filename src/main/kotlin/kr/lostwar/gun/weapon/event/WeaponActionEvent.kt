package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponAction
import kr.lostwar.gun.weapon.WeaponPlayer

class WeaponActionStartEvent(
    player: WeaponPlayer,
    val oldAction: WeaponAction?,
    val newAction: WeaponAction,
) : WeaponPlayerEvent(player) {
    override val weapon: Weapon = newAction.weapon
    init {
        GunEngine.log("init WeaponActionStart(old=${oldAction}, new=${newAction})")
    }
}
class WeaponActionEndEvent(
    player: WeaponPlayer,
    val oldAction: WeaponAction,
    private val initialNewAction: WeaponAction?,
) : WeaponPlayerEvent(player) {
    /**
     * 새로 시작할 action입니다. End 시 결정된 다음 action이 없어야 합니다.
     */
    var newAction: WeaponAction? = initialNewAction
        get() { return initialNewAction ?: field }
    override val weapon: Weapon = oldAction.weapon
    val isWeaponChanged = weapon != player.weapon
    init {
        GunEngine.log("init WeaponActionEndEvent(old=${oldAction}, new=${newAction})")
    }
}