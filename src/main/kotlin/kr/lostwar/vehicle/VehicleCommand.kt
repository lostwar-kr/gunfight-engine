package kr.lostwar.vehicle

import kr.lostwar.util.command.CommandUtil.findStartsWithOrContains
import kr.lostwar.util.command.MultiCommand
import kr.lostwar.util.command.OperatorSubCommand
import kr.lostwar.util.command.SubCommand
import kr.lostwar.util.command.SubCommandExecuteData
import kr.lostwar.util.ui.text.colorMessage
import kr.lostwar.util.ui.text.errorMessage
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object VehicleCommand : MultiCommand("andoo", "andoo", aliases = listOf("ad")) {

    private val debugCommand = object : OperatorSubCommand("debug") {
        override fun SubCommandExecuteData.execute() {
            VehicleEngine.isDebugging = !VehicleEngine.isDebugging
        }
    }

    private val reloadCommand = object : OperatorSubCommand("reload") {
        override fun SubCommandExecuteData.execute() {
            VehicleEngine.load(true)
        }
    }

    private val spawnCommand = object : OperatorSubCommand("spawn") {
        override fun SubCommandExecuteData.execute() {
            val vehicleInfo: VehicleInfo
            val location: Location
            val key: String
            if(sender is Player) {
                val player = sender as? Player ?: return
                location = player.location
                if(args.size == 1) {
                    player.errorMessage("사용법 : /$label $subCmd <vehicle-id>")
                    return
                }
                key = args[1]
                val info = VehicleInfo.byKey[key]
                if(info == null){
                    player.errorMessage("&e${key}&c는 유효하지 않은 차량입니다.")
                    return
                }
                vehicleInfo = info
            }else{
                if(args.size == 1) {
                    sender.errorMessage("사용법 : /$label $subCmd <vehicle-id> <player>")
                    return
                }
                key = args[1]
                val info = VehicleInfo.byKey[key]
                if(info == null){
                    sender.errorMessage("&e${key}&c는 유효하지 않은 차량입니다.")
                    return
                }
                vehicleInfo = info
                if(args.size == 2) {
                    sender.errorMessage("사용법 : /$label $subCmd $key <player>")
                    return
                }
                val rawPlayer = args[2]
                val targetPlayer = Bukkit.getPlayer(rawPlayer)
                if(targetPlayer == null) {
                    sender.errorMessage("&e${rawPlayer}&c는 유효하지 않은 플레이어입니다.")
                    return
                }
                location = targetPlayer.location
            }
            val vehicleEntity = vehicleInfo.spawn(location)
            sender.colorMessage("차량 &e${key}&r(을)를 소환했습니다.")
        }

        override fun SubCommandExecuteData.complete(): List<String>? {
            return when(args.size) {
                2 -> VehicleInfo.byKey.keys.findStartsWithOrContains(args[1])
                3 -> Bukkit.getOnlinePlayers().map { it.name }.findStartsWithOrContains(args[2])
                else -> null
            }
        }
    }

    private val listCommand = object : OperatorSubCommand("list") {
        override fun SubCommandExecuteData.execute() {
            sender.colorMessage("차량 목록: &7[${VehicleInfo.byKey.size}개]")
            for ((key, _) in VehicleInfo.byKey) {
                sender.colorMessage("&e- $key&6")
            }
        }
    }

    override val subCommands: List<SubCommand> = listOf(
        debugCommand,
        reloadCommand,
        spawnCommand,
        listCommand,
    )

}