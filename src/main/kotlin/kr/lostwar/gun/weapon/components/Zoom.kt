package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.*
import kr.lostwar.gun.weapon.actions.*
import kr.lostwar.gun.weapon.components.Ammo.Companion.ammo
import kr.lostwar.gun.weapon.event.*
import kr.lostwar.util.AnimationClip
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.potion.PotionEffectType

class Zoom(
    config: ConfigurationSection?,
    weaponType: WeaponType,
    parent: Zoom?,
) : WeaponComponent(config, weaponType, parent) {

    val amount: Int = getInt("amount", parent?.amount, 5)
    val applyNightVision: Boolean = getBoolean("nightVision", parent?.applyNightVision, false)
    val zoomAnimation: AnimationClip = getAnimationClip("animation.zoom", parent?.zoomAnimation)
    val unzoomAnimation: AnimationClip = getAnimationClip("animation.unzoom", parent?.unzoomAnimation)
    val zoomDuration: Int = getInt("zoomDuration", parent?.zoomDuration, zoomAnimation.mostDelayed)
    val unzoomDuration: Int = getInt("unzoomDuration", parent?.unzoomDuration, unzoomAnimation.mostDelayed)
    val applyZoomEffectImmediately: Boolean = getBoolean("applyZoomEffectImmediately", parent?.applyZoomEffectImmediately, false)
    val applyUnzoomEffectLazy: Boolean = getBoolean("applyUnzoomEffectLazy", parent?.applyUnzoomEffectLazy, false)

    val spread: Double = getDouble("spread", parent?.spread)
    val verticalRecoilModifier: Double = getDouble("recoil.vertical", parent?.verticalRecoilModifier, 0.0)
    val horizontalRecoilModifier: Double = getDouble("recoil.horizontal", parent?.horizontalRecoilModifier, 0.0)

    val singleShootAnimation: AnimationClip = getAnimationClip("animation.shoot", parent?.singleShootAnimation)
    val fullAutoShootLoopAnimation: AnimationClip = getAnimationClip("animation.fullAutoShootLoop", parent?.fullAutoShootLoopAnimation)
    val fullAutoShootStopAnimation: AnimationClip = getAnimationClip("animation.fullAutoShootStop", parent?.fullAutoShootStopAnimation)

    val canBoltWhileZooming: Boolean = getBoolean("canBoltWhileZooming", parent?.canBoltWhileZooming, false)
    val boltOpenAnimation: AnimationClip = getAnimationClip("animation.boltOpen", parent?.boltOpenAnimation)
    val boltCloseAnimation: AnimationClip = getAnimationClip("animation.boltClose", parent?.boltCloseAnimation)

    val canReloadWhileZooming: Boolean = getBoolean("canReloadWhileZooming", parent?.canReloadWhileZooming, false)
    val reloadStartAnimation: AnimationClip = getAnimationClip("animation.reloadStart", parent?.reloadStartAnimation)
    val reloadAnimation: AnimationClip = getAnimationClip("animation.reload", parent?.reloadAnimation)
    val reloadEndAnimation: AnimationClip = getAnimationClip("animation.reloadEnd", parent?.reloadEndAnimation)
    val tacticalReloadStartAnimation: AnimationClip = getAnimationClip("animation.tacticalReloadStart", parent?.tacticalReloadStartAnimation, reloadStartAnimation)
    val tacticalReloadAnimation: AnimationClip = getAnimationClip("animation.tacticalReload", parent?.tacticalReloadAnimation, reloadAnimation)
    val tacticalReloadEndAnimation: AnimationClip = getAnimationClip("animation.tacticalReloadEnd", parent?.tacticalReloadEndAnimation, reloadEndAnimation)
    val reloadIndividuallyStartAnimation: AnimationClip = getAnimationClip("animation.reloadIndividuallyStart", parent?.reloadIndividuallyStartAnimation)
    val reloadIndividuallyAnimation: AnimationClip = getAnimationClip("animation.reloadIndividually", parent?.reloadIndividuallyAnimation)
    val reloadIndividuallyEndAnimation: AnimationClip = getAnimationClip("animation.reloadIndividuallyEnd", parent?.reloadIndividuallyEndAnimation)

    fun WeaponPlayer.zoom(zooming: Boolean) {
        val weapon = weapon ?: return
        if(weapon.primaryAction != null) {
            // 만약 ShootAction인 경우, 발사 중단하고 조준 먼저
            if(weapon.primaryAction !is ShootAction) {
                return
            }
        }

        val zoomAction = zoomAction(zooming) ?: return
        weapon.primaryAction = zoomAction
    }

    fun WeaponPlayer.zoomAction(zooming: Boolean): ZoomAction? {
        val weapon = weapon ?: return null
        return ZoomAction(weapon, zooming)
    }

    fun WeaponPlayer.zoomEffect(zooming: Boolean) {
        val player = player
        if(zooming) {
            player.removePotionEffect(slow.type)
            slow.apply(player)
            if(applyNightVision) {
                nightVision.apply(player)
            }
        }else{
            player.removePotionEffect(slow.type)
            if(applyNightVision)
                player.removePotionEffect(nightVision.type)
        }
    }

    private fun PotionEffectType.create(amplifier: Int)
        = createEffect(Int.MAX_VALUE, amplifier)
            .withIcon(false)
            .withParticles(false)
    private val slow = PotionEffectType.SLOW.create(amount)
    private val nightVision = PotionEffectType.NIGHT_VISION.create(1)

    private val clickListener = WeaponPlayerEventListener(WeaponClickEvent::class.java) { event ->
        val weapon = event.weapon ?: return@WeaponPlayerEventListener
        if(event.clickType != ClickType.LEFT) return@WeaponPlayerEventListener

        zoom(!weapon.isZooming)
    }

    private val triggerListener = WeaponPlayerEventListener(WeaponTriggerEvent::class.java) { event ->
        val weapon = event.weapon ?: return@WeaponPlayerEventListener
        val currentAction = weapon.primaryAction
        if(currentAction !is ZoomAction) {
            return@WeaponPlayerEventListener
        }
        // 조준 중에는 발사 캔슬
        event.isCancelled = true
    }

    private val actionEndListener = WeaponPlayerEventListener(WeaponActionEndEvent::class.java, EventPriority.LOW) { event ->
        if(event.isWeaponChanged) {
            return@WeaponPlayerEventListener
        }
        val weapon = event.weapon


        if(event.newAction != null) {
            return@WeaponPlayerEventListener
        }

        // 조준 중에 action이 끝났는데 ...
        if(weapon.isZooming) {
            weapon.type.ammo?.let { ammo ->
                // 조준 중 볼트 비활성화, ShootAction이 끝났고, 발사 후 무언가 동작을 하는 경우 ...
                if(!canBoltWhileZooming
                    && event.oldAction is ShootAction
                    && ammo.boltLoadType[LoadEventType.SHOOT_END_NOT_EMPTY].isNotEmpty()
                ) {
                    event.newAction = zoomAction(false)
                }
                // 조준 중 장전 비활성화, ShootAction이 끝난 경우
                if(!canReloadWhileZooming && event.oldAction is ShootAction) {
                    // 탄창이 바닥나서 끝난 경우 조준을 강제로 해제함
                    if(weapon.ammo <= 0) {
                        event.newAction = zoomAction(false)
                    }
                }
            }
        }
    }

    // 재장전 인터셉트
    private val dropItemListener = WeaponPlayerEventListener(PlayerDropItemEvent::class.java, priority = EventPriority.LOW) { event ->
        val weapon = this.weapon ?: return@WeaponPlayerEventListener
        val ammo = weaponType.ammo ?: return@WeaponPlayerEventListener
        if(weapon.primaryAction != null) return@WeaponPlayerEventListener
        event.isCancelled = true

        if(weapon.isZooming && !canReloadWhileZooming) {
            weapon.primaryAction = zoomAction(false)
        }
    }

    // 무기를 더 이상 들지 않게 되면 강제 줌 효과 해제
    private val endHoldingListener = WeaponPlayerEventListener(WeaponEndHoldingEvent::class.java, -1) { event ->
        val weapon = event.weapon
        if(weapon.isZooming) {
            zoomEffect(false)
            weapon.isZooming = false
        }
    }

    private val animationListener = WeaponPlayerEventListener(WeaponAnimationDetermineEvent::class.java) { event ->
        val weapon = event.weapon
        // 조준 중에만 ...
        if(!weapon.isZooming) return@WeaponPlayerEventListener
        event.animationClip = when(event.type) {
            WeaponAnimationDetermineEvent.Type.SINGLE_SHOOT -> singleShootAnimation
            WeaponAnimationDetermineEvent.Type.FULL_AUTO_SHOOT_LOOP -> fullAutoShootLoopAnimation
            WeaponAnimationDetermineEvent.Type.FULL_AUTO_SHOOT_STOP -> fullAutoShootStopAnimation
            WeaponAnimationDetermineEvent.Type.BOLT_OPEN -> boltOpenAnimation
            WeaponAnimationDetermineEvent.Type.BOLT_CLOSE -> boltCloseAnimation
            WeaponAnimationDetermineEvent.Type.RELOAD_START -> reloadStartAnimation
            WeaponAnimationDetermineEvent.Type.RELOAD -> reloadAnimation
            WeaponAnimationDetermineEvent.Type.RELOAD_END -> reloadEndAnimation
            WeaponAnimationDetermineEvent.Type.TACTICAL_RELOAD_START -> tacticalReloadStartAnimation
            WeaponAnimationDetermineEvent.Type.TACTICAL_RELOAD -> tacticalReloadAnimation
            WeaponAnimationDetermineEvent.Type.TACTICAL_RELOAD_END -> tacticalReloadEndAnimation
            WeaponAnimationDetermineEvent.Type.RELOAD_INDIVIDUALLY_START -> reloadIndividuallyStartAnimation
            WeaponAnimationDetermineEvent.Type.RELOAD_INDIVIDUALLY -> reloadIndividuallyAnimation
            WeaponAnimationDetermineEvent.Type.RELOAD_INDIVIDUALLY_END -> reloadIndividuallyEndAnimation
        }
    }

    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        clickListener,
        triggerListener,
        actionEndListener,
        endHoldingListener,
        animationListener,
        dropItemListener,
    )

    override fun onLateInit() {
        // 조준 중에는 베이스 탄퍼짐 강제로 설정
        weapon.spread.registerFactor(WeaponSpreadFunction(Int.MIN_VALUE) { if(weapon?.isZooming == true) spread else it })
        weapon.recoil?.let { recoil ->
            recoil.vertical.registerFactor { verticalRecoilModifier }
            recoil.horizontal.registerFactor { horizontalRecoilModifier }
        }
    }

    override fun onInstantiate(weapon: Weapon) {
        weapon.registerNotNull(ZOOMING, false)
    }

    companion object {
        private val ZOOMING = WeaponPropertyType("zooming", WeaponPropertyType.BOOL)
        var Weapon.isZooming: Boolean
            get() = get(ZOOMING) ?: false
            set(value) { set(ZOOMING, value) }
    }
}