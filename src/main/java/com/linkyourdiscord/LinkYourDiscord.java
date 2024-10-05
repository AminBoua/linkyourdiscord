package com.linkyourdiscord;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class LinkYourDiscord extends JavaPlugin {
    public HashMap<String, UUID> pendingVerifications = new HashMap<>(); // Code -> Player UUID

    @Override
    public void onEnable() {
        getLogger().info("LinkYourDiscord has been enabled!");

        saveDefaultConfig(); // Save config.yml if it doesn't exist
        createLinkedAccountsFile(); // Ensure the linked_accounts.json file exists

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

        // Register Minecraft commands /discord and /unlink
        getCommand("discord").setExecutor(new DiscordCommand(this));
        getCommand("unlink").setExecutor(new DiscordCommand(this));

        try {
            // Initialize the Discord bot with the token from config.yml
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

    // Ensure linked_accounts.json exists
    public void createLinkedAccountsFile() {
        try {
            File file = new File(getDataFolder(), "linked_accounts.json");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
                // Initialize with an empty JSON object
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write("{}");
                }
            }
        } catch (IOException e) {
            getLogger().severe("Failed to create linked_accounts.json file: " + e.getMessage());
        }
    }

    // Generate a unique code for the player and associate it with their UUID
    public String generateVerificationCode(UUID playerUUID) {
        String code = UUID.randomUUID().toString().substring(0, 8); // Generate a short unique code
        pendingVerifications.put(code, playerUUID); // Store code -> player UUID mapping
        return code;
    }

    // Check if a player is already linked to a Discord account
    public boolean isPlayerLinked(UUID playerUUID) {
        JSONParser parser = new JSONParser();
        try (FileReader reader = new FileReader(getDataFolder() + "/linked_accounts.json")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            return jsonObject.containsKey(playerUUID.toString());
        } catch (IOException | ParseException e) {
            return false;
        }
    }

    // Command for generating the linking code and unlinking in Minecraft
    public class DiscordCommand implements CommandExecutor {

        private final LinkYourDiscord plugin;

        public DiscordCommand(LinkYourDiscord plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            UUID playerUUID = player.getUniqueId();

            FileConfiguration config = plugin.getConfig();

            if (label.equalsIgnoreCase("discord")) {
                // Check if the player is already linked
                if (plugin.isPlayerLinked(playerUUID)) {
                    String alreadyLinkedMessage = config.getString("messages.already-linked");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyLinkedMessage.replace("%player%", player.getName())));
                    return true;
                }

                // Create the clickable URL text
                String clickableText = config.getString("messages.discord-url-text");
                String discordUrl = config.getString("messages.discord-url");

                TextComponent clickableMessage = new TextComponent(ChatColor.translateAlternateColorCodes('&', clickableText));
                clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, discordUrl));

                // Send clickable message to player (before link message)
                player.spigot().sendMessage(clickableMessage);

                // Generate and send link message after clickable URL
                String code = plugin.generateVerificationCode(playerUUID);
                String message = config.getString("messages.link-message");

                // Send the customized link message to the player
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("%code%", code).replace("%player%", player.getName())));

                return true;

            } else if (label.equalsIgnoreCase("unlink")) {
                // Check permission
                if (!player.hasPermission("linkyourdiscord.admin")) {
                    player.sendMessage("You do not have permission to perform this action.");
                    return true;
                }

                // Ensure an argument (IGN) is passed
                if (args.length == 0) {
                    player.sendMessage("Usage: /unlink <player IGN>");
                    return true;
                }

                String playerName = args[0]; // The player's IGN

                // Remove linked account from JSON
                JSONParser parser = new JSONParser();
                JSONObject jsonObject;

                try (FileReader reader = new FileReader(plugin.getDataFolder() + "/linked_accounts.json")) {
                    jsonObject = (JSONObject) parser.parse(reader);
                } catch (IOException | ParseException e) {
                    player.sendMessage("An error occurred while retrieving the data.");
                    return true;
                }

                // Search for the playerName
                boolean found = false;
                for (Object key : jsonObject.keySet()) {
                    JSONObject playerData = (JSONObject) jsonObject.get(key);
                    if (playerData.get("playerName").equals(playerName)) {
                        jsonObject.remove(key); // Remove the entry
                        found = true;
                        break;
                    }
                }

                if (found) {
                    // Save updated JSON data
                    try (FileWriter file = new FileWriter(plugin.getDataFolder() + "/linked_accounts.json")) {
                        file.write(jsonObject.toJSONString());
                        file.flush();
                    } catch (IOException e) {
                        player.sendMessage("Failed to unlink the account.");
                        return true;
                    }

                    player.sendMessage("Successfully unlinked the account for player: " + playerName);
                } else {
                    player.sendMessage("No linked account found for player: " + playerName);
                }
            }

            return true;
        }
    }

    // Listener for Discord messages and slash commands
    public class DiscordListener extends ListenerAdapter {
        private final LinkYourDiscord plugin;
        private final String guildId;

        public DiscordListener(LinkYourDiscord plugin, String guildId) {
            this.plugin = plugin;
            this.guildId = guildId;
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            if (event.getName().equals("link")) {
                String code = event.getOption("code").getAsString();

                // Check if the code is valid
                UUID playerUUID = plugin.pendingVerifications.get(code);
                if (playerUUID == null) {
                    event.reply("Invalid code. Please check your code and try again.").setEphemeral(true).queue();
                    return;
                }

                // Remove the verification code from pending verifications
                plugin.pendingVerifications.remove(code);

                // Link the Discord user to the Minecraft account
                JSONParser parser = new JSONParser();
                JSONObject jsonObject;

                try (FileReader reader = new FileReader(plugin.getDataFolder() + "/linked_accounts.json")) {
                    jsonObject = (JSONObject) parser.parse(reader);
                } catch (IOException | ParseException e) {
                    event.reply("An error occurred while processing your request.").queue();
                    plugin.getLogger().severe("Error reading linked_accounts.json: " + e.getMessage());
                    return;
                }

                Player player = plugin.getServer().getPlayer(playerUUID);
                if (player == null) {
                    event.reply("Player not found or is not online. Please ensure the player is online.").queue();
                    return;
                }

                // Link the Discord account with Minecraft account
                JSONObject playerData = new JSONObject();
                playerData.put("playerName", player.getName());
                playerData.put("discordId", event.getUser().getId());

                jsonObject.put(playerUUID.toString(), playerData);

                try (FileWriter file = new FileWriter(plugin.getDataFolder() + "/linked_accounts.json")) {
                    file.write(jsonObject.toJSONString());
                    file.flush();
                } catch (IOException e) {
                    event.reply("Failed to save the linking data.").queue();
                    plugin.getLogger().severe("Error writing linked_accounts.json: " + e.getMessage());
                    return;
                }

                // Find and assign the "Linked" role to the user
                Guild guild = event.getGuild();
                if (guild != null) {
                    Role linkedRole = guild.getRolesByName("Linked", true).stream().findFirst().orElse(null);
                    if (linkedRole != null) {
                        guild.addRoleToMember(UserSnowflake.fromId(event.getUser().getId()), linkedRole).queue(
                                success -> {
                                    event.reply("Successfully linked your Minecraft account to your Discord account and assigned the Linked role.").queue();
                                    plugin.getLogger().info("Assigned 'Linked' role to user " + event.getUser().getName());
                                    // Send success message to the player in Minecraft
                                    if (player.isOnline()) {
                                        player.sendMessage(ChatColor.GREEN + "Your Discord account has been successfully linked!");
                                    }
                                },
                                failure -> {
                                    event.reply("Linked your account, but failed to assign the Linked role.").queue();
                                    plugin.getLogger().severe("Failed to assign 'Linked' role to user " + event.getUser().getName() + ": " + failure.getMessage());
                                }
                        );
                    } else {
                        event.reply("Successfully linked your account, but the 'Linked' role was not found.").queue();
                        plugin.getLogger().severe("'Linked' role not found in the guild.");
                    }
                } else {
                    event.reply("Failed to access guild.").queue();
                    plugin.getLogger().severe("Guild is null for some reason.");
                }
            }

            if (event.getName().equals("unlink") || event.getName().equals("checkign")) {
                // Read the role ID from config.yml
                String requiredRoleId = plugin.getConfig().getString("role-id");
                if (requiredRoleId == null || requiredRoleId.isEmpty()) {
                    event.reply("The required role ID is not set in the config.yml").setEphemeral(true).queue();
                    return;
                }

                // Check if the user has the required role or higher
                Role requiredRole = event.getGuild().getRoleById(requiredRoleId);
                if (requiredRole == null) {
                    event.reply("The required role does not exist.").setEphemeral(true).queue();
                    return;
                }

                if (event.getMember().getRoles().stream().noneMatch(role -> role.getPosition() >= requiredRole.getPosition())) {
                    event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
                    return;
                }

                if (event.getName().equals("unlink")) {
                    // Unlink a Minecraft account from a Discord user
                    String discordId = event.getOption("member").getAsUser().getId(); // The tagged Discord user

                    // Remove linked account from JSON
                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject;

                    try (FileReader reader = new FileReader(plugin.getDataFolder() + "/linked_accounts.json")) {
                        jsonObject = (JSONObject) parser.parse(reader);
                    } catch (IOException | ParseException e) {
                        event.reply("An error occurred while retrieving the data.").queue();
                        plugin.getLogger().severe("Error reading linked_accounts.json: " + e.getMessage());
                        return;
                    }

                    // Find and remove the account
                    boolean found = false;
                    String playerUUID = null;

                    for (Object key : jsonObject.keySet()) {
                        JSONObject playerData = (JSONObject) jsonObject.get(key);
                        if (playerData.get("discordId").equals(discordId)) {
                            playerUUID = key.toString();
                            jsonObject.remove(key); // Remove the entry
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        // Save updated JSON data
                        try (FileWriter file = new FileWriter(plugin.getDataFolder() + "/linked_accounts.json")) {
                            file.write(jsonObject.toJSONString());
                            file.flush();
                        } catch (IOException e) {
                            event.reply("Failed to unlink the account.").queue();
                            plugin.getLogger().severe("Error writing linked_accounts.json: " + e.getMessage());
                            return;
                        }

                        // Remove the Linked role from the Discord user
                        Guild guild = event.getGuild();
                        if (guild != null) {
                            Role linkedRole = guild.getRolesByName("Linked", true).stream().findFirst().orElse(null);
                            if (linkedRole != null) {
                                guild.removeRoleFromMember(UserSnowflake.fromId(discordId), linkedRole).queue(
                                        success -> event.reply("Successfully unlinked and removed the Linked role from <@" + discordId + ">").queue(),
                                        failure -> event.reply("Unlinked the account but failed to remove the Linked role.").queue()
                                );
                            } else {
                                event.reply("Unlinked the account, but the 'Linked' role was not found.").queue();
                            }
                        } else {
                            event.reply("Guild not found while removing the role.").queue();
                            plugin.getLogger().severe("Guild not found while removing the role.");
                        }

                        // Notify the player in Minecraft if they are online
                        if (playerUUID != null) {
                            Player player = plugin.getServer().getPlayer(UUID.fromString(playerUUID));
                            if (player != null && player.isOnline()) {
                                player.sendMessage("You have been unlinked from your Discord account.");
                            }
                        }

                    } else {
                        event.reply("This user is not linked to any Minecraft account.").queue();
                    }
                }

                if (event.getName().equals("checkign")) {
                    // Check the Minecraft IGN of a Discord user
                    String discordId = event.getOption("member").getAsUser().getId(); // The tagged Discord user

                    // Retrieve the linked account from JSON
                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject;

                    try (FileReader reader = new FileReader(plugin.getDataFolder() + "/linked_accounts.json")) {
                        jsonObject = (JSONObject) parser.parse(reader);
                    } catch (IOException | ParseException e) {
                        event.reply("An error occurred while retrieving the data.").queue();
                        plugin.getLogger().severe("Error reading linked_accounts.json: " + e.getMessage());
                        return;
                    }

                    // Find the account by Discord ID
                    boolean found = false;
                    String playerName = null;

                    for (Object key : jsonObject.keySet()) {
                        JSONObject playerData = (JSONObject) jsonObject.get(key);
                        if (playerData.get("discordId").equals(discordId)) {
                            playerName = (String) playerData.get("playerName");
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        event.reply("The Minecraft IGN of the user is: " + playerName).queue();
                    } else {
                        event.reply("This user is not linked to any Minecraft account.").queue();
                    }
                }
            }
        }

        @Override
        public void onReady(net.dv8tion.jda.api.events.session.ReadyEvent event) {
            // Register slash commands for the specific guild
            Guild guild = event.getJDA().getGuildById(guildId);
            if (guild == null) {
                plugin.getLogger().severe("Guild with ID " + guildId + " not found. Is the bot in the server?");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return;
            }

            guild.updateCommands().addCommands(
                    Commands.slash("link", "Link your Minecraft account with your Discord account.")
                            .addOption(OptionType.STRING, "code", "Your Minecraft linking code", true),
                    Commands.slash("unlink", "Unlink a Minecraft account from a Discord user.")
                            .addOption(OptionType.USER, "member", "The Discord member to unlink", true),
                    Commands.slash("checkign", "Check the Minecraft IGN of a Discord member.")
                            .addOption(OptionType.USER, "member", "The Discord member to check", true)
            ).queue();

            plugin.getLogger().info("Successfully registered commands for guild: " + guild.getName());
        }
    }
}