package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.*
import kr.lostwar.gun.weapon.actions.DelayAction
import kr.lostwar.gun.weapon.actions.LoadEventType
import kr.lostwar.gun.weapon.actions.ShootAction
import kr.lostwar.gun.weapon.components.Ammo.Companion.ammo
import kr.lostwar.gun.weapon.components.Ammo.Companion.lastLoadEvent
import kr.lostwar.gun.weapon.components.Ammo.Companion.lastLoadMotion
import kr.lostwar.gun.weapon.event.*
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import kr.lostwar.util.AnimationClip
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Event
import org.bukkit.event.inventory.ClickType
import org.bukkit.persistence.PersistentDataType

class Shoot(
    config: ConfigurationSection?,
    weaponType: WeaponType,
    parent: Shoot?,
) : WeaponComponent(config, weaponType, parent) {

    val shootDelay: Int = getInt("shootDelay", parent?.shootDelay, 1)
    val shootAnimation: AnimationClip = getAnimationClip("shootAnimation", parent?.shootAnimation)
    val shootClickTicks: Int = getInt("shootClickTicks", parent?.shootClickTicks, 6)

    private val onClick = WeaponPlayerEventListener(WeaponClickEvent::class.java) { event ->
        if(event.clickType != ClickType.RIGHT) {
            return@WeaponPlayerEventListener
        }
        val weapon = event.weapon ?: return@WeaponPlayerEventListener
        weaponType.ammo?.let {
            if(weapon.ammo <= 0) {
                return@WeaponPlayerEventListener
            }
        }
        trigger()
    }
    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        onClick,
        // 무기를 들었을 때
        WeaponPlayerEventListener(WeaponStartHoldingEvent::class.java) { event ->
            val weapon = event.weapon
            // 아직 action 이 아무에게도 점령당하지 않은 경우
            if(weapon.primaryAction != null) return@WeaponPlayerEventListener
            // 남은 shootDelay 가 있을 때
            val leftShootDelay = weapon.leftShootDelay
            if(leftShootDelay > 0) {
                weapon.primaryAction = DelayAction(weapon, leftShootDelay)
                weapon.leftShootDelay = 0
            }
        },
        WeaponPlayerEventListener(WeaponActionEndEvent::class.java) { event ->
            val weapon = event.weapon
            // 무기가 달라진 경우
            if(event.isWeaponChanged) {
                return@WeaponPlayerEventListener
            }
            // 다음 action 이 빈자리일 때
            if(event.newAction == null) {
                // 남은 shootDelay 가 있을 때
                val leftShootDelay = weapon.leftShootDelay
                if(leftShootDelay > 0) {
                    event.newAction = DelayAction(weapon, leftShootDelay)
                    weapon.leftShootDelay = 0
                }
            }
        },
    )

    private fun WeaponPlayer.trigger() {
        val weapon = weapon ?: return
        val currentAction = weapon.primaryAction
        if(currentAction is ShootAction) {
            currentAction.trigger()
            return
        }
        val triggerEvent = WeaponTriggerEvent(this)
            .callEventOnHoldingWeapon()
        if(triggerEvent.isCancelled){
            return
        }

        val shootAction = ShootAction(weapon)
        weapon.primaryAction = shootAction
    }

    internal fun WeaponPlayer.shoot(action: ShootAction) {
        val weapon = weapon ?: return
        val shootEvent = WeaponShootEvent(this, action)
            .callEventOnHoldingWeapon()
        player.sendMessage("shoot")
        // TODO 발사 로직
    }

    override fun onInstantiate(weapon: Weapon) {
        weapon.registerNullable(LEFT_SHOOT_DELAY, 0)
    }
    companion object {
        private val LEFT_SHOOT_DELAY = WeaponPropertyType("shoot.left_delay", PersistentDataType.INTEGER)

        var Weapon.leftShootDelay: Int
            get() = get(LEFT_SHOOT_DELAY) ?: 0
            set(value) { set(LEFT_SHOOT_DELAY, value) }
    }
}

