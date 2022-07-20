package kr.lostwar.gun

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import kr.lostwar.Engine
import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.WeaponListener
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.util.ui.text.console
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
    override fun onLoad(reload: Boolean) {
        /*
        if(!reload) {
            ProtocolLibrary.getProtocolManager().addPacketListener(object : PacketAdapter(plugin, PacketType.Play.Server.SET_SLOT) {
                override fun onPacketSending(event: PacketEvent) {
                    if(event.isPlayerTemporary) return
                    console("sending ${event.packetType} [")
                    val packet = event.packet
                    val windowId = packet.integers.read(0)
                    console("  windowId: $windowId")
                    val stateId = packet.integers.read(1)
                    console("  stateId: $stateId")
                    val slot = packet.integers.read(2)
                    console("  slot: $slot")
                    val item = packet.itemModifier.read(0)
                    console("  item: $item")
                    console("]")
                }
            })
        }

         */



        if(reload) {
            log("무기 불러오기 ...")
            loadWeapons()
        }
    }

    override fun onLateInit() {
        log("무기 불러오기 ...")
        loadWeapons()
    }

    fun loadWeapons() {
        WeaponPlayer.byUUID.values.forEach {
            val weapon = it.weapon ?: return
            weapon.storeTo(it.player.inventory.itemInMainHand)
        }

        WeaponType.load()

        WeaponPlayer.byUUID.values.forEach {
            val weapon = it.weapon ?: return
            weapon.type = WeaponType[weapon.type.key] ?: run {
                it.updateCurrentWeapon()
                return@forEach
            }
        }
    }

}