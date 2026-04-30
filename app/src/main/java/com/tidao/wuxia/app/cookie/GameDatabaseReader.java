package com.tidao.wuxia.app.cookie;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读取天刀助手游戏数据库获取角色信息
 *
 * 数据库路径: /data/data/com.tencent.gamehelper.wuxia/databases/
 * 每个账号对应不同的数据库，数据库文件名是数字
 * 需要根据当前登录账号的uin来找到对应的数据库
 */
public class GameDatabaseReader {
    private static final String TAG = "GameDatabaseReader";

    // 天刀助手包名
    private static final String TARGET_PACKAGE = "com.tencent.gamehelper.wuxia";

    // 数据库目录
    private static final String TARGET_DATABASE_DIR = "/data/data/" + TARGET_PACKAGE + "/databases/";

    // 临时复制文件的位置
    private static final String TEMP_GAME_DB = "/data/local/tmp/tidao_game.db";

    // 当前登录账号的uin
    private String currentUin = "";

    // 找到的目标数据库路径
    private String foundDbPath = "";

    /**
     * 单个角色信息
     */
    public static class SingleRole {
        public String area = "";
        public String areaId = "";
        public String areaName = "";
        public String playername = "";
        public String roleid = "";
        public String roleLevel = "";
        public String roleJob = "";
        public String serverName = "";

        @Override
        public String toString() {
            return playername + " [" + areaName + "-" + serverName + "] (" + roleJob + ", Lv." + roleLevel + ")";
        }
    }

    /**
     * 角色信息数据类
     */
    public static class RoleInfo {
        public String area = "";        // 大区ID (f_serverId)
        public String areaId = "";      // f_areaId
        public String areaName = "";    // 大区名称 (f_areaName)
        public String playername = "";  // 角色名 (f_roleName)
        public String roleid = "";      // 角色ID (f_roleId)
        public String uin = "";         // QQ号 (f_uin)
        public String roleLevel = "";   // 角色等级 (f_stringLevel)
        public String roleJob = "";     // 职业 (f_roleJob)
        public String serverName = "";  // 服务器名 (f_serverName)
        public String gameName = "";    // 游戏名 (f_gameName)
        public java.util.List<SingleRole> allRoles = new java.util.ArrayList<>(); // 所有角色列表

        public boolean isComplete() {
            return !area.isEmpty() && !playername.isEmpty() && !roleid.isEmpty();
        }

        public String toConfigString() {
            StringBuilder sb = new StringBuilder();
            sb.append("area=").append(area);
            sb.append(", playername=").append(playername);
            sb.append(", roleid=").append(roleid);
            if (!roleLevel.isEmpty()) sb.append(", roleLevel=").append(roleLevel);
            if (!roleJob.isEmpty()) sb.append(", roleJob=").append(roleJob);
            if (!areaName.isEmpty()) sb.append(", areaName=").append(areaName);
            return sb.toString();
        }

        @Override
        public String toString() {
            return "RoleInfo{" +
                    "area='" + area + '\'' +
                    ", areaName='" + areaName + '\'' +
                    ", playername='" + playername + '\'' +
                    ", roleid='" + roleid + '\'' +
                    ", uin='" + uin + '\'' +
                    ", roleLevel='" + roleLevel + '\'' +
                    ", roleJob='" + roleJob + '\'' +
                    ", serverName='" + serverName + '\'' +
                    ", gameName='" + gameName + '\'' +
                    ", allRoles=" + allRoles +
                    '}';
        }
    }

    public interface OnRoleInfoReadListener {
        void onRoleInfoReadSuccess(RoleInfo data);
        void onRoleInfoReadFailed(String error);
    }

    /**
     * 设置当前登录账号的uin（从Cookie中提取）
     */
    public void setCurrentUin(String uin) {
        // uin格式可能是 "o3136564405" 或 "o03136564405" 或 "3136564405"
        String cleanedUin = uin;
        if (cleanedUin.startsWith("o")) {
            cleanedUin = cleanedUin.substring(1);
        }
        // 去掉前导零，避免 "03136564405" 与 "3136564405" 不匹配
        try {
            cleanedUin = String.valueOf(Long.parseLong(cleanedUin));
        } catch (NumberFormatException e) {
            // 如果解析失败，保留原值
        }
        this.currentUin = cleanedUin;
        Log.d(TAG, "设置当前uin: " + this.currentUin);
    }

