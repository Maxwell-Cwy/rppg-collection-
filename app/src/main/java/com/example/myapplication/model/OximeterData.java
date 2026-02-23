package com.example.myapplication.model;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 指夹式血氧仪数据解析模型
 * 完全按照《手指血氧协议20230117 英文版》实现
 * 兼容 Java 11，已添加所有必要 getter
 */
public class OximeterData {

    private static final String TAG = "OximeterData";

    // 原始数据
    private final List<String> rawDataList = new ArrayList<>();

    // 实时值
    private int spo2 = -1;
    private int pr = -1;
    private double temperature = -1.0;
    private double pi = -1.0;
    private int respirationRate = -1;
    private String probeStatus = "未知";
    private int batteryLevel = -1;

    // 新增：PPG 波形和 HRV（只存最新数据，UI 层自己拿去画）
    private final List<Integer> ppgList = new ArrayList<>();     // 0~127
    private final List<Integer> barList  = new ArrayList<>();    // 0~15  脉搏强度棒图
    private final List<Integer> hrvList  = new ArrayList<>();    // RR间期 ms

    // 统计值
    private int validCount = 0;
    private int totalCount = 0; // 所有原始输入包数量（包括异常）
    private int sumSpo2 = 0, sumPr = 0;
    private int minSpo2 = 999, maxSpo2 = 0;
    private int minPr = 999, maxPr = 0;

    private String startTime;
    private static Integer index=0;


// ================ 只替换下面这些内容（其余代码保持不变）================

    private String mPendingLongPacket = null; // 正在拼接的 96/97 长包

    public void addData(String hexData) {
        totalCount++;
        if (rawDataList.isEmpty()) {
            startTime = com.example.myapplication.utils.TimeUtils.getPreciseTimeStamp();
        }


        String trimmed = hexData.trim();
        if (trimmed.isEmpty()) return;

        // 统一格式：多个空格变一个，方便日志阅读
        String display = trimmed.replaceAll("\\s+", " ").toUpperCase();
        Log.e("BLE_RAW", "→ 分片收到: " + display);

        String cleanNoSpace = trimmed.replaceAll("\\s+", "").toUpperCase();

        // 情况1：新包开始（以 FFFE 开头）
        if (cleanNoSpace.startsWith("FFFE")) {
            // 先尝试完成上一个长包（防止漏包）
            if (mPendingLongPacket != null) {
                tryCompletePendingPacket();
            }

            // 判断是不是 96 或 97 长包
            if (cleanNoSpace.length() >= 12) {
                String devId = cleanNoSpace.substring(8, 10);
                String cmd   = cleanNoSpace.substring(10, 12);
                if ("23".equals(devId) && ("96".equals(cmd) || "97".equals(cmd))) {
                    index++;
                    Log.w("BLE_RAW",+ index+ " :"+cleanNoSpace);
                    mPendingLongPacket = trimmed;
                    Log.w("BLE_RAW", "开始接收长包 (CMD " + cmd + ") ...");
                    return;
                }
            }

            // 普通短包（95、99 等）直接解析
            rawDataList.add(display);
            parsePacket(display);
            return;
        }

        // 情况2：后续分片（不以 FFFE 开头）
        if (mPendingLongPacket != null) {
            totalCount--;
            mPendingLongPacket += " " + trimmed;
            Log.w("BLE_RAW", "继续拼接 → 当前累积: " + mPendingLongPacket.replaceAll("\\s+", " "));
            tryCompletePendingPacket();
        } else {
            Log.w("BLE_RAW", "警告：收到不明数据（无包头且无pending）: " + display);
        }
    }

