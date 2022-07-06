package kr.lostwar.gun.setting

import com.destroystokyo.paper.MaterialSetTag
import org.bukkit.Material

class CustomMaterialSet(
    val name: String,
    val set: Set<Material>
) : Set<Material> by set {

    constructor(name: String, builder: () -> Set<Material>) : this(name, builder())

    companion object {
        private val builtInSets = listOf<CustomMaterialSet>(
            CustomMaterialSet("completelyPassable") {
                listOf(
                    MaterialSetTag.RAILS.values,
                    MaterialSetTag.RAILS.values,
                    MaterialSetTag.RAILS.values,
                    MaterialSetTag.RAILS.values,
                    MaterialSetTag.RAILS.values,
                ).flatten().toSet()
            }
        )
    }

}