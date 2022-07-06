package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.event.WeaponBlockHitEvent
import kr.lostwar.gun.weapon.event.WeaponHitEntityEvent
import kr.lostwar.gun.weapon.event.WeaponHitscanShootEvent
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import kr.lostwar.gun.weapon.event.WeaponShootEvent
import kr.lostwar.util.math.VectorUtil.normalized
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.util.Vector
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class Hitscan(
    config: ConfigurationSection?,
    weaponType: WeaponType,
    parent: Hitscan?,
) : WeaponComponent(config, weaponType, parent) {

    val minimumEntityHitDistance: Double = getDouble("minimumEntityHitDistance", parent?.minimumEntityHitDistance, 0.0)
    val maximumRange: Double = getDouble("maximumRange", parent?.maximumRange, 100.0)
    val damageRangeModifier: Double = getDouble("damageRangeModifier", parent?.damageRangeModifier, 1.0)
    val thickness: Double = getDouble("thickness", parent?.thickness, 1.0)
    val nearThickness: Double = getDouble("nearThickness", parent?.nearThickness, thickness)
    val nearThicknessRange: Double = getDouble("nearThicknessRange", parent?.nearThicknessRange, 0.0)
    val useHeadShot: Boolean = getBoolean("headShot.enable", parent?.useHeadShot, true)
    val headShotCheckRadiusMultiplier: Double = getDouble("headShot.checkRadiusMultiplier", parent?.headShotCheckRadiusMultiplier, 1.0)
    val headShotCheckDensity: Double = getDouble("headShot.checkDensity", parent?.headShotCheckDensity, 0.1)
    val ignorePlayerGameMode: List<GameMode> = getStringList(
        "ignorePlayerGameMode",
        parent?.ignorePlayerGameMode?.map { it.toString() },
        listOf(GameMode.CREATIVE, GameMode.SPECTATOR).map { it.toString() }
    ).mapNotNull {
        try { GameMode.valueOf(it) }
        catch (_: Exception) { null }
    }
    val entityResistance: Double = getDouble("resistance.entity", parent?.entityResistance, 0.0)

    private fun calculateRangeModifier(distance: Double) = damageRangeModifier.pow(distance / rangeModifierConstant)

    private val onShoot = WeaponPlayerEventListener(WeaponShootEvent::class.java) { event ->
        val weapon = weapon ?: return@WeaponPlayerEventListener
        val ray = event.shootRay

        val hitEntity = hashSetOf<UUID>()
        val hitBlock = hashSetOf<Block>()

        val spread = weapon.type.spread.getSpread(this)
        val hitscanShootEvent = WeaponHitscanShootEvent(this,
            thickness,
            nearThickness,
            nearThicknessRange
        ).callEventOnHoldingWeapon()
        val (
            farThickness,
            nearThickness,
            nearThicknessRange
        ) = hitscanShootEvent

        val rayPosition = ray.clone()
        val spreadVector = Vector(
            (Random.nextDouble() - Random.nextDouble()),
            (Random.nextDouble() - Random.nextDouble()),
            (Random.nextDouble() - Random.nextDouble()),
        ).multiply(spread * 0.1)
        val rayDirection = ray.direction.clone().add(spreadVector).normalize()
        hitscanShootEvent.onShoot.forEach { it.invoke(this, rayPosition, rayDirection) }
        val moveLength = min(farThickness, nearThickness)
        rayDirection.multiply(moveLength)

        var isBlockPierced = false
        var distance = 0.0
        var resistanceFactor = 0.0 // 0.0 ~ 1.0, 1.0부터는 작동 안 함
        rayLoop@while(distance <= maximumRange) {
            val radius =
                if(distance >= nearThicknessRange) farThickness
                else ((farThickness - nearThickness)/nearThicknessRange) * distance + moveLength // moveLength == 최솟값
            rayPosition.add(rayDirection)

            if(!rayPosition.isChunkLoaded) {
               break
            }

            // Block
            val block = rayPosition.block
            if(!hitBlock.contains(block)) {
                hitBlock.add(block)
                val result = with(weaponType.hitBlock) { hit(block) }

                val blockHitEvent = WeaponBlockHitEvent(this, block, resistanceFactor)
                    .callEventOnHoldingWeapon()
                resistanceFactor = blockHitEvent.resistanceFactor
                if(blockHitEvent.isBlockPierced) isBlockPierced = true
            }
            if(resistanceFactor <= -1.0) break

            val rangeModifier = calculateRangeModifier(distance)
            if(rangeModifier <= 0) break
            if(rangeModifier + resistanceFactor <= 0) break

            // Entity
            if(distance < minimumEntityHitDistance) {
                distance += moveLength
                continue
            }

            val nearbyEntities = rayPosition.world.getNearbyLivingEntities(rayPosition, radius) {
                !hitEntity.contains(it.uniqueId) && player.uniqueId != it.uniqueId && !it.isDead
            }
            for(target in nearbyEntities) {
                hitEntity.add(target.uniqueId)

                if(target.type == EntityType.PLAYER) {
                    val targetPlayer = target as Player
                    if(targetPlayer.gameMode in ignorePlayerGameMode) continue
                }

                if(!event.filter(target, player)) {
                    continue
                }

                val isHeadShot = useHeadShot && checkHeadShot(
                    rayDirection, rayPosition, target,
                    radius * headShotCheckRadiusMultiplier, headShotCheckDensity
                )
                with(weapon.type.hitEntity) {
                    val result = hit(target, location = rayPosition, isHeadShot = isHeadShot, isPiercing = isBlockPierced) { originalDamage ->
                        originalDamage * max(0.0, rangeModifier + resistanceFactor)
                    }
                    if(result != WeaponHitEntityEvent.DamageResult.IGNORE) {
                        resistanceFactor -= entityResistance
                    }
                }

            }
            hitscanShootEvent.onScan.forEach { it.invoke(this, rayPosition) }
            distance += moveLength
        }

    }
    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        onShoot,
    )

    companion object {
        const val rangeModifierConstant = 9.525
        private fun checkHeadShot(direction: Vector, start: Location, victim: LivingEntity, radius: Double, density: Double = 0.1): Boolean {
            val dir = direction.normalized.multiply(density)
            val position = start.clone()
            val radiusSquare = radius * radius
            var distance = 0.0
            while(distance <= 1.0) {
                if(position.distanceSquared(victim.eyeLocation) <= radiusSquare) {
                    return true
                }
                position.add(dir)
                distance += density
            }
            return false
        }
    }
}