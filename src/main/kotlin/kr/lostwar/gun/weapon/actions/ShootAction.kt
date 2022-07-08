package kr.lostwar.gun.weapon.actions

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponAction
import kr.lostwar.gun.weapon.components.Ammo.Companion.ammo
import kr.lostwar.gun.weapon.components.SelectorLever
import kr.lostwar.gun.weapon.components.SelectorLever.Companion.selector
import kr.lostwar.gun.weapon.components.Shoot.Companion.leftShootDelay
import kr.lostwar.gun.weapon.event.WeaponAnimationDetermineEvent

class ShootAction(
    weapon: Weapon,
) : WeaponAction(weapon) {
    val shoot = weapon.type.shoot ?: error("ShootAction created but weapon ${weapon} doesn't have Shoot component")
    val burst = weapon.type.burst
    val fullAuto = weapon.type.fullAuto
    enum class State {
        /**
         * 단발 발사 후 shoot.delay 기다리는 중
         */
        SINGLE_WAIT_DELAY,
        /**
         * 점사 간 딜레이 (burst.shootDelay) 기다리는 중
         */
        BURST_PER_SHOT,
        /**
         * 점사 후 burst.triggerDelay 기다리는 중
         */
        BURST_WAIT_DELAY,
        /**
         * 연사 간 딜레이 (fullAuto.delay) 기다리는 중
         */
        FULL_AUTO_PER_SHOT,

        /**
         * 탄창 비어서 대기 중
         */
        EMPTY_AMMO_WAIT,
    }
    lateinit var state: State; private set
    var shootCount = 0
    var delay = 0
    var leftBurst: Int = 0
    var completed = false
    override fun onStart() {
        weapon.state = Weapon.WeaponState.SHOOTING
        firstShoot()
    }
    private fun firstShoot() {
        when(weapon.selector) {
            SelectorLever.SelectorType.SINGLE -> {
                state = State.SINGLE_WAIT_DELAY
                delay = shoot.shootDelay
                WeaponAnimationDetermineEvent.Type.SINGLE_SHOOT
                    .create(player, shoot.animation)
                    .callEventAndGetClip()
                    .play(player, weapon.type)
            }
            SelectorLever.SelectorType.BURST -> {
                leftBurst = burst!!.amount
                state = State.BURST_PER_SHOT
                delay = burst.shootDelay
            }
            SelectorLever.SelectorType.FULL_AUTO -> {
                state = State.FULL_AUTO_PER_SHOT
                delay = fullAuto!!.delay
                // 상황에 따라 애니메이션 바뀌도록
                WeaponAnimationDetermineEvent.Type.FULL_AUTO_SHOOT_LOOP
                    .create(player, fullAuto!!.shootAnimationLoop)
                    .callEventAndGetClip()
                    .play(player, weapon.type, loop = true)
            }
            SelectorLever.SelectorType.SAFE -> {
                complete()
                return
            }
        }
        singleShoot()
        trigger()
    }
    private fun singleShoot() {
        with(shoot) { weapon.player?.shoot(this@ShootAction) }
        ++shootCount
    }
    override fun onTick() {
        // 무기가 달라진 경우
        if(player.weapon != weapon) {
            end()
            return
        }
        weapon.type.ammo?.let { ammoComponent ->
            if(weapon.ammo <= 0) {
                if(state != State.EMPTY_AMMO_WAIT) {
                    if(state == State.FULL_AUTO_PER_SHOT) {
                        WeaponAnimationDetermineEvent.Type.FULL_AUTO_SHOOT_STOP
                            .create(player, fullAuto!!.shootAnimationLoopStop)
                            .callEventAndGetClip()
                            .play(player, weapon.type)
                    }
                    state = State.EMPTY_AMMO_WAIT
                    delay = ammoComponent.reloadEmptyAmmoDelay
                }else{
                    if(delay > 0){
                        --delay
                    }else{
                        end()
                    }
                }
                return
            }
        }

        if(clickingTicks > 0) {
            --clickingTicks
            if(clickingTicks <= 0) {
                clicking = false
            }
        }

        --delay
        if(delay > 0) return
        when(state) {
            State.SINGLE_WAIT_DELAY -> {
                // 딱 끝나는 이번 틱에 누른 경우
                // 객체 반복 생성 방지를 위해 연결해줌
                if(clickingTicks == shoot.clickTicks) {
                    singleShoot()
                    delay = shoot.shootDelay
                }else{
                    complete()
                }
                return
            }
            State.BURST_PER_SHOT -> {
                --leftBurst
                if(leftBurst <= 0){
                    delay = burst!!.triggerDelay
                    state = State.BURST_WAIT_DELAY
                }else{
                    delay = burst!!.shootDelay
                }
            }
            State.BURST_WAIT_DELAY -> {
                if(!clicking) {
                    clicking = false
                    complete()
                    return
                }
                state = State.BURST_PER_SHOT
                delay = burst!!.shootDelay
                leftBurst = burst.amount - 1 // 반복이라 이번 틱에 이미 한 번 쏘므로 -1
            }
            State.FULL_AUTO_PER_SHOT -> {
                if(!clicking) {
                    clicking = false
                    complete()
                    return
                }
                delay = fullAuto!!.delay
            }
        }
        singleShoot()
    }

    private fun complete() {
        completed = true
        end()
    }

    override fun onEnd() {
        weapon.state = Weapon.WeaponState.NOTHING
        clicking = false
        if(state == State.FULL_AUTO_PER_SHOT) {
            WeaponAnimationDetermineEvent.Type.FULL_AUTO_SHOOT_STOP
                .create(player, fullAuto!!.shootAnimationLoopStop)
                .callEventAndGetClip()
                .play(player, weapon.type)
        }

        // 뭔가 하다 만 채로 끝난 경우
        if(!completed) {
            weapon.leftShootDelay = when(state) {
                State.SINGLE_WAIT_DELAY, State.FULL_AUTO_PER_SHOT, State.BURST_WAIT_DELAY -> delay
                State.BURST_PER_SHOT -> burst!!.triggerDelay // 괘씸죄
                else -> 0
            }
        }else{
            weapon.leftShootDelay = 0
        }
    }

    var clickingTicks = 0
    var clicking: Boolean = false
    fun trigger() {
        clickingTicks = shoot.clickTicks
        clicking = true
    }
    fun untrigger() {
        clickingTicks = 0
        clicking = false
    }
}