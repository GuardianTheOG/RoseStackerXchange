package com.farahsoftware.rsx;

import dev.rosewood.rosestacker.api.RoseStackerAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class RoseStackerXchange extends JavaPlugin {

    private static RoseStackerXchange instance;
    private ConfigManager configManager;
    private SpawnerManager spawnerManager;
    private GuiManager guiManager;
    private ExchangeManager exchangeManager;

    private RoseStackerAPI rsApi;

    @Override
    public void onEnable() {
        instance = this;

        // Save default resources
        saveDefaultConfig();
        saveResource("gui.yml", false);

        // Ensure RoseStacker is present (depend ensures load order, but double-check)
        if (!Bukkit.getPluginManager().isPluginEnabled("RoseStacker")) {
            getLogger().severe("RoseStacker not found. This plugin requires RoseStacker to function. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Get API instance (docs example). If null for any reason, disable.
        try {
            this.rsApi = RoseStackerAPI.getInstance();
        } catch (NoClassDefFoundError | Exception ex) {
            rsApi = null;
        }
        if (this.rsApi == null) {
            getLogger().severe("Could not obtain RoseStacker API instance. Make sure RoseStacker is up-to-date. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Init managers
        this.configManager = new ConfigManager(this);
        this.spawnerManager = new SpawnerManager(this, rsApi);
        this.guiManager = new GuiManager(this);
        this.exchangeManager = new ExchangeManager(this, spawnerManager, configManager);

        // Commands & listeners
        getCommand("rsx").setExecutor(new RSXCommand(this));
        getCommand("rsx").setTabCompleter(new RSXTabCompleter(this));
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        // Populate mobs in config from RoseStacker
        try {
            spawnerManager.populateMobsInConfig();
        } catch (Exception ex) {
            getLogger().warning("[RSX] populateMobsInConfig() failed: " + ex.getMessage());
        }

        getLogger().info("RoseStackerXchange enabled (RoseStacker API OK)");
    }

    @Override
    public void onDisable() {
        getLogger().info("RoseStackerXchange disabled");
    }

    public static RoseStackerXchange get() { return instance; }

    public ConfigManager getConfigManager() { return configManager; }
    public SpawnerManager getSpawnerManager() { return spawnerManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public ExchangeManager getExchangeManager() { return exchangeManager; }
    public RoseStackerAPI getRsApi() { return rsApi; }
}
