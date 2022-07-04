package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.*
import kr.lostwar.gun.weapon.actions.*
import kr.lostwar.gun.weapon.event.WeaponActionEndEvent
import kr.lostwar.gun.weapon.event.WeaponShootEvent
import kr.lostwar.gun.weapon.event.WeaponStartHoldingEvent
import kr.lostwar.gun.weapon.event.WeaponTriggerEvent
import kr.lostwar.util.AnimationClip
import kr.lostwar.util.ExtraUtil
import kr.lostwar.util.SoundClip
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.persistence.PersistentDataType

class Ammo(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Ammo?,
) : WeaponComponent(config, weapon, parent) {

    val amount: Int = getInt("amount", parent?.amount, 0)
    val startAmount: Int = getInt("startAmount", parent?.startAmount, amount)

    val canReload: Boolean = getBoolean("reload.enable", parent?.canReload, true)
    val reloadEmptyAmmoDelay: Int = getInt("reload.emptyAmmoDelay", parent?.reloadEmptyAmmoDelay, 5)
    val reloadIndividually: Boolean = getBoolean("reload.reloadIndividually", parent?.reloadIndividually, false)
    val reloadIndividuallyWhenTacticalReload: Boolean = getBoolean("reload.reloadIndividuallyWhenTacticalReload", parent?.reloadIndividuallyWhenTacticalReload, false)
    val reloadIndividuallyFillAmmoImmediately: Boolean = getBoolean("reload.reloadIndividuallyFillAmmoImmediately", parent?.reloadIndividuallyFillAmmoImmediately, false)

    val reloadDuration: Int = getInt("reload.duration", parent?.reloadDuration, 1)
    val reloadSound: SoundClip = getSoundClip("reload.reloadSound", parent?.reloadSound)
    val reloadAnimation: AnimationClip = getAnimationClip("reload.reloadAnimation", parent?.reloadAnimation)

    val reloadStartDuration: Int = getInt("reload.reloadStartDuration", parent?.reloadStartDuration, 0)
    val reloadEndDuration: Int = getInt("reload.reloadEndDuration", parent?.reloadEndDuration, 0)
    val reloadStartSound: SoundClip = getSoundClip("reload.reloadStartSound", parent?.reloadStartSound)
    val reloadEndSound: SoundClip = getSoundClip("reload.reloadEndSound", parent?.reloadEndSound)
    val reloadStartAnimation: AnimationClip = getAnimationClip("reload.reloadStartAnimation", parent?.reloadStartAnimation)
    val reloadEndAnimation: AnimationClip = getAnimationClip("reload.reloadEndAnimation", parent?.reloadEndAnimation)

    // TODO 아이템 탄약 기능 추가?

    val boltLoadType: LoadType = getEnumString("bolt.loadType", parent?.boltLoadType, LoadType.NONE)
    // tacticalReload로 인한 reload 시 모션 구분 사용 여부
    val boltUseTacticalReloadAction: Boolean = getBoolean("bolt.useTacticalReloadAction", parent?.boltUseTacticalReloadAction, false)
    val boltOpenSound: SoundClip = getSoundClip("bolt.openSound", parent?.boltOpenSound)
    val boltCloseSound: SoundClip = getSoundClip("bolt.closeSound", parent?.boltCloseSound)
    val boltOpenAnimation: AnimationClip = getAnimationClip("bolt.openAnimation", parent?.boltOpenAnimation)
    val boltCloseAnimation: AnimationClip = getAnimationClip("bolt.closeAnimation", parent?.boltCloseAnimation)
    val boltOpenDuration: Int = getInt("bolt.openDuration", parent?.boltOpenDuration, 1)
    val boltCloseDuration: Int = getInt("bolt.closeDuration", parent?.boltCloseDuration, 1)

    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        // 무기 LoadAction 복구
        WeaponPlayerEventListener(WeaponStartHoldingEvent::class.java, priority = EventPriority.LOWEST) { event ->
            val weapon = event.weapon

            // 마지막에 저장된 LoadAction이 있을 경우
            val lastMotion = weapon.lastLoadMotion
            val lastEvent = weapon.lastLoadEvent

            if(lastMotion != null && lastEvent != null) {
                loadAction(lastEvent, lastMotion)
                weapon.lastLoadMotion = null
                weapon.lastLoadEvent = null
            }
        },
        WeaponPlayerEventListener(WeaponStartHoldingEvent::class.java, priority = EventPriority.MONITOR) { event ->
            val weapon = event.weapon
            // 끝까지 아무런 action이 설정되지 않은 경우
            if(weapon.primaryAction != null) return@WeaponPlayerEventListener

            // 탄창이 다 비었을 때
            if(weapon.ammo <= 0) {
                loadAction(LoadEventType.EMPTY_RELOAD)
            }
        },
        WeaponPlayerEventListener(WeaponActionEndEvent::class.java) { event ->
            val weapon = event.weapon
            // 무기가 달라진 경우
            if(event.isWeaponChanged) {
                return@WeaponPlayerEventListener
            }
            // ShootAction 끝났을 때 (매 발사 종료 시마다)
            if(event.oldAction is ShootAction) {
                // 탄창 남았으면 볼트, 펌프 등 실행
                if(weapon.ammo > 0) {
                    event.newAction = loadActionGenerate(LoadEventType.SHOOT_END_NOT_EMPTY)
                }
                // 탄창 없으면 재장전
                else {
                    event.newAction = loadActionGenerate(LoadEventType.EMPTY_RELOAD)
                }
            }
            // 아무튼 다음 action이 빈자리일 때
            else if(event.newAction == null){
                // 대기 상태인 load가 있을 때
                val lastMotion = weapon.lastLoadMotion
                val lastEvent = weapon.lastLoadEvent

                if(lastMotion != null && lastEvent != null) {
                    event.newAction = loadActionGenerate(lastEvent, lastMotion)
                    weapon.lastLoadMotion = null
                    weapon.lastLoadEvent = null
                }
                // 탄창이 없을 때
                else if(weapon.ammo <= 0) {
                    event.newAction = loadActionGenerate(LoadEventType.EMPTY_RELOAD)
                }
            }
        },
        WeaponPlayerEventListener(PlayerDropItemEvent::class.java) { event ->
            val weapon = this.weapon ?: return@WeaponPlayerEventListener
            event.isCancelled = true

            loadAction(
                if(boltUseTacticalReloadAction) LoadEventType.TACTICAL_RELOAD
                else LoadEventType.EMPTY_RELOAD
            )
        },
        WeaponPlayerEventListener(WeaponTriggerEvent::class.java) { event ->
            val weapon = event.weapon ?: return@WeaponPlayerEventListener
            val currentAction = weapon.primaryAction
            if(currentAction !is LoadAction) {
                return@WeaponPlayerEventListener
            }
            event.isCancelled = true

            // 한 발씩 장전 중에 발사 신호가 왔을 경우
            // 한 발씩 장전하던 모션을 스킵함
            if(currentAction.isReload && currentAction.isIndividuallyReload) {
                currentAction.skip(LoadMotionType.RELOAD)
            }
        },
        WeaponPlayerEventListener(WeaponShootEvent::class.java) { event ->
            val weapon = this.weapon ?: return@WeaponPlayerEventListener
            weapon.ammo -= 1
        },
    )

    private fun WeaponPlayer.loadAction(eventType: LoadEventType, motionType: LoadMotionType? = null)
        = loadActionGenerate(eventType, motionType)?.let { it.weapon.primaryAction = it }
    private fun WeaponPlayer.loadActionGenerate(eventType: LoadEventType, motionType: LoadMotionType? = null): LoadAction? {
        val weapon = weapon ?: return null
        if(weapon.primaryAction != null) return null

        val actions = boltLoadType[eventType]
        if(actions.isEmpty()) return null

        val loadAction = LoadAction(weapon, eventType, motionType)
        return loadAction
    }

    override fun onInstantiate(weapon: Weapon) {
        weapon.registerNotNull(AMMO, startAmount)
        weapon.registerNullable(LOAD_MOTION)
        weapon.registerNullable(LOAD_EVENT)
    }
    companion object {
        private val LOAD_MOTION_TYPE = ExtraUtil.EnumPersistentDataType(LoadMotionType::class.java)
        private val LOAD_EVENT_TYPE = ExtraUtil.EnumPersistentDataType(LoadEventType::class.java)
        private val AMMO = WeaponPropertyType("ammo", PersistentDataType.INTEGER)
        private val LOAD_MOTION = WeaponPropertyType("load.motion", LOAD_MOTION_TYPE)
        private val LOAD_EVENT = WeaponPropertyType("load.event", LOAD_EVENT_TYPE)

        var Weapon.ammo: Int
            get() = get(AMMO) ?: 0
            set(value) { set(AMMO, value)  }
        var Weapon.lastLoadMotion: LoadMotionType?
            get() = get(LOAD_MOTION)
            set(value) { set(LOAD_MOTION, value) }
        var Weapon.lastLoadEvent: LoadEventType?
            get() = get(LOAD_EVENT)
            set(value) { set(LOAD_EVENT, value) }

    }

}