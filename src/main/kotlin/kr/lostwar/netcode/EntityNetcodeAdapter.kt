package kr.lostwar.netcode

import kr.lostwar.util.math.VectorUtil.set
import kr.lostwar.util.nms.NMSUtil.nmsEntity
import kr.lostwar.util.nms.UnsafeNMSUtil.setOnMoveBroadcast
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.*

class EntityNetcodeAdapter(entity: Entity) : NetcodeAdapter {

    var entity: Entity? = entity
    override val uniqueId: UUID = entity.uniqueId
    override val lerpStep: Int = when(entity.type) {
        EntityType.MINECART,
        EntityType.MINECART_CHEST,
        EntityType.MINECART_COMMAND,
        EntityType.MINECART_FURNACE,
        EntityType.MINECART_HOPPER,
        EntityType.MINECART_TNT,
        EntityType.MINECART_MOB_SPAWNER -> 5
        else -> 3
    }

    override fun checkValidOnTick(): Boolean {
        val entity = entity
        if(entity == null || !entity.isValid) {
            close()
            return false
        }
        return true
    }

    private val dummyLocation = Location(null, 0.0, 0.0, 0.0)
//    private val dummyVector = Vector(0, 0, 0)
    override fun getServerPositionNonAlloc(vector: Vector) {
        val location = entity?.getLocation(dummyLocation) ?: return
        vector.set(location)
    }
//    override val serverPosition: Vector?; get() = entity?.getLocation(dummyLocation)?.let { dummyVector.set(it) }
//    override val serverPosition: Vector?; get() = entity?.location?.toVector()

//    private var onMoveBroadcast: (Entity) -> Unit = {}
    override fun setOnMoveBroadcast(consumer: (Entity) -> Unit) {
        entity!!.setOnMoveBroadcast(consumer)
//        onMoveBroadcast = consumer
    }

    override fun close() {
        entity = null
    }

}