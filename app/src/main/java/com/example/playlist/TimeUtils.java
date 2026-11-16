package com.example.playlist;

import java.util.Locale;

/**
 * Перевод длительности: секунды <-> строка вида "3:45" или "1:02:10".
 */
public final class TimeUtils {

    private TimeUtils() { }

    /** 225 -> "3:45", 3730 -> "1:02:10" */
    public static String format(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    /**
     * Разбор строки в секунды.
     * "3:45" -> 225, "1:02:10" -> 3730, "4" -> 240 (просто число = минуты).
     * Возвращает -1, если строка некорректная.
     */
    public static int parse(String text) {
        if (text == null) return -1;
        text = text.trim();
        if (text.isEmpty()) return -1;
        try {
            String[] p = text.split(":");
            if (p.length == 1) {
                int min = Integer.parseInt(p[0]);
                return min >= 0 ? min * 60 : -1;
            } else if (p.length == 2) {
                int min = Integer.parseInt(p[0]);
                int sec = Integer.parseInt(p[1]);
                if (min < 0 || sec < 0 || sec > 59) return -1;
                return min * 60 + sec;
            } else if (p.length == 3) {
                int h = Integer.parseInt(p[0]);
                int min = Integer.parseInt(p[1]);
                int sec = Integer.parseInt(p[2]);
                if (h < 0 || min < 0 || min > 59 || sec < 0 || sec > 59) return -1;
                return h * 3600 + min * 60 + sec;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return -1;
    }
}
