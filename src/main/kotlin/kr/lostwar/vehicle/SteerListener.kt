package kr.lostwar.vehicle

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import kr.lostwar.GunfightEngine
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer

object SteerListener : PacketAdapter(GunfightEngine.plugin, PacketType.Play.Client.STEER_VEHICLE) {

    override fun onPacketReceiving(event: PacketEvent) {
        if(event.isPlayerTemporary) return
        val container = event.packet
        val player = event.player
        val vehiclePlayer = player.vehiclePlayer

        val side = container.float.read(0)
        val forward = container.float.read(1)
        val space = container.booleans.read(0)
        val shift = container.booleans.read(1)
        vehiclePlayer.updateInput(side, forward, space, shift)
    }

}