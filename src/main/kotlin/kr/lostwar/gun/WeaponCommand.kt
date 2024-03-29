package kr.lostwar.gun

import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.command.CommandUtil.findStartsWithOrContains
import kr.lostwar.util.command.MultiCommand
import kr.lostwar.util.command.OperatorSubCommand
import kr.lostwar.util.command.SubCommand
import kr.lostwar.util.command.SubCommandExecuteData
import kr.lostwar.util.ui.text.colorMessage
import kr.lostwar.util.ui.text.errorMessage
import kr.lostwar.vehicle.VehicleEngine
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object WeaponCommand : MultiCommand("fullmetaljacket", "fullmetaljacket", aliases = listOf("fmj", "fm")) {

    init {
        permission = "fmj.command"
    }

    override fun isSuggestible(sender: CommandSender): Boolean {
        return sender.isOp
    }

    override fun isExecutable(sender: CommandSender): Boolean {
        return sender.isOp
    }

    private val debugCommand = object : OperatorSubCommand("debug") {
        override fun SubCommandExecuteData.execute() {
            GunEngine.isDebugging = !GunEngine.isDebugging
        }
    }

    private val reloadCommand = object : OperatorSubCommand("reload") {
        override fun SubCommandExecuteData.execute() {
            GunEngine.load(true)
        }
    }

    private val getCommand = object : OperatorSubCommand("get") {
        override fun isExecutable(sender: CommandSender): Boolean {
            return super.isExecutable(sender) && sender is Player
        }
        override fun isSuggestible(sender: CommandSender): Boolean {
            return super.isSuggestible(sender) && sender is Player
        }
        override fun SubCommandExecuteData.execute() {
            val player = sender as? Player ?: return
            if(args.size == 1) {
                player.errorMessage("사용법 : /$label $subCmd <weapon-id>")
                return
            }
            val key = args[1]
            val weaponType = WeaponType[key]
            if(weaponType == null){
                player.errorMessage("&e${key}&c는 유효하지 않은 무기입니다.")
                return
            }
            val (item, weapon) = weaponType.instantiate()
            player.colorMessage("무기 &e${weapon}&r을 지급했습니다.")
            player.inventory.addItem(item)
        }

        override fun SubCommandExecuteData.complete(): List<String>? {
            return when(args.size) {
                2 -> WeaponType.weaponsByKey.keys.findStartsWithOrContains(args[1])
                else -> null
            }
        }
    }

    private val listCommand = object : OperatorSubCommand("list") {
        override fun SubCommandExecuteData.execute() {
            sender.colorMessage("무기 목록: &7[${WeaponType.weaponsByKey.size}개]")
            for ((key, weaponType) in WeaponType.weaponsByKey) {
                sender.colorMessage("&e- $key&6")
            }
        }
    }

    override val subCommands: List<SubCommand> = listOf(
        debugCommand,
        reloadCommand,
        getCommand,
        listCommand,
    )
}