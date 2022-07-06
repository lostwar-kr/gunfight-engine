package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.Location
import org.bukkit.util.Vector

class WeaponHitscanShootEvent(
    player: WeaponPlayer,
    var thickness: Double,
    var nearThickness: Double,
    var nearThicknessRange: Double,
) : WeaponPlayerEvent(player) {
    operator fun component1() = thickness
    operator fun component2() = nearThickness
    operator fun component3() = nearThicknessRange

    val onShoot = ArrayList<WeaponPlayer.(origin: Location, direction: Vector) -> Unit>()
    val onScan = ArrayList<WeaponPlayer.(position: Location) -> Unit>()

}