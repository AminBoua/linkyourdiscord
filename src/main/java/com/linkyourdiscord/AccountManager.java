package com.linkyourdiscord;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class AccountManager {

    public static void createLinkedAccountsFile(JavaPlugin plugin) {
        try {
            File file = new File(plugin.getDataFolder(), "linked_accounts.json");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write("{}");
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create linked_accounts.json file: " + e.getMessage());
        }
    }

    public static boolean isPlayerLinked(UUID playerUUID, JavaPlugin plugin) {
        JSONParser parser = new JSONParser();
        try (FileReader reader = new FileReader(plugin.getDataFolder() + "/linked_accounts.json")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            return jsonObject.containsKey(playerUUID.toString());
        } catch (IOException | ParseException e) {
            return false;
        }
    }

    public static void linkAccount(UUID playerUUID, String discordId, String playerName, JavaPlugin plugin) {
        JSONParser parser = new JSONParser();
        try (FileReader reader = new FileReader(plugin.getDataFolder() + "/linked_accounts.json")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            JSONObject playerData = new JSONObject();
            playerData.put("discordId", discordId);
            playerData.put("playerName", playerName);
            jsonObject.put(playerUUID.toString(), playerData);

            try (FileWriter file = new FileWriter(plugin.getDataFolder() + "/linked_accounts.json")) {
                file.write(jsonObject.toJSONString());
                file.flush();
            }
        } catch (IOException | ParseException e) {
            plugin.getLogger().severe("Failed to link account: " + e.getMessage());
        }
    }
}