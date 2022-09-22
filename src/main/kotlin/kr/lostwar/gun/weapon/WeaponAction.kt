package kr.lostwar.gun.weapon

import java.util.LinkedList

abstract class WeaponAction(
    val weapon: Weapon
) {
    val player = weapon.player ?: error("WeaponAction created on ${weapon} but cannot find WeaponPlayer")
    var isRunning: Boolean = false

    fun start(): Boolean {
        if(isRunning) return false
        isRunning = true
        onStart()
        return true
    }
    private val observersOnTick = LinkedList<() -> Unit>()
    private val observersOnEnd = LinkedList<() -> Unit>()
    fun subscribeOnTick(onTick: () -> Unit) = observersOnTick.add(onTick)
    fun subscribeOnEnd(onEnd: () -> Unit) = observersOnEnd.add(onEnd)
    fun end(): Boolean {
        if(!isRunning) return false
        isRunning = false
        onEnd()
        for(observer in observersOnEnd) observer()
        observersOnEnd.clear()
        observersOnTick.clear()
        return true
    }
    fun tick() {
        if(!isRunning) return
        onTick()
        for(observer in observersOnTick) observer()
    }
    protected open fun onStart() {}
    protected abstract fun onTick()
    protected open fun onEnd() {}

    val name = javaClass.simpleName
    override fun toString(): String {
        return name
    }

}