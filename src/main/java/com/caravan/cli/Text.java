package com.caravan.cli;

/** 표를 줄 맞춰 찍기 위한 잡동사니. 한글은 터미널에서 두 칸을 먹는다. */
final class Text {

    private Text() { }

    /** 터미널에서 차지하는 칸 수. 한중일 문자는 2칸으로 센다. */
    static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += isWide(s.charAt(i)) ? 2 : 1;
        }
        return w;
    }

    private static boolean isWide(char c) {
        return (c >= 0x1100 && c <= 0x115F)
                || (c >= 0x2E80 && c <= 0xA4CF)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }

    static String padRight(String s, int columns) {
        return s + " ".repeat(Math.max(0, columns - width(s)));
    }

    static String padLeft(String s, int columns) {
        return " ".repeat(Math.max(0, columns - width(s))) + s;
    }

    /** 1234567.8 → "1,234,568" */
    static String money(double amount) {
        return String.format("%,.0f", amount);
    }

    static String number(double value) {
        return String.format("%,.0f", value);
    }
}
