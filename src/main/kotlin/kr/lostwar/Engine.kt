package kr.lostwar

import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.util.Config
import kr.lostwar.util.ui.text.StringUtil.colored
import kr.lostwar.util.ui.text.console
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

abstract class Engine(val name: String) {

    val directory by lazy { plugin.directory+name+"/" }
    protected val config: Config by lazy { Config(directory+"config.yml") }

    abstract val listeners: List<Listener>
    abstract val commands: List<Command>
    var isEnable: Boolean = true; private set

    fun load(reload: Boolean) {
        val oldEnable = isEnable
        isEnable = config.getBoolean("enable", true)
        if(reload && oldEnable == true && isEnable == false) {
            unload()
            return
        }else{
            onLoad()
            listeners.forEach { Bukkit.getPluginManager().registerEvents(it, plugin) }
            if(!reload) {
                Bukkit.getCommandMap().registerAll(name, commands)
            }
        }
    }
    fun unload() {
        listeners.forEach { HandlerList.unregisterAll(it) }
        onUnload()
    }
    protected abstract fun onLoad()
    protected open fun onUnload() {}

    fun log(message: String) {
        console("[${name}] ${message.colored()}")
    }
    fun logWarn(message: String) = log("&e$message")

}