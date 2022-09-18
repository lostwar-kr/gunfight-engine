package kr.lostwar.vehicle

import kr.lostwar.util.ui.text.console
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.collections.ArrayList

class VehiclePlayer(
    player: Player
) {
    var player = player.also { byUUID[it.uniqueId] = this }; private set

    companion object {

        val byUUID = HashMap<UUID, VehiclePlayer>()

        val Player.vehiclePlayer: VehiclePlayer; get() = get(this)
        operator fun get(player: Player): VehiclePlayer {
            return byUUID[player.uniqueId]
                ?. also { if(it.player != player) it.player = player }
                ?: return VehiclePlayer(player)
        }

        @EventHandler
        fun PlayerJoinEvent.onJoin() {
            val player = player.vehiclePlayer
        }

        @EventHandler
        fun PlayerQuitEvent.onQuit() {
            byUUID.remove(player.uniqueId)
        }
    }

    enum class VehicleControl(val flag: Int) {
        FORWARD(0b000001),
        BACK   (0b000010),
        LEFT   (0b000100),
        RIGHT  (0b001000),
        SHIFT  (0b010000),
        SPACE  (0b100000);

        fun matches(flag: Int): Boolean = this.flag and flag != 0
        fun added(flag: Int): Int = flag or this.flag
        fun removed(flag: Int): Int = flag and this.flag.inv()

        companion object {
            val values by lazy { values() }
        }
    }

    private var blockingControls: Int = 0
    fun VehicleControl.isBlocking() = matches(blockingControls)
    fun addBlocking(vararg controls: VehicleControl) {
        for(control in controls) {
            blockingControls = control.added(blockingControls)
        }
    }
    fun removeBlocking(vararg controls: VehicleControl) {
        for(control in controls) {
            blockingControls = control.removed(blockingControls)
        }
    }
    fun blockAll() = addBlocking(*VehicleControl.values)
    fun unblockAll() { blockingControls = 0 }

    var forwardInput = 0f; private set
    var sideInput = 0f; private set
    var isSpace = false; private set
    var isShift = false; private set
    fun updateInput(side: Float, forward: Float, space: Boolean, shift: Boolean) {
        forwardInput =
            if(VehicleControl.FORWARD.isBlocking() && forward > 0
                || VehicleControl.BACK.isBlocking() && forward < 0
            ) 0f
            else forward
        isForward = forwardInput > 0
        isBackward = forwardInput < 0

        sideInput =
            if(VehicleControl.LEFT.isBlocking() && side > 0
                || VehicleControl.RIGHT.isBlocking() && side < 0
            ) 0f
            else side
        isLeft = sideInput > 0
        isRight = sideInput < 0

        isSpace =
            if(VehicleControl.SPACE.isBlocking()) false
            else space
        isShift =
            if(VehicleControl.SHIFT.isBlocking()) false
            else shift
    }

    var isForward = false; private set
    var isBackward = false; private set
    var isLeft = false; private set
    var isRight = false; private set


    var isReseating = false
    var isExiting = false

    private val hotbarSize = 9
    private val air = ItemStack(Material.AIR)
    private val hotbarHolder: ArrayList<ItemStack> = ArrayList<ItemStack>(hotbarSize)
        .also { for(i in 0 until hotbarSize) it.add(air) }

    /**
     * 플레이어가 현재 들고있는 아이템을 핫바 홀더에 저장합니다.
     */
    fun pushHotbarHolder() {
//        console("pushHotbarHolder:")
        (0 until hotbarSize).map { player.inventory.getItem(it) ?: air }
            .forEachIndexed { index, item ->
//                console(" [$index] : storing $item")
                hotbarHolder[index] = item
                player.inventory.setItem(index, null)
            }
    }

    /**
     * 플레이어의 핫바 홀더에서 아이템을 꺼냅니다.
     */
    fun popHotbarHolder() {
//        console("popHotbarHolder:")
        (0 until hotbarSize).forEach { index ->
            val item = hotbarHolder[index]
//            console(" [$index] : taking $item")
            player.inventory.setItem(index, item)
            hotbarHolder[index] = air
        }

    }

}