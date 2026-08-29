package com.palordersoftworks.brokenstarsmpmod;

import com.palordersoftworks.brokenstarsmpmod.commands.ImmortalCommand;
import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import com.palordersoftworks.brokenstarsmpmod.listeners.BlockListener;
import com.palordersoftworks.brokenstarsmpmod.listeners.EntityListener;
import com.palordersoftworks.brokenstarsmpmod.listeners.ItemMergeTask;
import com.palordersoftworks.brokenstarsmpmod.listeners.PlayerListener;
import com.palordersoftworks.brokenstarsmpmod.messages.Messages;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;

public class BrokenstarSMPPlugin extends JavaPlugin {
    private static BrokenstarSMPPlugin instance;
    private BukkitTask itemMergeTask;

    @Override
    public void onEnable() {
        instance = this;

        Path configDir = getDataFolder().toPath();
        ConfigManager.setConfigDirectory(configDir);
        Messages.setConfigDirectory(configDir);

        ConfigManager.registerAnnotatedConfigs(ServerRules.class);
        ConfigManager.registerAnnotatedConfigs(UnstableSMPRules.class);

        Messages.initialize();

        Bukkit.getPluginManager().registerEvents(new BlockListener(), this);
        Bukkit.getPluginManager().registerEvents(new EntityListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);

        if (ServerRules.INSTANT_ITEM_MERGE) {
            itemMergeTask = new ItemMergeTask().runTaskTimer(this, 1L, 1L);
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            ConfigManager.registerCommands(event.registrar());
            ImmortalCommand.register(event.registrar());
        });

        getLogger().info("BrokenstarSMP plugin enabled (Paper port)");
    }

    @Override
    public void onDisable() {
        if (itemMergeTask != null) {
            itemMergeTask.cancel();
        }
        ConfigManager.saveAll();
        getLogger().info("BrokenstarSMP plugin disabled");
    }

    public static BrokenstarSMPPlugin getInstance() {
        return instance;
    }
}
