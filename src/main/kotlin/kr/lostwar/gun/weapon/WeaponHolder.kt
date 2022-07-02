package kr.lostwar.gun.weapon

interface WeaponHolder {
    var weapon: Weapon?
    val isHoldingWeapon: Boolean; get() = weapon != null
}