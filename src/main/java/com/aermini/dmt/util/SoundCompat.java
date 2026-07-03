package com.aermini.dmt.util;

import org.bukkit.Sound;
import java.util.HashMap;
import java.util.Map;

public class SoundCompat {

    private static final Map<String, String> LEGACY_MAP = new HashMap<String, String>();

    static {
        LEGACY_MAP.put("NOTE_PIANO", "BLOCK_NOTE_PLING");
        LEGACY_MAP.put("NOTE_BASS", "BLOCK_NOTE_BASS");
        LEGACY_MAP.put("NOTE_PLING", "BLOCK_NOTE_PLING");
        LEGACY_MAP.put("NOTE_SNARE", "BLOCK_NOTE_SNARE");
        LEGACY_MAP.put("NOTE_STICKS", "BLOCK_NOTE_HAT");
        LEGACY_MAP.put("NOTE_BASS_DRUM", "BLOCK_NOTE_BASEDRUM");
        LEGACY_MAP.put("ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP");
        LEGACY_MAP.put("FIZZ", "BLOCK_FIRE_EXTINGUISH");
        LEGACY_MAP.put("EXPLODE", "ENTITY_GENERIC_EXPLODE");
        LEGACY_MAP.put("SPLASH", "ENTITY_GENERIC_SPLASH");
    }

    public static Sound getSound(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            String mapped = LEGACY_MAP.get(name);
            if (mapped != null) {
                try {
                    return Sound.valueOf(mapped);
                } catch (IllegalArgumentException ignored) {}
            }
            String reversed = null;
            for (Map.Entry<String, String> entry : LEGACY_MAP.entrySet()) {
                if (entry.getValue().equals(name)) {
                    reversed = entry.getKey();
                    break;
                }
            }
            if (reversed != null) {
                try {
                    return Sound.valueOf(reversed);
                } catch (IllegalArgumentException ignored) {}
            }
            return null;
        }
    }
}