package kr.lostwar.gun.weapon

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
    fun end(): Boolean {
        if(!isRunning) return false
        isRunning = false
        onEnd()
        return true
    }
    fun tick() {
        onTick()
    }
    protected open fun onStart() {}
    protected abstract fun onTick()
    protected open fun onEnd() {}
}