    /**
     * 读取角色信息
     */
    public void readRoleInfo(Context context, OnRoleInfoReadListener listener) {
        new Thread(() -> {
            try {
                Log.d(TAG, "开始读取角色信息，当前uin: " + currentUin);

                // 1. 找到包含当前uin的数据库
                String dbPath = findDatabaseForUin(currentUin);
                if (dbPath == null) {
                    listener.onRoleInfoReadFailed("找不到包含uin " + currentUin + " 的数据库");
                    return;
                }

                Log.d(TAG, "找到目标数据库: " + dbPath);

                // 2. 复制游戏数据库到临时位置
                boolean copySuccess = copyGameDatabaseWithSu(dbPath);
                if (!copySuccess) {
                    listener.onRoleInfoReadFailed("无法复制游戏数据库，可能没有 root 权限");
                    return;
                }

                // 3. 读取复制后的数据库
                RoleInfo data = readGameDatabase(context);

                // 4. 清理临时文件
                cleanupTempFile();

                if (data != null && data.isComplete()) {
                    listener.onRoleInfoReadSuccess(data);
                } else {
                    listener.onRoleInfoReadFailed("角色信息不完整或读取失败");
                }

            } catch (Exception e) {
                Log.e(TAG, "读取角色信息失败", e);
                listener.onRoleInfoReadFailed(e.getMessage());
            }
        }).start();
    }

    /**
     * 同步读取角色信息（阻塞）
     */
    public RoleInfo readRoleInfoSync(Context context) throws Exception {
        final Object lock = new Object();
        final RoleInfo[] result = new RoleInfo[1];
        final String[] error = new String[1];

        readRoleInfo(context, new OnRoleInfoReadListener() {
            @Override
            public void onRoleInfoReadSuccess(RoleInfo data) {
                result[0] = data;
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onRoleInfoReadFailed(String err) {
                error[0] = err;
                synchronized (lock) {
                    lock.notify();
                }
            }
        });

        synchronized (lock) {
            lock.wait(15000); // 15秒超时
        }

        if (error[0] != null) {
            throw new Exception(error[0]);
        }

        return result[0];
    }

    /**
     * 遍历所有数字命名的数据库，找到包含指定uin的数据库
     */
    private String findDatabaseForUin(String targetUin) {
        try {
            Log.d(TAG, "查找包含uin " + targetUin + " 的数据库...");

            // 列出databases目录下所有.db文件
            ProcessBuilder lsProcess = new ProcessBuilder(
                    "su", "-c", "ls " + TARGET_DATABASE_DIR
            );
            Process p = lsProcess.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line;
            List<String> dbFiles = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                // 匹配数字.db文件，如 30215688.db, 72290087.db
                if (line.matches("\\d+\\.db")) {
                    dbFiles.add(line);
                }
            }
            reader.close();
            p.waitFor();

            Log.d(TAG, "找到 " + dbFiles.size() + " 个数字数据库: " + dbFiles);

            // 遍历每个数据库，检查是否包含目标uin
            Pattern numberPattern = Pattern.compile("(\\d+)\\.db");
            for (String dbFile : dbFiles) {
                Matcher m = numberPattern.matcher(dbFile);
                if (m.matches()) {
                    String dbPath = TARGET_DATABASE_DIR + dbFile;
                    Log.d(TAG, "检查数据库: " + dbPath);

                    // 检查这个数据库是否包含目标uin
                    if (checkUinInDatabase(dbPath, targetUin)) {
                        Log.d(TAG, "找到匹配！数据库: " + dbPath + " 包含uin " + targetUin);
                        foundDbPath = dbPath; // 保存找到的路径
                        return dbPath;
                    }
                }
            }

            Log.d(TAG, "未找到包含uin " + targetUin + " 的数据库");
            return null;

        } catch (Exception e) {
            Log.e(TAG, "查找数据库失败", e);
            return null;
        }
    }

    /**
     * 检查指定数据库是否包含目标uin（使用sqlite3命令行）
     */
    private boolean checkUinInDatabase(String dbPath, String targetUin) {
        try {
            // 使用sqlite3命令行直接查询，避免WAL模式问题
            String query = "SELECT COUNT(*) FROM Role WHERE f_uin = \"" + targetUin + "\"";
            String cmd = "su -c \"sqlite3 " + dbPath + " \\\"" + query + "\\\"\"";

            Log.d(TAG, "执行查询: " + cmd);

            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            String result = reader.readLine();
            reader.close();

            int count = 0;
            if (result != null) {
                try {
                    count = Integer.parseInt(result.trim());
                } catch (NumberFormatException e) {
                    Log.e(TAG, "解析结果失败: " + result);
                }
            }

            Log.d(TAG, "数据库 " + dbPath + " 包含 " + count + " 条uin=" + targetUin + " 的记录");
            return count > 0;

        } catch (Exception e) {
            Log.e(TAG, "检查uin失败: " + dbPath, e);
            return false;
        }
    }

