package kr.lostwar.gun.weapon.actions

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponAction

open class DelayAction(
    weapon: Weapon,
    ticks: Int,
) : WeaponAction(weapon) {
    var count = ticks; private set
    override fun onTick() {
        if(count > 0) {
            --count
            return
        }
        end()
    }

    override fun toString(): String {
        return super.toString()+"(left=${count})"
    }

}