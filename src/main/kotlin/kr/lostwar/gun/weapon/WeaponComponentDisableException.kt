package kr.lostwar.gun.weapon

class WeaponComponentDisableException(
    val component: WeaponComponent
) : java.lang.Exception("component ${component.name} is disabled")
