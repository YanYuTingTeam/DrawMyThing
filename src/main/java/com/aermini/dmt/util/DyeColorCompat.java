package com.aermini.dmt.util;

import org.bukkit.DyeColor;
import java.lang.reflect.Method;

public class DyeColorCompat {
    private static Method woolDataMethod;
    private static boolean checked = false;
    public static byte getData(DyeColor dc) {
        if (!checked) {
            checked = true;
            try {
                woolDataMethod = DyeColor.class.getMethod("getWoolData");
            } catch (NoSuchMethodException e) {
                try {
                    woolDataMethod = DyeColor.class.getMethod("getData");
                } catch (NoSuchMethodException ignored) {}
            }
        }
        if (woolDataMethod != null) {
            try {
                return (byte) woolDataMethod.invoke(dc);
            } catch (Exception ignored) {}
        }
        return 0;
    }
}