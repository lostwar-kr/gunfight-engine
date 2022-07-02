package kr.lostwar.gun.weapon

abstract class WeaponAction(
    val weapon: Weapon
) {

    var isRunning: Boolean = false

    fun start() {
        isRunning = true
        onStart()
    }
    fun end() {
        if(!isRunning) return
        isRunning = false
        onEnd()
    }
    fun tick() {
        onTick()
    }
    protected open fun onStart() {}
    protected abstract fun onTick()
    protected open fun onEnd() {}
}