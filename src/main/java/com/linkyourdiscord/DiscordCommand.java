package com.linkyourdiscord;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

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
        FileConfiguration config = plugin.getConfig();

        if (label.equalsIgnoreCase("discord")) {
            // Check if the player is already linked
            if (AccountManager.isPlayerLinked(player.getUniqueId(), plugin)) {
                String alreadyLinkedMessage = config.getString("messages.already-linked");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyLinkedMessage));
                return true;
            }

            // Create clickable message
            String clickableText = config.getString("messages.discord-url-text");
            String discordUrl = config.getString("messages.discord-url");

            TextComponent clickableMessage = new TextComponent(ChatColor.translateAlternateColorCodes('&', clickableText));
            clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, discordUrl));
            player.spigot().sendMessage(clickableMessage);

            // Generate and send link message
            String code = VerificationManager.generateVerificationCode(player.getUniqueId());
            String message = config.getString("messages.link-message");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("%code%", code)));

        } else if (label.equalsIgnoreCase("unlink")) {
            // Handle unlink logic here
            // (left out for brevity)
        }
        return true;
    }
}