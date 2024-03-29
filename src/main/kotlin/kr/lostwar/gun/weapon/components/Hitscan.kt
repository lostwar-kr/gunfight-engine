package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.event.WeaponHitEntityEvent
import kr.lostwar.gun.weapon.event.WeaponHitscanShootEvent
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import kr.lostwar.gun.weapon.event.WeaponShootEvent
import kr.lostwar.netcode.EntityNetcodeFixer.Companion.netcodeFixer
import kr.lostwar.netcode.EntityNetcodeFixer.Companion.useNetcodeFixer
import kr.lostwar.util.DrawUtil
import kr.lostwar.util.ParticleSet
import kr.lostwar.util.math.VectorUtil.dot
import kr.lostwar.util.math.VectorUtil.localToWorld
import kr.lostwar.util.math.VectorUtil.normalized
import kr.lostwar.util.math.VectorUtil.plus
import kr.lostwar.util.nms.NMSUtil
import kr.lostwar.util.nms.NMSUtil.rayTraceBlocksPiercing
import kr.lostwar.util.ui.text.console
import kr.lostwar.vehicle.core.VehicleEntity.Companion.asVehicleEntityOrNull
import kr.lostwar.vehicle.core.VehicleEntity.Companion.vehicleEntityIdOrNull
import kr.lostwar.vehicle.util.ExtraUtil.getOutline
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.util.BoundingBox
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
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
    val thickness: Double = getDouble("thickness", parent?.thickness, 0.0)
    val nearThickness: Double = getDouble("nearThickness", parent?.nearThickness, thickness)
    val nearThicknessRange: Double = getDouble("nearThicknessRange", parent?.nearThicknessRange, 0.0)
    val useHeadShot: Boolean = getBoolean("headShot.enable", parent?.useHeadShot, true)
    val headShotVerticalMultiplier: Double = getDouble("headShot.verticalMultiplier", parent?.headShotVerticalMultiplier, 1.0)
    val headShotHorizontalMultiplier: Double = getDouble("headShot.horizontalMultiplier", parent?.headShotHorizontalMultiplier, 1.0)
    val ignorePlayerGameMode: List<GameMode> = getStringList(
        "ignorePlayerGameMode",
        parent?.ignorePlayerGameMode?.map { it.toString() },
        listOf(GameMode.CREATIVE, GameMode.SPECTATOR).map { it.toString() }
    ).mapNotNull {
        try { GameMode.valueOf(it) }
        catch (_: Exception) { null }
    }

    val effectAsProjectile: ParticleSet = getParticleSet("effect.projectile", parent?.effectAsProjectile)
    val effectAsProjectileOffset: Vector = getVector("effect.projectileOffset", parent?.effectAsProjectileOffset)

    private fun calculateRangeModifier(distance: Double) = damageRangeModifier.pow(distance / rangeModifierConstant)

    private class EntityHitResult(
        val target: LivingEntity,
        val distance: Double,
        val hitResult: RayTraceResult,
        hitHeadResult: RayTraceResult?,
    ) {
        val isHeadShot = hitHeadResult != null
    }

    private val onShoot = WeaponPlayerEventListener(WeaponShootEvent::class.java) { event ->
        val weapon = weapon ?: return@WeaponPlayerEventListener
        val ray = event.shootRay

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

        val world = ray.world
        val rayOrigin = ray.toVector()
        val rayPosition = ray.clone()
        val spreadVector = Vector(
            (Random.nextDouble() - Random.nextDouble()),
            (Random.nextDouble() - Random.nextDouble()),
            (Random.nextDouble() - Random.nextDouble()),
        ).multiply(spread * 0.1)
        val rayDirection = ray.direction.clone().add(spreadVector).normalize()

        hitscanShootEvent.onShoot.forEach { it.invoke(this, rayPosition, rayDirection) }

        val projectileEffectPosition = ray.plus(effectAsProjectileOffset.localToWorld(rayDirection))
        effectAsProjectile.executeEach { it.spawnAt(projectileEffectPosition, source = player, offset = rayDirection, count = 0) }

        val minimumThickness = min(farThickness, nearThickness)

        // 거리에 따른 범위 구하기
        fun getRadius(distance: Double): Double {
            return if(distance >= nearThicknessRange) farThickness
            else ((farThickness - nearThickness)/nearThicknessRange) * distance + minimumThickness
        }

//        GunEngine.log("entity pre-raycast:")
        // 최적화된 entity raycast
        // 전체 엔티티 검사를 딱 한 번만 함
        val dummyVector = Vector()
        val entities = world.livingEntities.mapNotNull { target ->
            // 방향벡터와 발사위치 기준 상대 위치와 내적
            // 또한 내적값은 방향벡터 기준 거리값도 가지고있음
            val dot = rayDirection.dot(target.location.subtract(rayOrigin))
            // 내적 결과값 음수면 발사 방향 기준 뒤편에 있음
            // 양수여도 최대거리 이상이면 스킵
            if(dot < 0 || dot < minimumEntityHitDistance || dot > maximumRange) return@mapNotNull null
            // 필터링
            if(target.type == EntityType.PLAYER) {
                val targetPlayer = target as Player
                if(targetPlayer.entityId == player.entityId || targetPlayer.gameMode in ignorePlayerGameMode) return@mapNotNull null
            }
            if(!event.filter(target, player)) {
               return@mapNotNull null
            }

            val boundingBox = target.boundingBox
            if(boundingBox.volume <= 0.0) return@mapNotNull null
            if(useNetcodeFixer) {
                // 넷코드 히트박스 이동

                // 1. 탑승중인 vehicle이 있으면 vehicle의 offset을 따라감.
                // 2. 엔티티 자체의 netcodeFixer가 있으면 엔티티 자체의 offset을 따라감
                // 3. 엔티티가 andoo 차량인 경우 차량의 offset을 따라감
                target.vehicle?.netcodeFixer?.let {
                    it.getOffsetNonAlloc(dummyVector)
                    boundingBox.shift(dummyVector)
                }?.also { console("target ${target.name}'s vehicle ${target.vehicle?.name} netcode applied") }
                    ?: target.netcodeFixer?.let {
                        it.getOffsetNonAlloc(dummyVector)
                        boundingBox.shift(dummyVector)
                    }?.also { console("target ${target.name} netcode applied") }
            }
            val hitbox = boundingBox.expand(getRadius(dot))
            // raytrace 실패 시 히트 안 했음
            val hitResult = hitbox.rayTrace(rayOrigin, rayDirection, maximumRange) ?: return@mapNotNull null
            // fixme andoo 차량인 경우 헤드샷 판정 무시
            val hitHeadResult = if(useHeadShot && target.vehicleEntityIdOrNull == null) {
                // 머갈통 히트박스
                // xz는 그대로 가져가고, y축은 (높이 - 눈) * 2
                val headX = (boundingBox.widthX/2.0) * headShotHorizontalMultiplier
                val headZ = (boundingBox.widthZ/2.0) * headShotHorizontalMultiplier
                val headY = (boundingBox.height - target.eyeHeight) * headShotVerticalMultiplier
                val headHitbox = BoundingBox.of(target.eyeLocation, headX, headY, headZ)
                // DEBUG
//                DrawUtil.drawFor(40, 10,
//                    hitbox.getOutline(2),
//                    Particle.DustOptions(Color.GREEN, 1f),
//                )
                val result = headHitbox.rayTrace(rayOrigin, rayDirection, maximumRange)
                if(GunEngine.isDebugging) {
                    if(result != null) {
                        DrawUtil.drawFor(40, 10,
                            headHitbox.getOutline(2),
                            Particle.DustOptions(Color.RED, 1f),
                        )
                    }else{
                        DrawUtil.drawFor(40, 10,
                            headHitbox.getOutline(2),
                            Particle.DustOptions(Color.YELLOW, 0.5f),
                        )
                    }
                }
                result
            }else null
//            GunEngine.log("- entity hit(${target}, ${dot}, ${hitResult})")
            EntityHitResult(target, dot, hitResult, hitHeadResult)
        }.sortedBy { it.distance } // 거리 기준 정렬
        val entitiesSize = entities.size
//        GunEngine.log("hitted entity count: ${entitiesSize}")

//        GunEngine.log("block raycast:")
        var currentEntityIndex = 0
        var currentDistance = entities.firstOrNull()?.distance ?: 0.0
        var isBlockPierced = false
        var resistanceFactor = 0.0 // 0.0 ~ 1.0, 1.0부터는 작동 안 함
        // 블록 raytrace
        // NMS 자체 raytrace 방법 사용(traverseBlocks)
        world.rayTraceBlocksPiercing(
            rayOrigin,
            rayDirection,
            maximumRange,
            FluidCollisionMode.NEVER,
        ) { blockDistance, blockHitResult ->
//            GunEngine.log("- block onHit(${blockDistance}, ${blockHitResult})")
            // 블록 충돌 이전의 모든 엔티티 충돌 처리
            while(currentEntityIndex < entitiesSize && blockDistance > currentDistance) {
                val hitResult = entities[currentEntityIndex]
                val target = hitResult.target

                val rangeModifier = calculateRangeModifier(currentDistance)
//                GunEngine.log("  * entity hit[${currentEntityIndex}](${target}, ${currentDistance}, ${rangeModifier})")
                if(rangeModifier <= 0){
//                    GunEngine.log("  ! entity hit stop by rangeModifier smaller than zero")
                    return@rayTraceBlocksPiercing NMSUtil.RayTraceContinuation.STOP
                }
                if(rangeModifier + resistanceFactor <= 0){
//                    GunEngine.log("  ! entity hit stop by rangeModifier + resistanceFactor smaller than zero")
                    return@rayTraceBlocksPiercing NMSUtil.RayTraceContinuation.STOP
                }

                val isHeadShot = hitResult.isHeadShot
                with(weapon.type.hit) {
                    val weaponHitEntityResult = hitEntity(
                        victim = target,
                        distance = hitResult.distance,
                        location = hitResult.hitResult.hitPosition.toLocation(world),
                        isHeadShot = isHeadShot,
                        isPiercing = isBlockPierced
                    ) { originalDamage ->
                        originalDamage * max(0.0, rangeModifier + resistanceFactor)
                    }
                    if(weaponHitEntityResult != WeaponHitEntityEvent.DamageResult.IGNORE) {
                        resistanceFactor -= entityPierceResistance
                    }
                }

                ++currentEntityIndex
                currentDistance = if(currentEntityIndex < entitiesSize) {
                    entities[currentEntityIndex].distance
                }else{
                    Double.MAX_VALUE
                }
            }
            if(blockHitResult == null){
//                GunEngine.log("- blockHitResult == null, PIERCE")
                return@rayTraceBlocksPiercing NMSUtil.RayTraceContinuation.PIERCE
            }
            val block = blockHitResult.hitBlock!!
            // DEBUG
//            DrawUtil.drawFor(60, 10,
//                block.boundingBox.getOutline(2),
//                Particle.DustOptions(Color.BLUE, 1f),
//            )
            val result = with(weaponType.hit) { hitBlock(
                blockHitResult.hitPosition.toLocation(world),
                block,
                blockHitResult.hitBlockFace?.direction ?: ray.direction.multiply(-1)
            ) }
            if(result.blockRay) {
//                GunEngine.log("! block hit: ${block}, ray stop")
                return@rayTraceBlocksPiercing NMSUtil.RayTraceContinuation.STOP
            }
            if(result.pierceSolid) {
                isBlockPierced = true
            }
            resistanceFactor -= result.resistance
            if(resistanceFactor <= -1) {
//                GunEngine.log("! block hit: ${block}, ray stop by resistance")
                NMSUtil.RayTraceContinuation.STOP
            }else{
//                GunEngine.log("- block hit: ${block}, ray continue")
                NMSUtil.RayTraceContinuation.PIERCE
            }
        }



        // 이전 코드
        /*
        rayLoop@while(currentDistance <= maximumRange) {
            rayPosition.add(moveVector)

            if(!rayPosition.isChunkLoaded) {
               break
            }

            // Block
            val block = rayPosition.block
            if(!hitBlock.contains(block)) {
                hitBlock.add(block)
                val result = with(weaponType.hitBlock) { hit(rayPosition, block, ray.direction.multiply(-1)) }
                if(result.blockRay) {
                    break
                }
                if(result.pierceSolid) {
                    isBlockPierced = true
                }
                resistanceFactor -= result.resistance
            }
            if(resistanceFactor <= -1.0) break

            val rangeModifier = calculateRangeModifier(currentDistance)
            if(rangeModifier <= 0) break
            if(rangeModifier + resistanceFactor <= 0) break

            // Entity
            if(currentDistance < minimumEntityHitDistance) {
                currentDistance += moveLength
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
//            hitscanShootEvent.onScan.forEach { it.invoke(this, rayPosition) }
            currentDistance += moveLength
        }
        */
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