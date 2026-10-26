package com.aermini.dmt.game;

import com.aermini.dmt.util.ChatUtil;
import com.aermini.dmt.DrawMyThing;
import com.aermini.dmt.util.SoundCompat;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameMessenger {

    private final Game game;
    private final DrawMyThing instance;

    public GameMessenger(Game game, DrawMyThing instance) {
        this.game = game;
        this.instance = instance;
    }

    public String replacePlaceholders(String template, Player player) {
        if (template == null) return "";
        String result = template;
        result = result.replace("%game%", game.getName());
        result = result.replace("%count%", String.valueOf(game.getPlayerCount()));
        result = result.replace("%max%", String.valueOf(game.getMaxPlayers()));
        result = result.replace("%min%", String.valueOf(game.getMinPlayers()));
        result = result.replace("%starttime%", String.valueOf(game.getStartTime()));
        result = result.replace("%endtime%", String.valueOf(game.getEndTime()));
        result = result.replace("%round_now%", String.valueOf(game.getRoundCount()));
        result = result.replace("%round_all%", String.valueOf(game.getRound() * game.getScoreMap().size()));
        if (game.getBuilder() != null) {
            result = result.replace("%builder_player%", game.getBuilder().getName());
        } else if (game.getRoundBuilderName() != null) {
            result = result.replace("%builder_player%", game.getRoundBuilderName());
        }
        if (player != null) {
            result = result.replace("%player%", player.getName());
            if (game.getScoreMap().containsKey(player)) result = result.replace("%score%", String.valueOf(game.getScoreMap().get(player)));
            if (game.getRoundGainedScore().containsKey(player)) result = result.replace("%getscore%", String.valueOf(game.getRoundGainedScore().get(player)));
            else result = result.replace("%getscore%", "0");
        }
        if (game.getWord() != null) {
            result = result.replace("%paintname%", game.getWord());
            result = result.replace("%tip%", player != null ? getTipText(player) : getTipText(null));
        }
        return result;
    }

    public String formatMsg(String key, Player player, String... replacements) {
        Object raw = instance.getMsgConfig().get(key);
        String template;
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(list.get(i).toString());
            }
            template = sb.toString();
        } else {
            template = raw != null ? raw.toString() : "";
        }
        String msg = ChatUtil.colorize(template);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return replacePlaceholders(msg, player);
    }

    public void sendMessage(String message) {
        for (Player p : game.getPlayers()) p.sendMessage(message);
    }

    public void sendMsg(String key, String... replacements) {
        String msg = formatMsg(key, null, replacements);
        if (msg.contains("\n")) {
            String[] lines = msg.split("\n");
            for (Player p : game.getPlayers()) {
                p.sendMessage(ChatUtil.getPrefix() + lines[0]);
                for (int i = 1; i < lines.length; i++) {
                    p.sendMessage(lines[i]);
                }
            }
        } else {
            for (Player p : game.getPlayers()) p.sendMessage(ChatUtil.getPrefix() + msg);
        }
    }

    public void playSoundForAll(Sound sound, float volume, float pitch) {
        for (Player p : game.getPlayers()) p.playSound(p.getLocation(), sound, volume, pitch);
    }

    public void playSoundsForAll(List<String> soundList) {
        if (soundList == null) return;
        for (String s : soundList) {
            if (s == null || s.isEmpty()) continue;
            try {
                String[] sp = s.split(",");
                Sound parsed = SoundCompat.getSound(sp[0].trim());
                if (parsed == null) continue;
                playSoundForAll(parsed, sp.length > 1 ? Float.parseFloat(sp[1].trim()) : 1.0f, sp.length > 2 ? Float.parseFloat(sp[2].trim()) : 1.0f);
            } catch (Exception ignored) {}
        }
    }

    public void sendTitleToAll(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player p : game.getPlayers()) ChatUtil.sendTitle(p, title, subtitle, fadeIn, stay, fadeOut);
    }

    public void sendActionBarToAll(String message) {
        for (Player p : game.getPlayers()) ChatUtil.sendActionBar(p, message);
    }

    public String getTipText(Player player) {
        if (game.getWord() == null) return "";
        String splice = instance.getConfig().getString("tip.splice", " ");
        String hide = instance.getConfig().getString("tip.hide", "_");
        if (game.getBuilder() != null && game.getBuilder().equals(player)) return joinWithSplice(game.getWord(), splice);
        StringBuilder sb = new StringBuilder();
        StringBuilder wordf = game.getWordFormatted();
        for (int i = 0; i < wordf.length(); i++) {
            if (i > 0) sb.append(splice);
            char c = wordf.charAt(i);
            sb.append(c == '_' ? hide : String.valueOf(c));
        }
        return sb.toString();
    }

    private String joinWithSplice(String word, String splice) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) { if (i > 0) sb.append(splice); sb.append(word.charAt(i)); }
        return sb.toString();
    }

    public void sendGuessedTitle(Player player) {
        String title = instance.getConfig().getString("game.guessed.title", null);
        String subtitle = instance.getConfig().getString("game.guessed.subtitle", null);
        if (title != null) title = replacePlaceholders(title, player);
        if (subtitle != null) subtitle = replacePlaceholders(subtitle, player);
        boolean hasTitle = title != null && !title.isEmpty();
        boolean hasSub = subtitle != null && !subtitle.isEmpty();
        if (!hasTitle && !hasSub) return;
        int fadeIn = instance.getConfig().getInt("game.guessed.fade-in", 0);
        int stay = instance.getConfig().getInt("game.guessed.stay", 20);
        int fadeOut = instance.getConfig().getInt("game.guessed.fade-out", 10);
        ChatUtil.sendTitle(player, hasTitle ? title : " ", hasSub ? subtitle : null, fadeIn, stay, fadeOut);
    }

    public void sendStartedBroadcast() {
        sendMsg("game-started");
        String title = instance.getConfig().getString("game.started.title", null);
        String subtitle = instance.getConfig().getString("game.started.subtitle", null);
        String soundStr = instance.getConfig().getString("game.started.sound", "NOTE_PLING,1,2");
        int fadeIn = instance.getConfig().getInt("game.started.fade-in", 0);
        int stay = instance.getConfig().getInt("game.started.stay", 40);
        int fadeOut = instance.getConfig().getInt("game.started.fade-out", 10);
        boolean hasTitle = title != null && !title.isEmpty();
        boolean hasSub = subtitle != null && !subtitle.isEmpty();
        if (hasTitle || hasSub) {
            sendTitleToAll(hasTitle ? ChatUtil.colorize(title) : null, hasSub ? ChatUtil.colorize(subtitle) : null, fadeIn, stay, fadeOut);
        }
        if (soundStr != null && !soundStr.isEmpty()) {
            try {
                String[] sp = soundStr.split(",");
                Sound snd = SoundCompat.getSound(sp[0].trim());
                if (snd != null) playSoundForAll(snd, sp.length > 1 ? Float.parseFloat(sp[1].trim()) : 1.0f, sp.length > 2 ? Float.parseFloat(sp[2].trim()) : 1.0f);
            } catch (Exception ignored) {}
        }
    }

    public void runCountdownBroadcast(int totalTime, List<Integer> showtime, List<String> soundList, String titleStr, String subtitleStr, int fadeIn, int stay, int fadeOut, String timePlaceholder, boolean checkRunning, List<BukkitRunnable> taskList) {
        runCountdownBroadcast(totalTime, showtime, soundList, titleStr, subtitleStr, fadeIn, stay, fadeOut, timePlaceholder, checkRunning, false, taskList);
    }

    public void runCountdownBroadcast(int totalTime, List<Integer> showtime, List<String> soundList, String titleStr, String subtitleStr, int fadeIn, int stay, int fadeOut, String timePlaceholder, boolean checkRunning, boolean duringGame, List<BukkitRunnable> taskList) {
        if (showtime == null || showtime.isEmpty()) return;
        for (int idx = 0; idx < showtime.size(); idx++) {
            final int t = showtime.get(idx);
            if (t > totalTime) continue;
            final String fs = soundList != null && idx < soundList.size() ? soundList.get(idx) : null;
            final String ft = titleStr;
            final String fst = subtitleStr;
            final int timeLeft = t;
            BukkitRunnable task = new BukkitRunnable() {
                public void run() {
                    if (duringGame) {
                        if (!game.isStarted() && !game.isAftergameRunning()) { cancel(); return; }
                    } else {
                        if (checkRunning ? !game.isAftergameRunning() : !game.isWaiting()) { cancel(); return; }
                    }
                    broadcastCountdown(timeLeft, fs, ft, fst, fadeIn, stay, fadeOut, timePlaceholder);
                }
            };
            task.runTaskLater(instance, (totalTime - t) * 20L);
            if (taskList != null) taskList.add(task);
        }
    }

    private void broadcastCountdown(int timeLeft, String soundStr, String titleStr, String subtitleStr, int fadeIn, int stay, int fadeOut, String timePlaceholder) {
        if (soundStr != null && !soundStr.isEmpty()) {
            try {
                String[] sp = soundStr.split(",");
                Sound snd = SoundCompat.getSound(sp[0].trim());
                if (snd != null) playSoundForAll(snd, sp.length > 1 ? Float.parseFloat(sp[1].trim()) : 1.0f, sp.length > 2 ? Float.parseFloat(sp[2].trim()) : 1.0f);
            } catch (Exception ignored) {}
        }
        String title = titleStr != null ? ChatUtil.colorize(titleStr.replace(timePlaceholder, String.valueOf(timeLeft))) : null;
        String subtitle = subtitleStr != null ? ChatUtil.colorize(subtitleStr.replace(timePlaceholder, String.valueOf(timeLeft))) : null;
        if ((title != null && !title.isEmpty()) || (subtitle != null && !subtitle.isEmpty())) {
            sendTitleToAll(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public void startCountdownBroadcast(int totalTime, List<BukkitRunnable> taskList) {
        List<Integer> showtime = instance.getConfig().getIntegerList("beforestart.showtime");
        if (showtime == null || showtime.isEmpty()) showtime = Arrays.asList(10, 5, 4, 3, 2, 1);
        List<String> soundList = instance.getConfig().getStringList("beforestart.broadcast.sound");
        String titleStr = instance.getConfig().getString("beforestart.broadcast.title", "&6&l游戏将在 %time% 秒后开始");
        String subtitleStr = instance.getConfig().getString("beforestart.broadcast.subtitle", null);
        int fadeIn = instance.getConfig().getInt("beforestart.broadcast.fade-in", 20);
        int stay = instance.getConfig().getInt("beforestart.broadcast.stay", 40);
        int fadeOut = instance.getConfig().getInt("beforestart.broadcast.fade-out", 20);
        runCountdownBroadcast(totalTime, showtime, soundList, titleStr, subtitleStr, fadeIn, stay, fadeOut, "%time%", false, taskList);
    }
}
