package com.farahsoftware.rsx;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class RSXCommand implements CommandExecutor {

    private final RoseStackerXchange plugin;

    public RSXCommand(RoseStackerXchange plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("exchange")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players may run this command.");
                return true;
            }
            Player p = (Player) sender;
            p.openInventory(plugin.getGuiManager().buildSelectionPage(0));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rsx.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            plugin.getConfigManager().reload();
            sender.sendMessage(ChatColor.GREEN + "RSX reloaded.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /rsx exchange | reload");
        return true;
    }
}
