package kr.lostwar.vehicle

import kr.lostwar.Engine
import kr.lostwar.gun.GunEngine
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.command.Command
import org.bukkit.event.Listener

object VehicleEngine : Engine("andoo") {
    override val listeners: List<Listener> = listOf(

    )
    override val commands: List<Command> = listOf(

    )
    override fun onLoad(reload: Boolean) {
        if(reload) {
            log("차량 불러오기 ...")
            loadVehicles()
        }
    }

    override fun onLateInit() {
        log("차량 불러오기 ...")
        loadVehicles()
    }

    fun loadVehicles() {
        VehicleInfo.load()
    }
}