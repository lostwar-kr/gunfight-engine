package kr.lostwar.gun.weapon.actions

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponAction
import kr.lostwar.gun.weapon.components.Ammo.Companion.ammo
import kr.lostwar.gun.weapon.components.Ammo.Companion.currentLoadEvent
import kr.lostwar.gun.weapon.components.Ammo.Companion.currentLoadMotion
import kr.lostwar.gun.weapon.event.WeaponAnimationDetermineEvent.Type
import kr.lostwar.util.AnimationClip
import kr.lostwar.util.ExtraUtil.joinToString
import kr.lostwar.util.SoundClip

class LoadAction(
    weapon: Weapon,
    val eventType: LoadEventType,
    val startMotion: LoadMotionType? = null,
) : WeaponAction(weapon) {

    val ammo = weapon.type.ammo ?: error("LoadAction created but weapon ${weapon} doesn't have Ammo component")
    val loadType = ammo.boltLoadType
    val motions = loadType[eventType]
    var currentMotionIndex = -1
    var currentMotion: LoadMotionType
        get() = weapon.currentLoadMotion!!
        set(value) { weapon.currentLoadMotion = value }

    val isReload = eventType.isReload
    val isTacticalReload = weapon.ammo > 0
    val isIndividuallyReload = ammo.reloadIndividually && (!ammo.reloadIndividuallyWhenTacticalReload || isTacticalReload)

    var completed = false
    var lapsedTime = 0; private set
    var delay = 0

    override fun onStart() {
        weapon.currentLoadEvent = eventType
        // 중간부터 시작하는 경우
        if(startMotion != null) {
            currentMotion = startMotion
            currentMotionIndex = motions.indexOf(startMotion)
            delay = 0
            with(currentMotion) { execute() }
        }
        // 중간부터 시작하는 게 아닐 경우
        else {
            nextMotion(true)
        }
    }

    private fun nextMotion(force: Boolean = false) {

        if(!force && with(currentMotion) { repeat() }) {
            delay = 0
            with(currentMotion) { execute() }
            return
        }
        if (currentMotionIndex + 1 >= motions.size) {
            completed = true
            end()
            return
        }
        ++currentMotionIndex
        currentMotion = motions[currentMotionIndex]
        delay = 0
        with(currentMotion) { execute() }
    }


    private val skippingMotions = HashSet<LoadMotionType>()
    fun skip(vararg motions: LoadMotionType) {
        skippingMotions.addAll(motions)
    }

    override fun onTick() {
        ++lapsedTime
        if(currentMotion in skippingMotions) {
            nextMotion(true)
            skippingMotions.remove(currentMotion)
            return
        }
        if(delay > 0){
            --delay
            return
        }
        nextMotion()
    }

    override fun onEnd() {
        weapon.state = Weapon.WeaponState.NOTHING
        if(completed) {
            if(isReload) {
                weapon.ammo = ammo.amount
            }
            weapon.currentLoadMotion = null
            weapon.currentLoadEvent = null
        }
    }

    override fun toString(): String {
        return super.toString()+"(" +
                "event=${eventType}," +
                "motion=${if(isRunning) currentMotion else null}," +
                "delay=${delay}," +
                "lapsed=${lapsedTime}," +
                "motions=${motions.joinToString()}" +
        ")"
    }
}


/**
<DISABLED>
shoot: shoot
reload: reload
reload_empty: reload

<SLIDE>
shoot: shoot
reload: reload
reload_empty: open, reload, close

<BOLT>
shoot: shoot, open, close
reload: [open, ]reload[, close]
reload_empty: open, reload, close

<PUMP>
shoot: shoot, open, close,
reload: reload
reload_empty: reload, open, close

 */

