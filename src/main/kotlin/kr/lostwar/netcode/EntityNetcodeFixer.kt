package kr.lostwar.netcode

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import com.destroystokyo.paper.event.server.ServerTickStartEvent
import kr.lostwar.gun.GunEngine
import kr.lostwar.util.math.VectorUtil.set
import kr.lostwar.vehicle.util.ExtraUtil.getOutline
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.vehicle.VehicleCreateEvent
import org.bukkit.util.Vector
import java.util.UUID

class EntityNetcodeFixer(val adapter: NetcodeAdapter) {

    constructor(entity: Entity) : this(EntityNetcodeAdapter(entity))

    private var isValid = true
    init {
        map[adapter.uniqueId] = this
        adapter.setOnMoveBroadcast {
            val position = adapter.serverPosition
            clientPosition.set(position)
            lerpSteps = adapter.lerpStep
            delay = 0
        }
    }

    private val clientPosition: Vector = adapter.serverPosition
    private val renderPosition: Vector = clientPosition.clone()
    val expectClientPosition; get() = renderPosition.clone()
    fun getExpectClientPositionNonAlloc(vector: Vector) {
        vector.set(renderPosition)
    }
    val offset; get() = expectClientPosition.subtract(adapter.serverPosition)
    private val dummyVector by lazy { Vector() }
    fun getOffsetNonAlloc(vector: Vector) {
        getExpectClientPositionNonAlloc(vector)
        adapter.getServerPositionNonAlloc(dummyVector)
        vector.subtract(dummyVector)
    }
    private var delay = 0
    private var lerpSteps = 0
    private fun tick() {
        if(!isValid || !adapter.checkValidOnTick()) {
            close()
            return
        }
        if(delay > 0) {
            --delay
            return
        }
        if(lerpSteps > 0) {
            val lerpStepsInDouble = lerpSteps.toDouble()
            renderPosition.set(
                renderPosition.x + (clientPosition.x - renderPosition.x) / lerpStepsInDouble,
                renderPosition.y + (clientPosition.y - renderPosition.y) / lerpStepsInDouble,
                renderPosition.z + (clientPosition.z - renderPosition.z) / lerpStepsInDouble,
            )
            --lerpSteps
        }
    }

    private fun lateDebugTick() {
        if(GunEngine.isDebugging) {
            if(adapter is EntityNetcodeAdapter) {
                val boundingBox = adapter.entity?.boundingBox
                    ?.also { bb ->
                        // 차량 있으면 차량의 오프셋을 따라감
                        adapter.entity?.vehicle?.netcodeFixer?.let { bb.shift(it.offset) }
                            ?:  bb.shift(offset)
                    }
                val outline = boundingBox?.getOutline(4) ?: emptyList()
                Bukkit.getOnlinePlayers().forEach { p ->
                    outline.forEach { v ->
                        val location = v.toLocation(p.world)
                        p.spawnParticle(Particle.BUBBLE_COLUMN_UP, location, 1, 0.0, 0.0, 0.0, 0.0)
                    }
                }
//                    DrawUtil.drawPoints(
//                        boundingBox?.getOutline(4) ?: emptyList(),
//                        testColor,
//                    )
            }
            /*
            else if(adapter is VehicleEntityNetcodeAdapter) {
                adapter.entity?.kinematicEntities?.values?.forEach { entity ->
                    val boundingBox = entity.boundingBox.shift(offset)
                    if(boundingBox.volume <= 0.0) return@forEach
                    val outline = boundingBox.getOutline(4)
                    Bukkit.getOnlinePlayers().forEach { p ->
                        outline.forEach { v ->
                            val location = v.toLocation(p.world)
                            p.spawnParticle(Particle.BUBBLE_COLUMN_UP, location, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                }
            }

             */
            else{
                world.spawnParticle(Particle.HEART, renderPosition.toLocation(world).add(0.0, 1.0 + 1.5, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
            }
            world.spawnParticle(Particle.CRIT, clientPosition.toLocation(world).add(0.0, 1.0 + 2.5, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
        }
    }


    private fun close() {
        isValid = false
        adapter.close()
    }

    companion object : Listener {
        var useNetcodeFixer = true
        var useNetcodeFixerAtTickEnd = true

        private val testColor = DustOptions(Color.fromRGB(0xFFFFFF), 0.5f)
        val world by lazy { Bukkit.getWorlds()[0] }
        private val map = HashMap<UUID, EntityNetcodeFixer>()

        val Entity.netcodeFixer: EntityNetcodeFixer?
            get() = map[uniqueId]
        val Entity.netcodeFixerOrCreate: EntityNetcodeFixer
            get() = map.getOrPut(uniqueId) { EntityNetcodeFixer(this) }
        fun Entity.forceBindNetcodeFixer(netcodeFixer: EntityNetcodeFixer) = forceBind(uniqueId, netcodeFixer)
        fun forceBind(uniqueId: UUID, netcodeFixer: EntityNetcodeFixer) {
            map[uniqueId] = netcodeFixer
        }
        fun Entity.unbindNetcodeFixer() = unbind(uniqueId)
        fun unbind(uniqueId: UUID) {
            map.remove(uniqueId)
        }

        // 플레이어 접속 시 넷코드 자동으로 달아줌
        @EventHandler fun PlayerJoinEvent.onJoin() {
            player.netcodeFixerOrCreate
        }
        @EventHandler fun PlayerQuitEvent.onQuit() {
            player.netcodeFixer?.close()
            unbind(player.uniqueId)
        }

        // 엔티티 소환 시 엔티티에 fixer 달아두기
        private val entityTypes = hashSetOf(
            EntityType.ZOMBIE,
        )
        @EventHandler
        fun EntitySpawnEvent.onEntitySpawn() {
            if(entity.type !in entityTypes) return
            this.entity.netcodeFixerOrCreate
        }

        private val vehicleTypes = hashSetOf(
            EntityType.MINECART,
            EntityType.MINECART_TNT,
            EntityType.MINECART_HOPPER,
            EntityType.MINECART_CHEST,
            EntityType.MINECART_FURNACE,
            EntityType.MINECART_COMMAND,
            EntityType.MINECART_MOB_SPAWNER,
        )
        @EventHandler
        fun VehicleCreateEvent.onVehicleCreate() {
            if(vehicle.type !in vehicleTypes) return
            vehicle.netcodeFixerOrCreate
        }

        @EventHandler
        fun ServerTickStartEvent.onTickStart() {
            if(useNetcodeFixerAtTickEnd) {
                return
            }
            tick()
        }
        @EventHandler
        fun ServerTickEndEvent.onTickEnd() {
            if(!useNetcodeFixerAtTickEnd) {
                return
            }
            tick()
        }
        private fun tick() {
            map.forEach { (_, fixer) -> fixer.tick() }
            map.entries.removeAll { !it.value.isValid }

            if(GunEngine.isDebugging) {
                map.forEach { (_, fixer) -> fixer.lateDebugTick() }
            }
        }
    }

}