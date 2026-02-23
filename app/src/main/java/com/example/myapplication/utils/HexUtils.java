package com.example.myapplication.utils;

/**
 * 十六进制工具类：蓝牙数据转换
 */
public class HexUtils {
    /**
     * 字节数组转十六进制字符串
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append("0");
            }
            sb.append(hex.toUpperCase()).append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * 十六进制字符串转字节数组
     */
    public static byte[] hexToBytes(String hexStr) {
        if (hexStr == null || hexStr.isEmpty()) {
            return new byte[0];
        }
        hexStr = hexStr.replaceAll(" ", "");
        int len = hexStr.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hexStr.charAt(i), 16) << 4)
                    + Character.digit(hexStr.charAt(i + 1), 16));
        }
        return bytes;
    }
}