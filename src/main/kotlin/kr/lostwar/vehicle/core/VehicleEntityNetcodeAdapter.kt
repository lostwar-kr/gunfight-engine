package kr.lostwar.vehicle.core

import kr.lostwar.netcode.NetcodeAdapter
import kr.lostwar.util.math.VectorUtil.set
import org.bukkit.entity.Entity
import org.bukkit.util.Vector
import java.util.*

class VehicleEntityNetcodeAdapter(entity: VehicleEntity<*>) : NetcodeAdapter {
    var entity: VehicleEntity<*>? = entity
    override val uniqueId: UUID = entity.uniqueId
    override val lerpStep: Int = 3

    override fun checkValidOnTick(): Boolean {
        val entity = entity
        if(entity == null || entity.isDead) {
            close()
            return false
        }
        return true
    }

    override fun getServerPositionNonAlloc(vector: Vector) {
        val position = entity?.transform?.position ?: return
        vector.set(position)
    }
    override fun setOnMoveBroadcast(consumer: (Entity) -> Unit) {
        entity!!.onMoveBroadcast = consumer
    }

    override fun close() {
        entity = null
    }
}