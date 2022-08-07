package kr.lostwar

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class EngineReloadEvent(
    val engine: Engine,
): Event() {
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}