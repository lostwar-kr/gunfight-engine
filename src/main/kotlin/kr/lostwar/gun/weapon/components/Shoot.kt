package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.*
import kr.lostwar.gun.weapon.actions.DelayAction
import kr.lostwar.gun.weapon.actions.ShootAction
import kr.lostwar.gun.weapon.components.Ammo.Companion.ammo
import kr.lostwar.gun.weapon.event.*
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import kr.lostwar.util.AnimationClip
import kr.lostwar.util.ParticleSet
import kr.lostwar.util.SoundClip
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.localToWorld
import kr.lostwar.util.math.VectorUtil.plus
import kr.lostwar.util.math.VectorUtil.toVectorString
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.inventory.ClickType
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector

class Shoot(
    config: ConfigurationSection?,
    weaponType: WeaponType,
    parent: Shoot?,
) : WeaponComponent(config, weaponType, parent) {

    val shootDelay: Int = getInt("delay", parent?.shootDelay, 1)
    val shootCount: Int = getInt("shootCount", parent?.shootCount, 1)
    val sound: SoundClip = getSoundClip("sound", parent?.sound)
    val animation: AnimationClip = getAnimationClip("animation", parent?.animation)
    val clickTicks: Int = getInt("clickTicks", parent?.clickTicks, 6)
    val adjustDirectionByShootPositionOffset: Boolean = getBoolean("adjustDirectionByShootPositionOffset", parent?.adjustDirectionByShootPositionOffset, true)
    val adjustDirectionThickness: Double = getDouble("adjustDirectionThickness", parent?.adjustDirectionThickness, 1.0)
    val adjustDirectionRange: Double = getDouble("adjustDirectionRange", parent?.adjustDirectionRange, 160.0)
    val shootPositionOffset: List<Vector> = getStringList("shootPositionOffset", parent?.shootPositionOffset?.map { it.toVectorString() })
        .mapNotNull { VectorUtil.fromVectorString(it) ?: GunEngine.logErrorNull("cannot parse offset vector: $it") }

    val takeItemOnShoot: Boolean = getBoolean("takeItemOnShoot", parent?.takeItemOnShoot, false)

    val effectAtMuzzle: ParticleSet = getParticleSet("effect.muzzle", parent?.effectAtMuzzle)
    val effectAtMuzzleOffset: Vector = getVector("effect.muzzleOffset", parent?.effectAtMuzzleOffset)

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
        val prepareEvent = WeaponShootPrepareEvent(this, action, player.eyeLocation)
            .callEventOnHoldingWeapon(callBukkit = true)
        action.shoot.sound.playAt(player)

        val immutableRay = prepareEvent.ray
        val ray: Location = prepareEvent.ray


        val shootEvent = WeaponShootEvent(this, action, ray, prepareEvent.filter)
        // 한 개면 그냥 이펙트 한 번만
        if(shootPositionOffset.isEmpty() || shootPositionOffset.size == 1) {
            val direction = ray.direction
            val muzzleEffectPosition = ray.plus(effectAtMuzzleOffset.localToWorld(direction))
            effectAtMuzzle.executeEach { it.spawnAt(muzzleEffectPosition, source = player, offset = it.offset.localToWorld(direction)) }
        }
        val initialShootCount = weapon.shootCount
        for(i in 0 until shootCount) {
            if(shootPositionOffset.isNotEmpty()) {
                nextShootPositionOffset(ray, immutableRay, prepareEvent.filter)
                // 여러개면 총구당 한 번씩만
                if(weapon.shootCount - initialShootCount <= shootPositionOffset.size) {
                    val direction = ray.direction
                    val muzzleEffectPosition = ray.plus(effectAtMuzzleOffset.localToWorld(direction))
                    effectAtMuzzle.executeEach { it.spawnAt(muzzleEffectPosition, source = player, offset = it.offset.localToWorld(direction)) }
                }
            }
            shootEvent.callEventOnHoldingWeapon()
        }

        if(takeItemOnShoot) {
            val item = player.inventory.itemInMainHand
            item.amount -= 1
            if(item.amount <= 0) {
                WeaponAllUsedEvent(this).callEvent()
            }
        }
    }

    fun WeaponPlayer.nextShootPositionOffset(mutableRay: Location, immutableRay: Location, filter: RaycastPredicate) {
        val weapon = weapon!!
        val currentOffset = shootPositionOffset[weapon.shootCount % shootPositionOffset.size]
        val currentWorldOffset = currentOffset.localToWorld(immutableRay.direction)
        val currentOffsetPosition = currentWorldOffset.clone().add(immutableRay.toVector())

        // 발사 위치만 설정, 방향은 그대로 사용
        if(!adjustDirectionByShootPositionOffset) {
            mutableRay.set(currentOffsetPosition.x, currentOffsetPosition.y, currentOffsetPosition.z)
        }
        // 방향도 보정
        else{
            player.world.rayTrace(
                immutableRay, immutableRay.direction,
                adjustDirectionRange,
                FluidCollisionMode.NEVER,
                true, // ignorePassable
                adjustDirectionThickness
            ) { it is LivingEntity && filter(it, player) } // filter
                ?.let { result ->
                    mutableRay.direction = result.hitPosition.subtract(currentWorldOffset)
                }
        }
        ++weapon.shootCount
    }

    override fun onInstantiate(weapon: Weapon) {
        weapon.registerNullable(LEFT_SHOOT_DELAY, 0)
        weapon.registerNullable(SHOOT_COUNT, 0)
    }
    companion object {
        private val LEFT_SHOOT_DELAY = WeaponPropertyType("shoot.left_delay", PersistentDataType.INTEGER)
        private val SHOOT_COUNT = WeaponPropertyType("shoot.count", PersistentDataType.INTEGER)

        var Weapon.leftShootDelay: Int
            get() = get(LEFT_SHOOT_DELAY) ?: 0
            set(value) { set(LEFT_SHOOT_DELAY, value) }

        var Weapon.shootCount: Int
            get() = get(SHOOT_COUNT) ?: 0
            set(value) { set(SHOOT_COUNT, value) }
    }
}

