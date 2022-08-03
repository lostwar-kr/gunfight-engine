package kr.lostwar.util

import com.destroystokyo.paper.MaterialSetTag
import com.destroystokyo.paper.MaterialTags
import org.bukkit.Material

class CustomMaterialSet(
    val name: String,
    val set: Set<Material>
) : Set<Material> by set {

    constructor(name: String, builder: () -> Set<Material>) : this(name, builder())

    companion object {
        val airs = CustomMaterialSet("air", setOf(
            Material.AIR, Material.VOID_AIR, Material.CAVE_AIR, Material.LIGHT)
        )
        val fluids = CustomMaterialSet("fluids", setOf(Material.WATER, Material.LAVA))
        val plants = CustomMaterialSet("plants") {
            listOf(
                MaterialSetTag.FLOWERS.values,
                MaterialSetTag.CORAL_PLANTS.values,
                setOf(
                    Material.SEA_PICKLE, // 불우렁쉥이
                    Material.BEETROOTS,
                    Material.FERN, Material.LARGE_FERN,
                    Material.GRASS, Material.TALL_GRASS,
                    Material.DEAD_BUSH,
                    Material.BROWN_MUSHROOM, Material.RED_MUSHROOM,
                    Material.ROSE_BUSH, Material.WITHER_ROSE,
                    Material.DANDELION,

                )
            ).flatten().toSet()
        }
        val glasses = CustomMaterialSet("glasses") {
            listOf(
                MaterialTags.GLASS.values,
                MaterialTags.GLASS_PANES.values,
                MaterialTags.STAINED_GLASS.values,
                MaterialTags.STAINED_GLASS_PANES.values,
                MaterialSetTag.FLOWER_POTS.values,
                setOf(
                    Material.ICE, Material.PACKED_ICE,
                    Material.GLOWSTONE, Material.SEA_LANTERN,
                    Material.REDSTONE_LAMP,
                    Material.TINTED_GLASS,
                )
            ).flatten().toSet()
        }
        val completelyPassable = CustomMaterialSet("completely_passable") {
            listOf(
                MaterialSetTag.RAILS.values,
                MaterialSetTag.SIGNS.values,
                MaterialSetTag.STANDING_SIGNS.values,
//                MaterialSetTag.LEAVES.values,
                MaterialSetTag.WALL_SIGNS.values,
                MaterialSetTag.WOOL_CARPETS.values,
                MaterialSetTag.PRESSURE_PLATES.values,
                MaterialSetTag.BANNERS.values,
                MaterialSetTag.WOODEN_FENCES.values,
                MaterialSetTag.FLOWER_POTS.values,
                MaterialSetTag.BUTTONS.values,
                airs,
                fluids,
                plants,
                setOf(
                    Material.FIRE,
                    Material.SNOW,
                    Material.SEA_PICKLE,
                    Material.TORCH, Material.WALL_TORCH,
                    Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH,
                    Material.SOUL_TORCH, Material.SOUL_WALL_TORCH,
                    Material.LADDER,
                )
            ).flatten().toSet()
        }
        val passable = CustomMaterialSet("passable") {
            listOf(
                completelyPassable,
                MaterialSetTag.WOODEN_FENCES.values,
                MaterialSetTag.FENCE_GATES.values,
                MaterialSetTag.BEDS.values,
                MaterialSetTag.WOODEN_DOORS.values,
                MaterialSetTag.WOODEN_SLABS.values,
                MaterialSetTag.WOODEN_STAIRS.values,
                MaterialSetTag.WOODEN_TRAPDOORS.values,
                setOf(
                    Material.DIRT, Material.GRASS_BLOCK,
                    Material.RED_SAND,
                    // TODO RED_SANDSTONE ? or not
                    Material.DRIED_KELP_BLOCK,
                    Material.BARREL,
                    Material.IRON_BARS,
                )
            ).flatten().toSet()
        }
        private val builtInSets = listOf<CustomMaterialSet>(
            airs,
            fluids,
            plants,
            completelyPassable,
        )
    }

}