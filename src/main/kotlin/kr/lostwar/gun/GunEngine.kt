package kr.lostwar.gun

import kr.lostwar.Engine
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.WeaponListener
import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.command.Command
import org.bukkit.event.Listener

object GunEngine : Engine("full-metal-jacket") {
    override val listeners: List<Listener> = listOf(
        WeaponListener,
        WeaponPlayer.Companion,
    )
    override val commands: List<Command> = listOf(
        WeaponCommand,
    )
    override fun onLoad() {
        log("무기 불러오기 ...")
        WeaponType.load()
    }

}