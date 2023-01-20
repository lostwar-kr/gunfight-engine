package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.*
import kr.lostwar.gun.weapon.actions.*
import kr.lostwar.gun.weapon.components.Zoom.Companion.isZooming
import kr.lostwar.gun.weapon.event.*
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
    val startAmount: Int = getInt("startAmount", amount)

    val canReload: Boolean = getBoolean("reload.enable", parent?.canReload, true)
    val reloadEmptyAmmoDelay: Int = getInt("reload.emptyAmmoDelay", parent?.reloadEmptyAmmoDelay, 5)
    val reloadDuration: Int = getInt("reload.duration", parent?.reloadDuration, 1)
    val reloadSound: SoundClip = getSoundClip("reload.sound", parent?.reloadSound)
    val reloadAnimation: AnimationClip = getAnimationClip("reload.animation", parent?.reloadAnimation)

    val reloadStartDuration: Int = getInt("reload.reloadStartDuration", parent?.reloadStartDuration, 0)
    val reloadEndDuration: Int = getInt("reload.reloadEndDuration", parent?.reloadEndDuration, 0)
    val reloadStartSound: SoundClip = getSoundClip("reload.reloadStartSound", parent?.reloadStartSound)
    val reloadEndSound: SoundClip = getSoundClip("reload.reloadEndSound", parent?.reloadEndSound)
    val reloadStartAnimation: AnimationClip = getAnimationClip("reload.reloadStartAnimation", parent?.reloadStartAnimation)
    val reloadEndAnimation: AnimationClip = getAnimationClip("reload.reloadEndAnimation", parent?.reloadEndAnimation)

    val tacticalReloadAnimation: AnimationClip = getAnimationClip("tacticalReload.animation", reloadAnimation.takeIf { it.isNotEmpty() } ?: parent?.tacticalReloadAnimation, reloadAnimation)
    val tacticalReloadStartAnimation: AnimationClip = getAnimationClip("tacticalReload.reloadStartAnimation", reloadStartAnimation.takeIf { it.isNotEmpty() } ?: parent?.tacticalReloadStartAnimation, reloadStartAnimation)
    val tacticalReloadEndAnimation: AnimationClip = getAnimationClip("tacticalReload.reloadEndAnimation", reloadEndAnimation.takeIf { it.isNotEmpty() } ?: parent?.tacticalReloadEndAnimation, reloadEndAnimation)

    val reloadIndividually: Boolean = getBoolean("reloadIndividually.enable", parent?.reloadIndividually, false)
    val reloadIndividuallyWhenTacticalReload: Boolean = getBoolean("reloadIndividually.useWhenTacticalReload", parent?.reloadIndividuallyWhenTacticalReload, false)
    val reloadIndividuallyFillAmmoImmediately: Boolean = getBoolean("reloadIndividually.fillAmmoImmediately", parent?.reloadIndividuallyFillAmmoImmediately, false)
    val reloadIndividuallyDuration: Int = getInt("reloadIndividually.duration", parent?.reloadIndividuallyDuration, reloadDuration)
    val reloadIndividuallySound: SoundClip = getSoundClip("reloadIndividually.sound", parent?.reloadIndividuallySound, reloadSound)
    val reloadIndividuallyAnimation: AnimationClip = getAnimationClip("reloadIndividually.animation", parent?.reloadIndividuallyAnimation, reloadAnimation)

    val reloadIndividuallyStartDuration: Int = getInt("reloadIndividually.reloadStartDuration", parent?.reloadIndividuallyStartDuration, reloadStartDuration)
    val reloadIndividuallyEndDuration: Int = getInt("reloadIndividually.reloadEndDuration", parent?.reloadIndividuallyEndDuration, reloadEndDuration)
    val reloadIndividuallyStartSound: SoundClip = getSoundClip("reloadIndividually.reloadStartSound", parent?.reloadIndividuallyStartSound, reloadStartSound)
    val reloadIndividuallyEndSound: SoundClip = getSoundClip("reloadIndividually.reloadEndSound", parent?.reloadIndividuallyEndSound, reloadEndSound)
    val reloadIndividuallyStartAnimation: AnimationClip = getAnimationClip("reloadIndividually.reloadStartAnimation", parent?.reloadIndividuallyStartAnimation, reloadStartAnimation)
    val reloadIndividuallyEndAnimation: AnimationClip = getAnimationClip("reloadIndividually.reloadEndAnimation", parent?.reloadIndividuallyEndAnimation, reloadEndAnimation)

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


    private fun WeaponPlayer.loadAction(eventType: LoadEventType, motionType: LoadMotionType? = null) {
        if(weapon?.primaryAction != null) return
        val generatedAction = loadActionGenerate(eventType, motionType) ?: return
        generatedAction.weapon.primaryAction = generatedAction
    }
    private fun WeaponPlayer.loadActionGenerate(
        eventType: LoadEventType,
        motionType: LoadMotionType? = null
    ): WeaponAction? {
        val weapon = weapon ?: return null

        val actions = boltLoadType[eventType]
        if (actions.isEmpty()) return null
        
        // 이미 탄창이 꽉 찬 상태로 재장전 시도하면 무시
        if(eventType.isReload && weapon.isAmmoFull) {
            return null
        }
        weapon.type.zoom?.let { zoom ->
            if(eventType.isReload && weapon.isZooming && !zoom.canReloadWhileZooming) {
                return with(zoom) { zoomAction(false) }
            }
        }

        return LoadAction(weapon, eventType, motionType)
    }

    // 무기 LoadAction 복구
    private val startHoldingListenerForRecoverLoadAction
    = WeaponPlayerEventListener(WeaponStartHoldingEvent::class.java, priority = EventPriority.NORMAL) { event ->
        val weapon = event.weapon

        // 마지막에 저장된 LoadAction이 있을 경우
        val lastEvent = weapon.currentLoadEvent
        val lastMotion = weapon.currentLoadMotion

        if(lastEvent != null) {
            loadAction(lastEvent, lastMotion)
        }
    }
    private val startHoldingListenerForReload
    = WeaponPlayerEventListener(WeaponStartHoldingEvent::class.java, priority = EventPriority.MONITOR) { event ->
        val weapon = event.weapon
        // 끝까지 아무런 action이 설정되지 않은 경우
        if(weapon.primaryAction != null) return@WeaponPlayerEventListener

        // 탄창이 다 비었을 때
        if(canReload && weapon.ammo <= 0) {
            loadAction(LoadEventType.EMPTY_RELOAD)
        }
    }

    private val weaponActionEndListener
    = WeaponPlayerEventListener(WeaponActionEndEvent::class.java) { event ->
//            GunEngine.log("Ammo::WeaponActionEndEvent")
        val weapon = event.weapon
        // 무기가 달라진 경우
        if(event.isWeaponChanged) {
//                GunEngine.log("- weapon changed, return")
            return@WeaponPlayerEventListener
        }
        val oldIsShootAction = event.oldAction is ShootAction
        // 이미 다음 액션이 결정된 경우 예약해두기
        if(event.newAction != null) {
            if(oldIsShootAction) {
                if(weapon.ammo > 0) {
                    weapon.currentLoadEvent = LoadEventType.SHOOT_END_NOT_EMPTY
                }else if(canReload) {
                    weapon.currentLoadEvent = LoadEventType.EMPTY_RELOAD
                }
            }
            return@WeaponPlayerEventListener
        }
        // ShootAction 끝났을 때 (매 발사 종료 시마다)
        if(oldIsShootAction) {
            // 탄창 남았으면 볼트, 펌프 등 실행
            if(weapon.ammo > 0) {
                event.newAction = loadActionGenerate(LoadEventType.SHOOT_END_NOT_EMPTY)
            }
            // 탄창 없으면 재장전
            else if(canReload) {
                event.newAction = loadActionGenerate(LoadEventType.EMPTY_RELOAD)
            }
        }
        // 아무튼 다음 action이 빈자리일 때
        else{
            // 대기 상태인 load가 있을 때
            val lastEvent = weapon.currentLoadEvent
            val lastMotion = weapon.currentLoadMotion

            if(lastEvent != null) {
                event.newAction = loadActionGenerate(lastEvent, lastMotion)
            }
            // 탄창이 없을 때
            else if(weapon.ammo <= 0) {
                event.newAction = loadActionGenerate(LoadEventType.EMPTY_RELOAD)
            }
        }
    }

    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        startHoldingListenerForRecoverLoadAction,
        startHoldingListenerForReload,
        weaponActionEndListener,
        WeaponPlayerEventListener(WeaponClickEvent::class.java, priority = EventPriority.MONITOR) { event ->
            val weapon = this.weapon ?: return@WeaponPlayerEventListener
            if(weapon.primaryAction != null) {
                recoverAnimation()
            }
        },
        WeaponPlayerEventListener(PlayerDropItemEvent::class.java) { event ->
            val weapon = this.weapon ?: return@WeaponPlayerEventListener
            event.isCancelled = true
            if(!canReload) {
                return@WeaponPlayerEventListener
            }

            val type = 
                if(boltUseTacticalReloadAction && weapon.ammo > 0) LoadEventType.TACTICAL_RELOAD 
                else LoadEventType.EMPTY_RELOAD
            // 누군가한테 인터셉트 당해버렸으면 (Zoom ...)
            val action = weapon.primaryAction
            if(action != null) {
//                GunEngine.log("reload intercepted(${weapon.primaryAction}), queueing reload ...")
                recoverAnimation() // Q키 눌러서 애니메이션이 초기화됨, 복구 필요
                weapon.currentLoadEvent = type
            }else{
                loadAction(
                    if(boltUseTacticalReloadAction && weapon.ammo > 0) LoadEventType.TACTICAL_RELOAD
                    else LoadEventType.EMPTY_RELOAD
                )
            }
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
        WeaponPlayerEventListener(WeaponShootPrepareEvent::class.java) { event ->
            val weapon = this.weapon ?: return@WeaponPlayerEventListener
            weapon.ammo -= 1
        },
    )

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
            set(value) { set(AMMO, value) }
        val Weapon.isAmmoFull: Boolean
            get() = type.ammo == null || ammo >= type.ammo!!.amount
        var Weapon.currentLoadMotion: LoadMotionType?
            get() = get(LOAD_MOTION)
            set(value) { set(LOAD_MOTION, value) }
        var Weapon.currentLoadEvent: LoadEventType?
            get() = get(LOAD_EVENT)
            set(value) { set(LOAD_EVENT, value) }

    }

}