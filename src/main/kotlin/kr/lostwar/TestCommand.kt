package kr.lostwar

import kr.lostwar.util.command.MultiCommand
import kr.lostwar.util.command.OperatorSubCommand
import kr.lostwar.util.command.SubCommand
import kr.lostwar.util.command.SubCommandExecuteData
import kr.lostwar.util.ui.text.errorMessage
import org.bukkit.Material
import org.bukkit.entity.Player

object TestCommand : MultiCommand("test", "gunfight-engine") {


    private val setCooldownCommand = object : OperatorSubCommand("set-cooldown") {
        override fun SubCommandExecuteData.execute() {
            val player = sender as? Player
            if(player == null){
                sender.errorMessage("can only use player")
                return
            }
            if(args.size == 1){
                sender.errorMessage("Usage: /$label $subCmd <ticks> [material]")
                return
            }
            val rawTicks = args[1]
            val ticks = rawTicks.toIntOrNull()
            if(ticks == null){
                sender.errorMessage("&e${rawTicks}&c is not an integer")
                return
            }
            val material: Material
            if(args.size == 2) {
                val item = player.inventory.itemInMainHand
                if(!item.type.isItem) {
                    sender.errorMessage("&e${item.type}&c is not an item")
                    return
                }
                material = item.type
            }else{
                val rawType = args[2]
                val type = Material.getMaterial(rawType)
                if(type == null || !type.isItem) {
                    sender.errorMessage("&e${rawType}&c is invalid item")
                    return
                }
                material = type
            }
            player.setCooldown(material, ticks)

        }
    }

    override val subCommands: List<SubCommand> = listOf(
        setCooldownCommand,
    )

}