    private void tryCompletePendingPacket() {
        {
            if (mPendingLongPacket == null) return;

            String clean = mPendingLongPacket.replaceAll("\\s+", "").toUpperCase();

            if (clean.length() < 12 || !clean.startsWith("FFFE")) {
                Log.e("BLE_RAW", "长包损坏，丢弃");
                mPendingLongPacket = null;
                return;
            }

            int ll = Integer.parseInt(clean.substring(4, 6), 16);
            int totalBytesNeeded = 2 + ll;                    // 包头2字节 + 数据ll字节
            int totalHexCharsNeeded = totalBytesNeeded * 2;

            if (clean.length() >= totalHexCharsNeeded) {
                String completeHex = clean.substring(0, totalHexCharsNeeded);
                String niceFormat = insertSpacesEveryTwoChars(completeHex);
                Log.e("BLE_RAW", "完整包拼接成功！→ " +niceFormat);

                rawDataList.add(niceFormat);
                parsePacket(niceFormat);   // 关键：这里会走校验和并解析 96/97

                // 处理剩余数据（连续发包的情况）
                if (clean.length() > totalHexCharsNeeded) {
                    String remain = clean.substring(totalHexCharsNeeded);
                    if (remain.startsWith("FFFE")) {
                        String nextPacket = insertSpacesEveryTwoChars(remain);
                        Log.w("BLE_RAW", "发现连续包，递归处理: " + nextPacket);
                        mPendingLongPacket = null;
                        addData(nextPacket);  // 递归处理下一个包
                        return;
                    }
                }

                mPendingLongPacket = null; // 清空，准备接下一个
            }
            // 否则还不够，继续等下一片
        }
    }
    // 工具：每两个字符插入空格（日志好看）
    private String insertSpacesEveryTwoChars(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            if (i > 0) sb.append(" ");
            sb.append(hex.substring(i, Math.min(i + 2, hex.length())));
        }
        return sb.toString();
    }


    private void parsePacket(String hexData) {
        String clean = hexData.replaceAll("\\s", "").toUpperCase();
        if (clean.length() < 12 || !clean.startsWith("FFFE")) {
            return;
        }

        try {
            int ll = Integer.parseInt(clean.substring(4, 6), 16);
            int csGiven = Integer.parseInt(clean.substring(6, 8), 16);
            String deviceId = clean.substring(8, 10);
            String cmdId = clean.substring(10, 12);

            if (!"23".equals(deviceId)) {
                return;
            }

            int calcCs = 0;
            calcCs += ll;                                           // LL
            calcCs += Integer.parseInt(clean.substring(8, 10), 16); // deviceID (23)
            calcCs += Integer.parseInt(cmdId, 16);
            // cmdID
            // DATA 从第12个字符（第6字节）之后开始，正好跳过 CS 字节
            for (int i = 12; i < clean.length(); i += 2) {
                calcCs += Integer.parseInt(clean.substring(i, i + 2), 16);
            }
            calcCs &= 0xFF;

            if (calcCs != csGiven) {
                Log.w(TAG, "校验和错误: 期望=" + String.format("%02X", csGiven) +
                        " 计算=" + String.format("%02X", calcCs) + " 数据=" + hexData);
                return;
            }

            String dataHex = clean.substring(12);
            byte[] data = hexStringToByteArray(dataHex);

            // 原来就有的两个 + 新增三个
            if ("95".equals(cmdId)) {
                parseCmd95(data);
            } else if ("99".equals(cmdId) && data.length >= 1) {
                parseCmd99(data);
            } else if ("96".equals(cmdId) && data.length >= 20) {        // PPG 波形包
                parseCmd96(data);
            } else if ("97".equals(cmdId) && data.length >= 20) {        // HRV 包
                parseCmd97(data);
            }

        } catch (Exception e) {
            Log.e(TAG, "解析异常: " + hexData, e);
        }
    }

    // ==================== 原来就有的 CMD 95 ====================
    private void parseCmd95(byte[] d) {
        if (d.length < 7) return;

        int b0 = d[0] & 0xFF;
        int b1 = d[1] & 0xFF;
        int b2 = d[2] & 0xFF;
        int b3 = d[3] & 0xFF;
        int b4 = d[4] & 0xFF;
        int b5 = d[5] & 0xFF;
        int b6 = d[6] & 0xFF;
        int b7 = (d.length >= 8) ? d[7] & 0xFF : -1;

        // 探头状态（完全保持你原来写法）
        int probeCode = (b0 >> 2) & 0x07;
        switch (probeCode) {
            case 0: probeStatus = "正常"; break;
            case 1: probeStatus = "探头未接"; break;
            case 2: probeStatus = "电流过大"; break;
            case 3: probeStatus = "探头故障"; break;
            case 4: probeStatus = "手指脱落"; break;
            default: probeStatus = "未知状态(" + probeCode + ")"; break;
        }

        // PR
        pr = b1 + ((b2 >> 7) & 1) * 256;
        if (pr < 25 || pr > 300) pr = -1;

        // SpO2
        spo2 = b2 & 0x7F;
        if (spo2 > 100) spo2 = -1;

        // 体温
        if (b3 >= 1 && b3 <= 99 && b4 <= 9) {
            temperature = b3 + b4 / 10.0;
        } else {
            temperature = -1.0;
        }

        // PI
        if (b5 != 0x7F && b6 != 0x7F && b5 <= 20) {
            pi = b5 + b6 / 100.0;
        } else {
            pi = -1.0;
        }

        // 呼吸率（修复：原来误写成 b，应为 b7）
        if (b7 >= 4 && b7 <= 120) {
            respirationRate = b7;
        } else if (b7 == -1) {
            // 不覆盖旧值
        } else {
            respirationRate = -1;
        }

        // 统计（完全保留你原来的逻辑）
        if (spo2 > 0 || pr > 0) {
            validCount++;
            if (spo2 > 0 && spo2 <= 100) {
                sumSpo2 += spo2;
                minSpo2 = Math.min(minSpo2, spo2);
                maxSpo2 = Math.max(maxSpo2, spo2);
            }
            if (pr >= 25 && pr <= 300) {
                sumPr += pr;
                minPr = Math.min(minPr, pr);
                maxPr = Math.max(maxPr, pr);
            }
        }
    }

    private void parseCmd99(byte[] d) {
        batteryLevel = d[0] & 0x03;
    }

    private void parseCmd96(byte[] d) {
        if (d.length < 20) return;

        for (int i = 0; i < 20; i += 2) {
            int wave = d[i] & 0x7F;
            int bar  = d[i + 1] & 0x0F;

            // 直接用类的成员变量！不要重新声明！
            ppgList.add(wave);
            barList.add(bar);
        }
    }

    private void parseCmd97(byte[] d) {
        if (d.length < 20) return;

        for (int i = 0; i < 20; i += 2) {
            int rr = ((d[i] & 0xFF) << 8) | (d[i + 1] & 0xFF);
            if (rr > 0 && rr <= 2000) {
                hrvList.add(rr);   // 直接用成员变量
                if (hrvList.size() > 500) {
                    hrvList.remove(0);
                }
            }
        }
    }
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    | Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    // ====================== 对外接口（只新增3个，其余完全不变）======================
    public String toHexString() {
        return String.join(",", rawDataList);
    }

    public int getCount() { return rawDataList.size(); }

    public String getStartTime() { return startTime != null ? startTime : ""; }

    public boolean hasData() { return !rawDataList.isEmpty(); }

    public int getValidCount() { return validCount; }
    public int getTotalCount() { return totalCount; }

    // 新增的三个 getter（UI层画波形直接调这三个就行）
    public List<Integer> getPpgList() { return ppgList; }
    public List<Integer> getBarList()  { return barList; }
    public List<Integer> getHrvList()  { return hrvList; }

    // 你原来的 generateReport 一字未改
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("指夹式血氧检测报告\n");
        sb.append("══════════════════════════\n");
        sb.append("检测时间：").append(getStartTime()).append("\n");
        sb.append("数据包总数：").append(totalCount).append(" 条\n");
        sb.append("有效数据：").append(rawDataList.size()).append(" 条\n");
        sb.append("探头状态：").append(probeStatus).append("\n\n");

        if (spo2 >= 0) {
            sb.append(String.format("当前血氧 SpO₂： %3d%%\n", spo2));
            if (validCount > 0) {
                sb.append(String.format("  ├ 平均值域：%d ~ %d %%\n", minSpo2 == 999 ? 0 : minSpo2, maxSpo2));
                sb.append(String.format("  └ 平均：%.1f %%\n", sumSpo2 * 1.0 / validCount));
            }
        } else sb.append("当前血氧: 错误\n");

        if (pr >= 0) {
            sb.append(String.format("当前心率 PR：   %3d bpm\n", pr));
            if (validCount > 0) {
                sb.append(String.format("  ├ 平均：%d bpm\n", sumPr / validCount));
            }
        } else sb.append("当前心率: 错误\n");

        if (temperature > 0) sb.append(String.format("体温：         %.1f ℃\n", temperature));
        else sb.append("温度： 错误\n");

        if (pi >= 0) sb.append(String.format("灌注指数 PI：  %.2f%%\n", pi));
        if (respirationRate > 0) sb.append(String.format("呼吸率：       %d 次/分\n", respirationRate));
        else sb.append("呼吸率： 错误\n");

        if (batteryLevel >= 0) {
            String[] bats = {"电量空", "电量低", "电量中等", "电量充足"};
            sb.append("设备电量：     ").append(bats[batteryLevel]).append("\n");
        }

        sb.append("══════════════════════════\n");
        sb.append("正常参考：SpO₂≥95% | PR 60-100 | 体温36.0-37.2℃\n");
        return sb.toString();
    }

    // 你原来所有的 getter 完全保留
    public int getSpo2() { return spo2; }
    public int getPr() { return pr; }
    public double getTemperature() { return temperature; }
    public double getPi() { return pi; }
    public int getRespirationRate() { return respirationRate; }
    public String getProbeStatus() { return probeStatus; }
    public int getBatteryLevel() { return batteryLevel; }

    public int getAvgSpo2() { return validCount > 0 ? sumSpo2 / validCount : -1; }
    public int getMinSpo2() { return minSpo2 == 999 ? -1 : minSpo2; }
    public int getMaxSpo2() { return maxSpo2; }
    public int getAvgPr() { return validCount > 0 ? sumPr / validCount : -1; }
    public int getMinPr() { return minPr == 999 ? -1 : minPr; }
    public int getMaxPr() { return maxPr; }

    public void clear() {
        rawDataList.clear();
        ppgList.clear();
        barList.clear();
        hrvList.clear();
        startTime = null;
        spo2 = pr = -1;
        temperature = pi = -1.0;
        respirationRate = batteryLevel = -1;
        probeStatus = "未知";
        validCount = sumSpo2 = sumPr = 0;
        minSpo2 = minPr = 999;
        maxSpo2 = maxPr = 0;
        totalCount = 0;
    }

    public static OximeterData fromJson(JSONObject json) {
        OximeterData data = new OximeterData();

        // 恢复统计值（设置内部字段以匹配 getter）
        int avgSpo2 = json.optInt("avg_spo2", -1);
        data.minSpo2 = json.optInt("min_spo2", 999);  // 如果 -1，则设为999以匹配 getMinSpo2
        data.maxSpo2 = json.optInt("max_spo2", 0);
        if (avgSpo2 >= 0) {
            data.validCount = 1;  // 任意非0值，确保 avg 计算正确
            data.sumSpo2 = avgSpo2;  // 因为 avg = sum / validCount
        } else {
            data.validCount = 0;
            data.sumSpo2 = 0;
        }

        int avgPr = json.optInt("avg_pr", -1);
        data.minPr = json.optInt("min_pr", 999);
        data.maxPr = json.optInt("max_pr", 0);
        if (avgPr >= 0) {
            if (data.validCount == 0) data.validCount = 1;
            data.sumPr = avgPr;
        } else {
            data.sumPr = 0;
        }

        // 实时值（用 JSON 中的值设置，假设为最后有效值）
        data.temperature = json.optDouble("temperature", -1.0);
        data.pi = json.optDouble("pi", -1.0);
        data.respirationRate = json.optInt("respiration_rate", -1);
        data.probeStatus = json.optString("probe_status", "未知");
        data.batteryLevel = json.optInt("battery_level", -1);

        // 恢复 PPG 数据
        JSONArray ppgArray = json.optJSONArray("ppg_data");
        if (ppgArray != null) {
            for (int i = 0; i < ppgArray.length(); i++) {
                JSONObject obj = ppgArray.optJSONObject(i);
                if (obj != null) {
                    data.ppgList.add(obj.optInt("wave", 0));
                    data.barList.add(obj.optInt("bar", 0));
                }
            }
        }

        // 恢复 HRV 数据
        JSONArray hrvArray = json.optJSONArray("hrv_data");
        if (hrvArray != null) {
            for (int i = 0; i < hrvArray.length(); i++) {
                data.hrvList.add(hrvArray.optInt(i, 0));
            }
        }

        // 恢复原始 hex 数据（rawDataList）
        String rawHex = json.optString("raw_hex_data", "");
        if (!rawHex.isEmpty()) {
            String[] parts = rawHex.split(",");
            for (String part : parts) {
                data.rawDataList.add(part.trim());
            }
            data.totalCount = data.rawDataList.size();
        }

        // 可选：用 data_start_time 作为 startTime（如果需要）
        // data.startTime = json.optString("data_start_time", null);

        return data;
    }
}