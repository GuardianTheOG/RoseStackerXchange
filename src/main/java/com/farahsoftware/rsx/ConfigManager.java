package com.farahsoftware.rsx;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.ChatColor;

import java.util.*;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration cfg;
    private FileConfiguration guiCfg;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
        this.guiCfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.File(plugin.getDataFolder(), "gui.yml"));
    }

    public FileConfiguration getRawConfig() { return cfg; }
    public FileConfiguration getGuiConfig() { return guiCfg; }

    /* GUI helpers */
    public String getGuiTitle() { return ChatColor.translateAlternateColorCodes('&', guiCfg.getString("title", "&aExchange Spawners")); }
    public int getGuiRows() { return Math.max(1, Math.min(6, guiCfg.getInt("rows", 3))); }
    public int getCancelSlot() { return guiCfg.getInt("cancel-slot", 0); }
    public int getConfirmSlot() { return guiCfg.getInt("confirm-slot", 8); }
    public Material getFillerMaterial() {
        try {
            return Material.valueOf(guiCfg.getString("filler.material", "BLACK_STAINED_GLASS_PANE").toUpperCase());
        } catch (Exception e) {
            return Material.BLACK_STAINED_GLASS_PANE;
        }
    }
    public String getFillerName() { return ChatColor.translateAlternateColorCodes('&', guiCfg.getString("filler.name", "&r")); }
    public List<Integer> getInputSlotCandidates() {
        List<Integer> list = guiCfg.getIntegerList("input-slot-candidates");
        if (list == null || list.isEmpty()) return Arrays.asList(10,11,12,13,14,15);
        return list;
    }
    public String getItemName(String path) { return ChatColor.translateAlternateColorCodes('&', guiCfg.getString(path + ".name", "")); }
    public Material getItemMaterial(String path, Material fallback) {
        try {
            return Material.valueOf(guiCfg.getString(path + ".material", fallback.name()).toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Return a map of mobKey -> rate. Safe reads of config.yml.
     * Format supported:
     *
     * mobs:
     *   ZOMBIE:
     *     allow: true
     *     rate: 1
     *
     * or simple:
     * mobs:
     *   ZOMBIE: 1
     */
    public Map<String, Integer> getMobMap() {
        Map<String,Integer> out = new LinkedHashMap<>();
        ConfigurationSection mobs = cfg.getConfigurationSection("mobs");
        if (mobs == null) return out;
        List<String> keys = new ArrayList<>(mobs.getKeys(false));
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        for (String key : keys) {
            String u = key.toUpperCase(Locale.ROOT);
            int rate = 1;
            boolean allow = true;
            if (mobs.isInt(key)) {
                rate = mobs.getInt(key, 1);
            } else {
                ConfigurationSection sec = mobs.getConfigurationSection(key);
                if (sec != null) {
                    rate = sec.getInt("rate", sec.getInt("value", 1));
                    allow = sec.getBoolean("allow", true);
                }
            }
            if (allow) out.put(u, Math.max(1, rate));
        }
        return out;
    }

    public boolean hasMob(String mobKey) {
        return cfg.contains("mobs." + mobKey);
    }

    public void setMobRate(String mobKey, int rate) {
        cfg.set("mobs." + mobKey, rate);
        save();
    }

    public void addMobIfMissing(String mobKey) {
        if (!hasMob(mobKey)) {
            setMobRate(mobKey, 1);
            plugin.getLogger().info("[RSX] Added missing mob to config: " + mobKey + " (default rate=1)");
        }
    }

    public void save() { plugin.saveConfig(); }
}