    /**
     * 使用 su 命令复制游戏数据库（包括WAL模式的相关文件）
     */
    private boolean copyGameDatabaseWithSu(String dbPath) {
        try {
            Log.d(TAG, "复制游戏数据库: " + dbPath);

            // 获取不带扩展名的路径
            String basePath = dbPath.replaceAll("\\.db$", "");
            String walPath = basePath + "-wal";
            String shmPath = basePath + "-shm";

            // 检查源文件是否存在
            ProcessBuilder checkExist = new ProcessBuilder(
                    "su", "-c", "ls -la " + dbPath
            );
            Process p = checkExist.start();
            int exitCode = p.waitFor();

            if (exitCode != 0) {
                Log.e(TAG, "游戏数据库文件不存在, exit code: " + exitCode);
                return false;
            }

            // 同时复制主数据库文件、WAL文件和SHM文件
            String copyCmd = "su -c \"cat " + dbPath + " > " + TEMP_GAME_DB;
            // 如果存在WAL文件也复制
            copyCmd += " && if [ -f " + walPath + " ]; then cat " + walPath + " > " + TEMP_GAME_DB + "-wal; fi";
            copyCmd += " && if [ -f " + shmPath + " ]; then cat " + shmPath + " > " + TEMP_GAME_DB + "-shm; fi";
            copyCmd += " && chmod 666 " + TEMP_GAME_DB + " " + TEMP_GAME_DB + "-wal " + TEMP_GAME_DB + "-shm 2>/dev/null";
            copyCmd += "\"";

            Log.d(TAG, "执行命令: " + copyCmd);

            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", copyCmd});
            int result = process.waitFor();

            Log.d(TAG, "复制结果 exit code: " + result);

            // 验证文件是否复制成功
            File tempDb = new File(TEMP_GAME_DB);
            if (tempDb.exists()) {
                Log.d(TAG, "文件复制成功, 大小: " + tempDb.length());

                // 检查 WAL 文件
                File walFile = new File(TEMP_GAME_DB + "-wal");
                if (walFile.exists()) {
                    Log.d(TAG, "WAL文件复制成功, 大小: " + walFile.length());
                }

                return true;
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "复制游戏数据库失败", e);
            return false;
        }
    }

    /**
     * 读取游戏数据库（使用sqlite3命令行直接读取源数据库）
     * 返回所有匹配的角色信息
     */
    private RoleInfo readGameDatabase(Context context) {
        RoleInfo result = new RoleInfo();

        try {
            if (foundDbPath.isEmpty()) {
                Log.e(TAG, "未找到数据库路径");
                return null;
            }

            // 使用sqlite3命令行直接查询源数据库，返回所有匹配的角色
            String query = "SELECT f_serverId, f_areaId, f_areaName, f_roleId, f_roleName, f_roleJob, f_serverName, f_uin, f_stringLevel, f_gameName FROM Role WHERE f_uin = \"" + currentUin + "\"";
            String cmd = "su -c \"sqlite3 " + foundDbPath + " \\\"" + query + "\\\"\"";
            Log.d(TAG, "执行查询: " + cmd);

            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            // 读取所有行（每个角色一行）
            String line;
            boolean firstRole = true;
            while ((line = reader.readLine()) != null) {
                if (line != null && !line.isEmpty()) {
                    String[] fields = line.split("\\|");
                    if (fields.length >= 10) {
                        // 保存到allRoles列表
                        SingleRole role = new SingleRole();
                        role.area = fields[0];
                        role.areaId = fields[1];
                        role.areaName = fields[2];
                        role.roleid = fields[3];
                        role.playername = fields[4];
                        role.roleJob = fields[5];
                        role.serverName = fields[6];
                        role.roleLevel = fields[8];
                        result.allRoles.add(role);

                        // 第一个角色作为主角色
                        if (firstRole) {
                            result.area = fields[0];
                            result.areaId = fields[1];
                            result.areaName = fields[2];
                            result.roleid = fields[3];
                            result.playername = fields[4];
                            result.roleJob = fields[5];
                            result.serverName = fields[6];
                            result.uin = fields[7];
                            result.roleLevel = fields[8];
                            result.gameName = fields[9];
                            firstRole = false;
                        }

                        Log.d(TAG, "从数据库读取到角色: " + role.playername + " (" + role.roleJob + ", Lv." + role.roleLevel + ")");
                    }
                }
            }
            reader.close();

            if (!result.allRoles.isEmpty()) {
                Log.d(TAG, "共读取到 " + result.allRoles.size() + " 个角色");
                return result;
            }

            Log.d(TAG, "未找到匹配的角色数据");

        } catch (Exception e) {
            Log.e(TAG, "读取数据库失败", e);
        }

        return result;
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFile() {
        try {
            ProcessBuilder cleanup = new ProcessBuilder("rm", "-f", TEMP_GAME_DB, TEMP_GAME_DB + "-wal", TEMP_GAME_DB + "-shm");
            Process p = cleanup.start();
            p.waitFor();
            Log.d(TAG, "临时文件已清理");
        } catch (Exception e) {
            Log.e(TAG, "清理临时文件失败", e);
        }
    }
}
