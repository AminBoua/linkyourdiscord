package com.linkyourdiscord;

import java.util.UUID;

import org.bukkit.entity.Player;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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
            UUID playerUUID = VerificationManager.getPendingVerification(code);
            if (playerUUID == null) {
                event.reply("Invalid code. Please try again.").setEphemeral(true).queue();
                return;
            }

            // Link the Discord account and assign role
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null) {
                AccountManager.linkAccount(playerUUID, event.getUser().getId(), player.getName(), plugin);
                Guild guild = event.getGuild();
                Role linkedRole = guild.getRolesByName("Linked", true).stream().findFirst().orElse(null);
                if (linkedRole != null) {
                    guild.addRoleToMember(UserSnowflake.fromId(event.getUser().getId()), linkedRole).queue();
                    event.reply("Successfully linked your accounts!").queue();
                }
            }
        }
    }
}