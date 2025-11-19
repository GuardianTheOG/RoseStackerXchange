package com.farahsoftware.rsx;

import org.bukkit.command.*;
import java.util.*;

public class RSXTabCompleter implements TabCompleter {
    private final RoseStackerXchange plugin;
    public RSXTabCompleter(RoseStackerXchange plugin) { this.plugin = plugin; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList("exchange", "reload"));
            if (!sender.hasPermission("rsx.admin")) return Collections.singletonList("exchange");
            return filter(opts, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        if (prefix == null || prefix.isEmpty()) return list;
        List<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase().startsWith(prefix.toLowerCase())) out.add(s);
        return out;
    }
}
