package kr.lostwar.gun.weapon.actions

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponAction
import kr.lostwar.gun.weapon.components.Ammo
import kr.lostwar.gun.weapon.components.Ammo.Companion.ammo
import kr.lostwar.gun.weapon.components.Ammo.Companion.lastLoadEvent
import kr.lostwar.gun.weapon.components.Ammo.Companion.lastLoadMotion
import kr.lostwar.gun.weapon.event.WeaponAnimationDetermineEvent
import kr.lostwar.util.AnimationClip
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
    lateinit var currentMotion: LoadMotionType

    val isReload = eventType == LoadEventType.TACTICAL_RELOAD || eventType == LoadEventType.EMPTY_RELOAD
    val isTacticalReload = weapon.ammo > 0
    val isIndividuallyReload = ammo.reloadIndividually && (!ammo.reloadIndividuallyWhenTacticalReload || isTacticalReload)

    var completed = false
    var lapsedTime = 0; private set
    var delay = 0

    override fun onStart() {
        // 중간부터 시작하는 경우
        if(startMotion != null) {
            currentMotion = startMotion
            currentMotionIndex = motions.indexOf(startMotion)
            delay = 0
            with(currentMotion) { execute() }
        }
        // 중간부터 시작하는 게 아닐 경우
        else {
            nextMotion()
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
            weapon.lastLoadMotion = null
            weapon.lastLoadEvent = null
        }
        // 중단된 경우 마지막 상태 저장
        else {
            weapon.lastLoadMotion = currentMotion
            weapon.lastLoadEvent = eventType
        }
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
    val sound: Ammo.() -> SoundClip,
    val animationType: WeaponAnimationDetermineEvent.Type,
    val animation: Ammo.() -> AnimationClip,
    val duration: Ammo.() -> Int,
) {
    OPEN(
        { boltOpenSound },
        WeaponAnimationDetermineEvent.Type.BOLT_OPEN, { boltOpenAnimation },
        { boltOpenDuration }
    ),
    CLOSE(
        { boltCloseSound },
        WeaponAnimationDetermineEvent.Type.BOLT_CLOSE, { boltCloseAnimation },
        { boltCloseDuration }
    ),
    RELOAD_START(
        { reloadStartSound },
        WeaponAnimationDetermineEvent.Type.RELOAD_START, { reloadStartAnimation },
        { reloadStartDuration }
    ),
    RELOAD(
        { reloadSound },
        WeaponAnimationDetermineEvent.Type.RELOAD, { reloadAnimation },
        { reloadDuration }
    ) {
        override fun LoadAction.execute() {
            if(!isIndividuallyReload) {
                executeDefault(this)
                return
            }
            player.playSound(ammo.sound().playAt(player.player))
            animationType.create(player, ammo.animation())
                .callEventAndGetClip()
                .play(player, weapon.type)
            delay = ammo.reloadDuration
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
        { reloadEndSound },
        WeaponAnimationDetermineEvent.Type.RELOAD_END, { reloadEndAnimation },
        { reloadEndDuration }
    ),
    ;

    open fun LoadAction.execute() {
        executeDefault(this)
    }
    protected fun executeDefault(action: LoadAction) {
        val duration = action.ammo.duration()
        val offset =
            if(action.delay == 0) 0
            else duration - action.delay
        action.player.playSound(action.ammo.sound().playAt(action.player.player, offset))
        animationType.create(action.player, action.ammo.animation())
            .callEventAndGetClip()
            .play(action.player, action.weapon.type, offset)
        action.delay = duration
    }

    open fun LoadAction.repeat(): Boolean = false
}

enum class LoadEventType {
    // 발사 종료 시
    SHOOT_END_NOT_EMPTY,

    // tacticalReload 활성화 시 탄창이 남은 상태에서 재장전 시
    TACTICAL_RELOAD,

    // 탄창이 빈 상태에서 재장전 시
    // 또는 tacticalReload 비활성화 시 그냥 재장전 시
    EMPTY_RELOAD,
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