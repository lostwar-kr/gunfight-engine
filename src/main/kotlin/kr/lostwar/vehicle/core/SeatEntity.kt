package kr.lostwar.vehicle.core

import kr.lostwar.GunfightEngine
import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponPlayer.Companion.weaponPlayer
import kr.lostwar.gun.weapon.event.WeaponShootPrepareEvent
import kr.lostwar.util.ui.text.console
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
import kr.lostwar.vehicle.core.VehicleEntity.Companion.asVehicleEntityOrNull
import kr.lostwar.vehicle.core.VehicleEntity.Companion.onShootPrepare
import kr.lostwar.vehicle.event.VehicleEnterEvent
import kr.lostwar.vehicle.event.VehicleExitEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.util.Vector
import java.lang.Exception

class SeatEntity(
    val index: Int,
    info: VehicleModelInfo,
    entity: ArmorStand,
    vehicle: VehicleEntity<*>,
) : ModelEntity(info, entity, vehicle) {

    override var info: VehicleModelInfo
        get() = super.info
        set(value) {
            super.info = value

            turretEntitiesBySlot.clear()
            turretEntitiesBySlot.putAll(vehicle.modelEntities.filter { it.value.info.turretInfo != null }.values.let {
                HashMap<Int, MutableList<ModelEntity>>().groupBySlotIndex(it)
            })

        }


    val seatInfo; get() = this.info.seatInfo
    val attachedWeapons = seatInfo?.attachedWeapons?.map { it?.instantiate()?.first }?.toMutableList()
    val exitPosition; get() = seatInfo?.let {
        worldTransform.localToWorld(it.exitOffset)
    } ?: Vector()

    var passenger: Player? = null
        set(value) {
            field = value
        }

    private val Player.isValidPassenger: Boolean
        get() = isOnline && !isDead && gameMode != GameMode.SPECTATOR

    fun ride(player: Player): Boolean {
        if(passenger != null) {
            return false
        }
        val vehiclePlayer = player.vehiclePlayer
        // 이전에 타고 있던 차량이 있을 때 하차 처리
        val oldRiding = player.vehicle
        oldRiding?.asVehicleEntityOrNull?.let { oldVehicle ->
            for(entity in oldVehicle.seatEntities){
                if(entity.entityId == oldRiding.entityId) {
                    vehiclePlayer.isReseating = true
                    entity.exit(false)
                    break
                }
            }
        }
        passenger = player
        entity.addPassenger(player)
        vehiclePlayer.isReseating = false
        VehicleEnterEvent(vehicle, player, this).callEvent()

        // 무기 장착 처리
        if(attachedWeapons != null) {
            vehiclePlayer.pushHotbarHolder()
            for((index, item) in attachedWeapons.withIndex()) {
//                console("take from vehicle [$index]: $item")
                player.inventory.setItem(index, item)
            }
            player.inventory.heldItemSlot = 0
            player.weaponPlayer.updateCurrentWeapon()
        }
        return true
    }

    fun exit(teleportExitPosition: Boolean = true): Boolean {
        passenger?.let { player ->

            val vehiclePlayer = player.vehiclePlayer
            vehiclePlayer.isExiting = true
            if(attachedWeapons != null) {
//                console("exit call stack trace ::")
//                try {
//                    throw Exception("stack trace")
//                }catch (e: Exception) {
//                    e.printStackTrace()
//                }
                for(i in attachedWeapons.indices) {
                    val item = player.inventory.getItem(i)
//                    console("storing to vehicle [$i]: $item")
                    // 썼던 거 되돌려놓기
                    attachedWeapons[i] = item
                }
                vehiclePlayer.popHotbarHolder()
                player.weaponPlayer.updateCurrentWeapon()
            }
            if(teleportExitPosition && exitPosition.lengthSquared() > 0.0) {
                val exitLocation = exitPosition
                    .toLocation(vehicle.world)
                    .setDirection(player.location.direction)
                entity.eject()
                Bukkit.getScheduler().runTaskLater(plugin, Runnable { player.teleport(exitLocation) }, 1)
            }else{
                entity.eject()
            }
            vehiclePlayer.isExiting = false

            VehicleExitEvent(vehicle, player, this).callEvent()
        } ?: entity.eject()
        passenger = null
        return true
    }

    private fun MutableMap<Int, MutableList<ModelEntity>>.groupBySlotIndex(entities: Iterable<ModelEntity>) = apply {
        for(model in entities) {
            val turret = model.info.turretInfo ?: continue
            for(index in turret.slotIndexes) {
                getOrPut(index) { ArrayList() }.add(model)
            }
        }
    }
    val turretEntitiesBySlot = vehicle.modelEntities.filter { it.value.info.turretInfo != null }.values.let {
        HashMap<Int, MutableList<ModelEntity>>().groupBySlotIndex(it)
    }
    val turretShootCountBySlot = Array(9) { 0 }
    override fun onShoot(event: WeaponShootPrepareEvent) {
        val player = passenger ?: return
        val slot = player.inventory.heldItemSlot
        val turrets = turretEntitiesBySlot[slot] ?: return
        val size = turrets.size
        val index = turretShootCountBySlot[slot] % size
        val turret = turrets[index]
        turret.onShoot(event)
        turretShootCountBySlot[slot] += 1
    }

}