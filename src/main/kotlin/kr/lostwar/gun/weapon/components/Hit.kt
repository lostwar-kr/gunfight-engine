package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.Constants
import kr.lostwar.util.CustomMaterialSet
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.components.HitBlock.Companion.getResult
import kr.lostwar.gun.weapon.event.WeaponHitEntityEvent
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEvent
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import kr.lostwar.util.ParticleInfo.Companion.getParticleInfo
import kr.lostwar.util.ParticleSet
import kr.lostwar.util.SoundClip
import kr.lostwar.util.block.BlockUtil
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.toLocationString
import kr.lostwar.util.math.VectorUtil.toVectorString
import kr.lostwar.util.nms.NMSUtil.damage
import kr.lostwar.util.ui.text.console
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.util.Vector

class Hit(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Hit?,
) : WeaponComponent(config, weapon, parent, true) {

    val damage: Double = getDouble("entity.damage", parent?.damage)
    val resetHitCooldown: Boolean = getBoolean("entity.resetHitCooldown", parent?.resetHitCooldown, true)
    val resetVelocity: Boolean = getBoolean("entity.resetVelocity", parent?.resetVelocity, true)
    val hitSound: SoundClip = getSoundClip("entity.hitSound", parent?.hitSound)

    val entityPierceResistance: Double = getDouble("entity.pierce.resistance", parent?.entityPierceResistance, 1.0)
    val pierceSound: SoundClip = getSoundClip("entity.pierce.sound", parent?.pierceSound)

    val headShotDamageAdd: Double = getDouble("entity.headShot.damageAdd", parent?.headShotDamageAdd, 0.0)
    val headShotDamageMultiply: Double = getDouble("entity.headShot.damageMultiply", parent?.headShotDamageMultiply, 2.0)
    val headShotSound: SoundClip = getSoundClip("entity.headShot.sound", parent?.headShotSound)

    fun WeaponPlayer.hitEntity(
        victim: LivingEntity,
        distance: Double,
        damage: Double = this@Hit.damage,
        damageSource: Entity? = null,
        location: Location? = null,
        isHeadShot: Boolean = false,
        isPiercing: Boolean = false,
        damageModifier: (Double) -> Double = { it },
    ): WeaponHitEntityEvent.DamageResult {
        val originalDamage = (if(isHeadShot) damage * headShotDamageMultiply else damage) + (if(isHeadShot) headShotDamageAdd else 0.0)
        val finalDamage = damageModifier(originalDamage)
        val type = this@Hit.weapon
        val event = WeaponHitEntityEvent(this,
            victim,
            finalDamage,
            type,
            damageSource,
            distance,
            location,
            isHeadShot,
            isPiercing,
        )
        if(weapon?.type == type) {
            event.callEventOnHoldingWeapon(true)
        }else{
            event.callEvent(type, true)
        }
        if(event.isCancelled) {
            return event.result
        }
        val damage = event.damage
        if(victim.health - damage <= 0) {
            victim.killer = player
        }
        // using special cause
        victim.damage(damage, Constants.weaponDamageCause)
        if(resetHitCooldown){
            victim.noDamageTicks = 0
        }
        if(resetVelocity) {
            victim.velocity = VectorUtil.ZERO
        }
        hitSound.playToPlayer(player)
        if(isHeadShot) {
            headShotSound.playToPlayer(player)
        }
        if(isPiercing) {
            pierceSound.playToPlayer(player)
        }

        return event.result
    }


    private val interactionMap: Map<HitBlockInteraction, HitBlock> = get("", parent?.interactionMap, emptyMap()) { _ ->
        val map = HashMap<HitBlockInteraction, HitBlock>(parent?.interactionMap)
        for(key in getKeys(false)) {
            if(key == "entity") continue
            val interaction = HitBlockInteraction.registeredInteractionMap[key]
            if(interaction == null) {
                GunEngine.logWarn("invalid interaction key ${key} while loading $weapon")
                continue
            }
            map[interaction] = getResult(key, map[interaction] ?: interaction.defaultResult)
        }
//        console("weapon ${weapon.key} - Hit component interactions")
        map
//            .onEach { (interaction, hitBlock) ->
//            console("- ${interaction.name}: ${hitBlock.types.joinToString()}")
//        }
    }!!


    fun WeaponPlayer.hitBlock(location: Location, block: Block, hitNormal: Vector): HitBlock {
        val type = block.type
//        var isEmptySpace: Boolean? = null
//        console("hitBlock(${location.toVectorString()}, ${block.type})")
        for(interaction in HitBlockInteraction.registeredInteraction) {
            val inputResult = interactionMap[interaction] ?: interaction.defaultResult
//            console("- ${interaction}: ${inputResult.types.joinToString(prefix = "[", postfix = "]")}")
            if(!inputResult.contains(type)) {
                continue
            }
            /*
            // emptySpace 연산은 한 번만 하도록
            if(inputResult.checkIsEmptySpace) {
                if(isEmptySpace == null) {
                    isEmptySpace = BlockUtil.isEmptySpace(location, block)
                }
                if(isEmptySpace == true) {
                    continue
                }
            }
            */
//            console("! hit ${interaction}")
            val outputResult = interaction.onHit(this, this@Hit, location, inputResult, block, hitNormal)
            return outputResult
        }

        // 여기까지 올 일은 없겠지만 ...
        val fallbackInteraction = HitBlockInteraction.builtInCollideInteraction
        val fallbackResult = interactionMap[fallbackInteraction] ?: fallbackInteraction.defaultResult
        return fallbackInteraction.onHit(this, this@Hit, location, fallbackResult, block, hitNormal)
    }

}

