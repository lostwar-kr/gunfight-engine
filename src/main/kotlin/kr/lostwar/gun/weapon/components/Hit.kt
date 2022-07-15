package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.setting.CustomMaterialSet
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.components.HitBlock.Companion.getResult
import kr.lostwar.gun.weapon.event.WeaponHitEntityEvent
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import kr.lostwar.util.block.BlockUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector

class Hit(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Hit?,
) : WeaponComponent(config, weapon, parent, true) {

    val entityDamage: Double = getDouble("entity.damage", parent?.entityDamage)
    val entityResistance: Double = getDouble("entity.resistance", parent?.entityResistance, 1.0)
    val headShotDamageAdd: Double = getDouble("entity.headShot.damageAdd", parent?.headShotDamageAdd, 0.0)
    val headShotDamageMultiply: Double = getDouble("entity.headShot.damageMultiply", parent?.headShotDamageMultiply, 2.0)

    fun WeaponPlayer.hitEntity(
        victim: LivingEntity,
        damage: Double = this@Hit.entityDamage,
        damageSource: Entity? = null,
        location: Location? = null,
        isHeadShot: Boolean = false,
        isPiercing: Boolean = false,
        damageModifier: (Double) -> Double = { it },
    ): WeaponHitEntityEvent.DamageResult {
        val originalDamage = (if(isHeadShot) damage * headShotDamageMultiply else damage) + (if(isHeadShot) headShotDamageAdd else 0.0)
        val finalDamage = damageModifier(originalDamage)
        val event = WeaponHitEntityEvent(this,
            victim,
            finalDamage,
            damageSource,
            location,
            isHeadShot,
            isPiercing
        )
            .callEventOnHoldingWeapon(true)
        if(event.isCancelled) {
            return event.result
        }
        val damage = event.damage
        if(victim.health - damage <= 0) {
            victim.killer = player
        }
        victim.damage(damage)
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
            map[interaction] = getResult(key, parent?.interactionMap?.get(interaction) ?: interaction.defaultResult)
        }
        map
    }!!


    fun WeaponPlayer.hitBlock(location: Location, block: Block, hitNormal: Vector): HitBlock {
        val type = block.type
        var isEmptySpace: Boolean? = null
        for(interaction in HitBlockInteraction.registeredInteraction) {
            val inputResult = interactionMap[interaction] ?: interaction.defaultResult
            if(!inputResult.contains(type)) {
                continue
            }
            // emptySpace 연산은 한 번만 하도록
            if(inputResult.checkIsEmptySpace) {
                if(isEmptySpace == null) {
                    isEmptySpace = BlockUtil.isEmptySpace(location, block)
                }
                if(isEmptySpace == true) {
                    continue
                }
            }
            val outputResult = interaction.onHit(this, this@Hit, inputResult, block, hitNormal)
            return outputResult
        }

        // 여기까지 올 일은 없겠지만 ...
        val fallbackInteraction = HitBlockInteraction.builtInCollideInteraction
        val fallbackResult = interactionMap[fallbackInteraction] ?: fallbackInteraction.defaultResult
        return fallbackInteraction.onHit(this, this@Hit, fallbackResult, block, hitNormal)
    }

}

data class HitBlock(
    val types: Set<Material>,
    val checkIsEmptySpace: Boolean,
    val blockRay: Boolean,
    val pierceSolid: Boolean,
    val resistance: Double,
    private val checkContains: (Material) -> Boolean = { types.contains(it) }
) {
    operator fun contains(material: Material) = checkContains(material)
    companion object {
        fun ConfigurationSection.getResult(key: String, default: HitBlock): HitBlock {
            val section = getConfigurationSection(key) ?: return default

            val checkIsEmptySpace = section.getBoolean("checkIsEmptySpace", default.checkIsEmptySpace)
            val blockRay = section.getBoolean("blockRay", default.blockRay)
            val pierceSolid = section.getBoolean("pierceSolid", default.pierceSolid)
            val resistance = section.getDouble("resistance", default.resistance)

            val types =
                if(!section.isList("types")) default.types
                else section.getStringList("types").let { rawList ->
                    val set = HashSet<Material>()
                    for(rawType in rawList) {
                        set.apply(rawType, default)
                    }
                    set
                }

            return HitBlock(types, checkIsEmptySpace, blockRay, pierceSolid, resistance, default.checkContains)
        }

        private fun MutableSet<Material>.apply(rawType: String, default: HitBlock) {
            if(rawType == "default") {
                addAll(default.types)
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
        result: HitBlock,
        block: Block,
        hitNormal: Vector
    ) -> HitBlock = { _, result, _, _ -> result }
) {

    companion object {
        val builtInCollideInteraction = HitBlockInteraction("collide", HitBlock(
            emptySet(),
            true,
            true,
            false,
            1.0,
            { true }
        )) { hitBlock, result, block, hitNormal ->

            result
        }
        private val interactions = mutableListOf<HitBlockInteraction>(
            HitBlockInteraction("ignore", HitBlock(
                CustomMaterialSet.completelyPasssable,
                false,
                false,
                false,
                0.0
            )),
            HitBlockInteraction("glass", HitBlock(
                CustomMaterialSet.glasses,
                false,
                false,
                false,
                0.0
            )) { hitBlock, result, block, hitNormal ->
                block.breakNaturally()
                result
            },
            HitBlockInteraction("pierce", HitBlock(
                CustomMaterialSet.passable,
                true,
                false,
                true,
                0.5
            )) { hitBlock, result, block, hitNormal ->
                result
            },
        )
        fun register(interaction: HitBlockInteraction) {
            interactions.add(interaction)
        }
        val registeredInteraction by lazy { interactions + builtInCollideInteraction }
        val registeredInteractionMap by lazy { registeredInteraction.associateBy { it.name } }

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