package kr.lostwar.vehicle

import kr.lostwar.util.command.CommandUtil.findStartsWithOrContains
import kr.lostwar.util.command.MultiCommand
import kr.lostwar.util.command.OperatorSubCommand
import kr.lostwar.util.command.SubCommand
import kr.lostwar.util.command.SubCommandExecuteData
import kr.lostwar.util.ui.text.colorMessage
import kr.lostwar.util.ui.text.errorMessage
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object VehicleCommand : MultiCommand("andoo", "andoo", aliases = listOf("ad")) {


    private val reloadCommand = object : OperatorSubCommand("reload") {
        override fun SubCommandExecuteData.execute() {
            VehicleEngine.load(true)
        }
    }

    private val spawnCommand = object : OperatorSubCommand("spawn") {
        override fun isExecutable(sender: CommandSender): Boolean {
            return super.isExecutable(sender) && sender is Player
        }
        override fun isSuggestible(sender: CommandSender): Boolean {
            return super.isSuggestible(sender) && sender is Player
        }
        override fun SubCommandExecuteData.execute() {
            val player = sender as? Player ?: return
            if(args.size == 1) {
                player.errorMessage("사용법 : /$label $subCmd <vehicle-id>")
                return
            }
            val key = args[1]
            val vehicleInfo = VehicleInfo.byKey[key]
            if(vehicleInfo == null){
                player.errorMessage("&e${key}&c는 유효하지 않은 무기입니다.")
                return
            }
            val vehicleEntity = vehicleInfo.spawn(player.location)
            player.colorMessage("차량 &e${key}&r(을)를 소환했습니다.")
        }

        override fun SubCommandExecuteData.complete(): List<String>? {
            return when(args.size) {
                2 -> VehicleInfo.byKey.keys.findStartsWithOrContains(args[1])
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
        reloadCommand,
        spawnCommand,
        listCommand,
    )

}