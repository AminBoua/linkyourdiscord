package com.linkyourdiscord;

import org.bukkit.plugin.java.JavaPlugin;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class LinkYourDiscord extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("LinkYourDiscord has been enabled!");

        saveDefaultConfig(); // Save config.yml if it doesn't exist
        AccountManager.createLinkedAccountsFile(this); // Ensure linked_accounts.json file exists

        // Read bot token and guild ID from config.yml
        String botToken = getConfig().getString("bot-token");
        String guildId = getConfig().getString("guild-id");

        if (botToken == null || botToken.isEmpty() || botToken.equals("YOUR_DISCORD_BOT_TOKEN")) {
            getLogger().severe("Bot token is missing from config.yml! Please provide a valid bot token.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (guildId == null || guildId.isEmpty() || guildId.equals("YOUR_GUILD_ID")) {
            getLogger().severe("Guild ID is missing from config.yml! Please provide a valid Guild ID.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register Minecraft commands
        getCommand("discord").setExecutor(new DiscordCommand(this));
        getCommand("unlink").setExecutor(new DiscordCommand(this));

        try {
            JDABuilder jdaBuilder = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordListener(this, guildId));
            jdaBuilder.build();
        } catch (Exception e) {
            getLogger().severe("Failed to start Discord bot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("LinkYourDiscord has been disabled!");
    }
}