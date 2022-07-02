package kr.lostwar

import kr.lostwar.gun.GunEngine
import kr.lostwar.util.ui.text.StringUtil.colored
import kr.lostwar.util.ui.text.console
import kr.lostwar.vehicle.VehicleEngine
import org.bukkit.plugin.java.JavaPlugin

class GunfightEngine : JavaPlugin() {

    companion object {
        lateinit var plugin: GunfightEngine
    }

    val directory = "plugins/${name}/"

    private val engines: List<Engine> = listOf(
        GunEngine,
        VehicleEngine,
    )

    override fun onEnable() {
        plugin = this
        for(engine in engines) {
            log("${engine.name} &e활성화 중 ...")
            engine.load(reload = false)
            if(engine.isEnable){
                log("${engine.name} &a활성화 완료!")
            }else{
                log("${engine.name} &8비활성화됨")
            }
        }
    }

    override fun onDisable() {
        for(engine in engines) {
            log("${engine.name} &e비활성화 중 ...")
            engine.unload()
            log("${engine.name} &e비활성화 완료")
        }
    }
    fun log(message: String) {
        console("[${name}] ${message.colored()}")
    }
    fun logWarn(message: String) = log("&e$message")
}