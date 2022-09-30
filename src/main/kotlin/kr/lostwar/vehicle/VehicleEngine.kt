package kr.lostwar.vehicle

import com.comphenix.protocol.ProtocolLibrary
import kr.lostwar.Engine
import kr.lostwar.gun.GunEngine
import kr.lostwar.vehicle.core.VehicleEntity
import kr.lostwar.vehicle.core.VehicleInfo
import kr.lostwar.vehicle.core.parachute.ParachuteInfo
import org.bukkit.command.Command
import org.bukkit.event.Listener

object VehicleEngine : Engine("andoo") {

    var isDebugging = false

    override val listeners: List<Listener> = listOf(
        VehicleEntity.Companion,
        ParachuteInfo.Companion,
    )
    override val commands: List<Command> = listOf(
        VehicleCommand,
    )
    override fun onLoad(reload: Boolean) {
        if(reload) {
            log("차량 불러오기 ...")
            loadVehicles()
        }else{
            ProtocolLibrary.getProtocolManager().addPacketListener(SteerListener)
        }
    }

    override fun onLateInit() {
        log("차량 불러오기 ...")
        loadVehicles()
    }

    override fun onUnload() {
        VehicleEntity.byUUID.map { it.value }.forEach { it.death(explosion = false) }
    }

    fun loadVehicles() {
        VehicleInfo.load()
    }


}