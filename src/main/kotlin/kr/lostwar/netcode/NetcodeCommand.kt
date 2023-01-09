package kr.lostwar.netcode

import kr.lostwar.netcode.EntityNetcodeFixer.Companion.netcodeFixerOrCreate
import kr.lostwar.netcode.EntityNetcodeFixer.Companion.useNetcodeFixer
import kr.lostwar.netcode.EntityNetcodeFixer.Companion.useNetcodeFixerAtTickEnd
import kr.lostwar.util.command.MultiCommand
import kr.lostwar.util.command.OperatorSubCommand
import kr.lostwar.util.command.SubCommand
import kr.lostwar.util.command.SubCommandExecuteData
import kr.lostwar.util.ui.text.colorMessage
import kr.lostwar.util.ui.text.errorMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object NetcodeCommand : MultiCommand("netcode", "fullmetaljacket") {

    private val toggleNetcodeCommand = object : OperatorSubCommand("toggle") {
        override fun SubCommandExecuteData.execute() {
            useNetcodeFixer = !useNetcodeFixer
            sender.colorMessage("new use state: &e${useNetcodeFixer}")
        }
    }
    private val toggleNetcodeAtTickEndCommand = object : OperatorSubCommand("toggle-tickend") {
        override fun SubCommandExecuteData.execute() {
            useNetcodeFixerAtTickEnd = !useNetcodeFixerAtTickEnd
            sender.colorMessage("new use tickend state: &e${useNetcodeFixerAtTickEnd}")
        }
    }

    private val netcodeAdaptCommand = object : OperatorSubCommand("adapt") {
        override fun SubCommandExecuteData.execute() {
            val player = sender as? Player
            if(player == null){
                sender.errorMessage("can only use player")
                return
            }
            if(args.size <= 1){
                sender.errorMessage("Usage: /$label $subCmd <player>")
                return
            }
            val playerName = args[1]
            val targetPlayer = Bukkit.getPlayer(playerName)
            if(targetPlayer == null){
                sender.errorMessage("&e${playerName}&c is not player")
                return
            }
            targetPlayer.netcodeFixerOrCreate
        }
    }

    override val subCommands: List<SubCommand> = listOf(
        toggleNetcodeCommand,
        toggleNetcodeAtTickEndCommand,
        netcodeAdaptCommand,
    )

}