enum class LoadMotionType(
    val sound: LoadAction.() -> SoundClip,
    val animationType: LoadAction.() -> Type,
    val animation: LoadAction.() -> AnimationClip,
    val duration: LoadAction.() -> Int,
) {
    OPEN(
        { ammo.boltOpenSound },
        { Type.BOLT_OPEN },
        { ammo.boltOpenAnimation },
        { ammo.boltOpenDuration }
    ),
    CLOSE(
        { ammo.boltCloseSound },
        { Type.BOLT_CLOSE },
        { ammo.boltCloseAnimation },
        { ammo.boltCloseDuration }
    ),
    RELOAD_START(
        { if(isIndividuallyReload) ammo.reloadIndividuallyStartSound else ammo.reloadStartSound },
        { if(isIndividuallyReload) Type.RELOAD_INDIVIDUALLY_START else if(isTacticalReload) Type.TACTICAL_RELOAD_START else Type.RELOAD_START },
        {
            if(isIndividuallyReload) ammo.reloadIndividuallyStartAnimation
            else if (isTacticalReload) ammo.tacticalReloadStartAnimation
            else ammo.reloadStartAnimation
        },
        { if(isIndividuallyReload) ammo.reloadIndividuallyStartDuration else ammo.reloadStartDuration }
    ),
    RELOAD(
        { if(isIndividuallyReload) ammo.reloadIndividuallySound else ammo.reloadSound },
        { if(isIndividuallyReload) Type.RELOAD_INDIVIDUALLY else if(isTacticalReload) Type.TACTICAL_RELOAD else Type.RELOAD },
        {
            if(isIndividuallyReload) ammo.reloadIndividuallyAnimation
            else if (isTacticalReload) ammo.tacticalReloadAnimation
            else ammo.reloadAnimation
        },
        { if(isIndividuallyReload) ammo.reloadIndividuallyDuration else ammo.reloadDuration }
    ) {
        override fun LoadAction.execute() {
            executeDefault(this)
            if(!isIndividuallyReload) {
                return
            }
            // (거의 그럴리가 없지만) 사운드/애니메이션과 동시에 채움
            if(ammo.reloadIndividuallyFillAmmoImmediately) {
                weapon.ammo += 1
            }
        }

        override fun LoadAction.repeat(): Boolean {
            if(!isIndividuallyReload) {
                return false
            }
            // 즉시 채우는 게 아니면 반복 결정 시점에 채움
            if(!ammo.reloadIndividuallyFillAmmoImmediately) {
                weapon.ammo += 1
            }
            if(weapon.ammo >= ammo.amount) {
                weapon.ammo = ammo.amount
                return false
            }
            return true
        }
    },
    RELOAD_END(
        { if(isIndividuallyReload) ammo.reloadIndividuallyEndSound else ammo.reloadEndSound },
        { if(isIndividuallyReload) Type.RELOAD_INDIVIDUALLY_END else if(isTacticalReload) Type.TACTICAL_RELOAD_END else Type.RELOAD_END },
        {
            if(isIndividuallyReload) ammo.reloadIndividuallyEndAnimation
            else if (isTacticalReload) ammo.tacticalReloadEndAnimation
            else ammo.reloadEndAnimation
        },
        { if(isIndividuallyReload) ammo.reloadIndividuallyEndDuration else ammo.reloadEndDuration }
    ),
    ;

    open fun LoadAction.execute() {
        executeDefault(this)
    }
    protected fun executeDefault(action: LoadAction) = with(action) {
        val duration = duration()
        val offset =
            if(delay == 0) 0
            else duration - delay
        player.playSound(sound().playAt(player.player, offset))
        animationType().create(player, animation())
            .callEventAndGetClip()
            .play(player, weapon.type, offset)
        delay = duration
    }

    open fun LoadAction.repeat(): Boolean = false
}

enum class LoadEventType(val isReload: Boolean) {
    // 발사 종료 시
    SHOOT_END_NOT_EMPTY(false),

    // tacticalReload 활성화 시 탄창이 남은 상태에서 재장전 시
    TACTICAL_RELOAD(true),

    // 탄창이 빈 상태에서 재장전 시
    // 또는 tacticalReload 비활성화 시 그냥 재장전 시
    EMPTY_RELOAD(true),
}


enum class LoadType(
    eventMap: Map<LoadEventType, List<LoadMotionType>>,
) {
    NONE(
        mapOf(
            LoadEventType.SHOOT_END_NOT_EMPTY to emptyList(),
            LoadEventType.TACTICAL_RELOAD to listOf(
                LoadMotionType.RELOAD_START,
                LoadMotionType.RELOAD,
                LoadMotionType.RELOAD_END
            ),
            LoadEventType.EMPTY_RELOAD to listOf(
                LoadMotionType.RELOAD_START,
                LoadMotionType.RELOAD,
                LoadMotionType.RELOAD_END
            ),
        )
    ),
    SLIDE(
        mapOf(
            LoadEventType.SHOOT_END_NOT_EMPTY to emptyList(),
            LoadEventType.TACTICAL_RELOAD to listOf(
                LoadMotionType.RELOAD_START,
                LoadMotionType.RELOAD,
                LoadMotionType.RELOAD_END
            ),
            LoadEventType.EMPTY_RELOAD to listOf(
                LoadMotionType.OPEN,
                LoadMotionType.RELOAD_START,
                LoadMotionType.RELOAD,
                LoadMotionType.RELOAD_END,
                LoadMotionType.CLOSE
            ),
        )
    ),
    BOLT(
        mapOf(
            LoadEventType.SHOOT_END_NOT_EMPTY to listOf(LoadMotionType.OPEN, LoadMotionType.CLOSE),
            LoadEventType.TACTICAL_RELOAD to listOf(
                LoadMotionType.RELOAD_START,
                LoadMotionType.RELOAD,
                LoadMotionType.RELOAD_END
            ),
            LoadEventType.EMPTY_RELOAD to listOf(
                LoadMotionType.OPEN,
                LoadMotionType.RELOAD_START,
                LoadMotionType.RELOAD,
                LoadMotionType.RELOAD_END,
                LoadMotionType.CLOSE
            ),
        )
    ),
    PUMP(
        mapOf(
            LoadEventType.SHOOT_END_NOT_EMPTY to listOf(LoadMotionType.OPEN, LoadMotionType.CLOSE),
            LoadEventType.TACTICAL_RELOAD to listOf(
                LoadMotionType.RELOAD_START,
                LoadMotionType.RELOAD,
                LoadMotionType.RELOAD_END
            ),
            LoadEventType.EMPTY_RELOAD to listOf(
                LoadMotionType.RELOAD_START,
                LoadMotionType.RELOAD,
                LoadMotionType.RELOAD_END,
                LoadMotionType.OPEN,
                LoadMotionType.CLOSE
            ),
        )
    ),
    ;

    private val eventMap = LoadEventType.values().associateWith { eventMap[it] ?: emptyList() }
    operator fun get(eventType: LoadEventType): List<LoadMotionType> = eventMap[eventType] ?: emptyList()
}