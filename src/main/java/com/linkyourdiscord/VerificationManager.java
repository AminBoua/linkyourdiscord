package com.linkyourdiscord;

import java.util.HashMap;
import java.util.UUID;

public class VerificationManager {

    // This map holds pending verification codes and their associated player UUIDs.
    private static HashMap<String, UUID> pendingVerifications = new HashMap<>();

    // Generate a unique verification code for the player and store it in the pendingVerifications map.
    public static String generateVerificationCode(UUID playerUUID) {
        String code = UUID.randomUUID().toString().substring(0, 8); // Shorten UUID to 8 characters
        pendingVerifications.put(code, playerUUID); // Store code -> player UUID mapping
        return code;
    }

    // Retrieve the player's UUID associated with the verification code.
    public static UUID getPendingVerification(String code) {
        return pendingVerifications.get(code);
    }

    // Remove the verification code once it's been used.
    public static void removePendingVerification(String code) {
        pendingVerifications.remove(code);
    }

    // Check if a verification code is still valid (exists in the map).
    public static boolean isCodeValid(String code) {
        return pendingVerifications.containsKey(code);
    }
}