data class HitBlock(
    val types: Set<Material>?,
//    val checkIsEmptySpace: Boolean,
    val blockRay: Boolean,
    val pierceSolid: Boolean,
    val resistance: Double,
    val effect: ParticleSet,
) {
    operator fun contains(material: Material) = types?.contains(material) ?: true
    companion object {
        fun ConfigurationSection.getResult(key: String, default: HitBlock): HitBlock {
            val section = getConfigurationSection(key) ?: return default

//            val checkIsEmptySpace = section.getBoolean("checkIsEmptySpace", default.checkIsEmptySpace)
            val blockRay = section.getBoolean("blockRay", default.blockRay)
            val pierceSolid = section.getBoolean("pierceSolid", default.pierceSolid)
            val resistance = section.getDouble("resistance", default.resistance)
            val effect = ParticleSet.getParticleSetOrNull(section, "effect") ?: default.effect

            val types =
                if(!section.isList("types")) default.types
                else section.getStringList("types").let { rawList ->
                    val set = HashSet<Material>()
                    for(rawType in rawList) {
                        set.apply(rawType, default)
                    }
                    set
                }

            return HitBlock(
                types,
//                checkIsEmptySpace,
                blockRay,
                pierceSolid,
                resistance,
                effect
            )
        }

        private fun MutableSet<Material>.apply(rawType: String, default: HitBlock) {
            val defaultTypes = default.types
            if(defaultTypes == null) {
                GunEngine.logWarn("redefining fallback hitblock material set detected")
                return
            }
            if(rawType == "default") {
                addAll(defaultTypes)
                return
            }
            if(!rawType.contains('.')){
                val material = Material.getMaterial(rawType) ?: run {
                    GunEngine.logWarn("cannot parse material ${rawType}")
                    return
                }
                if(rawType.startsWith('-'))
                    remove(material)
                else
                    add(material)
                return
            }
        }
    }
}

class HitBlockInteraction(
    val name: String,
    val defaultResult: HitBlock,
    val onHit: WeaponPlayer.(
        hitBlock: Hit,
        location: Location,
        result: HitBlock,
        block: Block,
        hitNormal: Vector
    ) -> HitBlock = { _, _, result, _, _ -> result }
) {

    companion object {
        val builtInCollideInteraction = HitBlockInteraction("collide", HitBlock(
            null,
            true,
            false,
            1.0,
            ParticleSet.emptySet,
        )) { hitBlock, location, result, block, hitNormal ->
            result.effect.forEach { it.spawnAt(location, offset = hitNormal) }
            result
        }
        private val interactions = mutableListOf<HitBlockInteraction>(
            HitBlockInteraction("ignore", HitBlock(
                CustomMaterialSet.completelyPassable,
                false,
                false,
                0.0,
                ParticleSet.emptySet,
            )),
            HitBlockInteraction("glass", HitBlock(
                CustomMaterialSet.glasses,
                false,
                false,
                0.0,
                ParticleSet.emptySet,
            )) { hitBlock, location, result, block, hitNormal ->
                block.breakNaturally()
                result
            },
            HitBlockInteraction("pierce", HitBlock(
                CustomMaterialSet.passable,
                false,
                true,
                0.5,
                ParticleSet.emptySet,
            )) { hitBlock, location, result, block, hitNormal ->
                result
            },
        )
        fun register(interaction: HitBlockInteraction) {
            interactions.add(interaction)
        }
        val registeredInteraction by lazy { interactions + builtInCollideInteraction }
        val registeredInteractionMap by lazy { registeredInteraction.associateBy { it.name } }

    }

    override fun toString(): String {
        return name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other is Hit) {
            return name == other.name
        }
        return super.equals(other)
    }

}