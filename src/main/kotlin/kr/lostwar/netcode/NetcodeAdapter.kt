package kr.lostwar.netcode

import org.bukkit.entity.Entity
import org.bukkit.util.Vector
import java.util.UUID

interface NetcodeAdapter {

    val uniqueId: UUID
    fun setOnMoveBroadcast(consumer: (Entity) -> Unit)
    fun getServerPositionNonAlloc(vector: Vector)
    val serverPosition: Vector; get() {
        val vector = Vector()
        getServerPositionNonAlloc(vector)
        return vector
    }
    val lerpStep: Int

    fun checkValidOnTick(): Boolean
    fun close()
}