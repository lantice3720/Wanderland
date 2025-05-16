package kr.lanthanide.wanderland.command

import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.CommandExecutor
import net.minestom.server.command.builder.arguments.ArgumentType


class Give: Command("give") {
    init {
        defaultExecutor = CommandExecutor { sender: CommandSender, context: CommandContext ->
            sender.sendMessage("usage: /give <targetPlayer> <itemstack>|<custom item id>")
        }


        // give <targetPlayer> <itemstack>
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(true)
        val itemArg = ArgumentType.ItemStack("item")
        val customItemIdArg = ArgumentType.String("custom item id")

        addSyntax(CommandExecutor { sender: CommandSender, context: CommandContext ->
            val target = context.get(targetArg).findFirstPlayer(sender)
            val item = context.get(itemArg)

            if (target == null) {
                sender.sendMessage("Could not find player!")
                return@CommandExecutor
            }
            if (item == null) {
                sender.sendMessage("Could not find item!")
                return@CommandExecutor
            }

            target.inventory.addItemStack(item)

        }, targetArg, itemArg)

        addSyntax(CommandExecutor { sender: CommandSender, context: CommandContext ->
            val target = context.get(targetArg).findFirstPlayer(sender)
            val customItemId = context.get(customItemIdArg)

            if (target == null) {
                sender.sendMessage("Could not find player!")
                return@CommandExecutor
            }
            if (customItemId == null) {
                sender.sendMessage("Could not find custom item!")
                return@CommandExecutor
            }

//            val item = ItemFactory.fromId(customItemId)
//            if (item == null) {
//                sender.sendMessage("Could not find custom item!")
//                return@CommandExecutor
//            }

//            target.inventory.addItemStack(item)

        }, targetArg, customItemIdArg)
    }
}