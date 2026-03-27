package com.tidao.wuxia.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tidao.wuxia.app.R;
import com.tidao.wuxia.app.cookie.BindingChecker;
import com.tidao.wuxia.app.cookie.CookieExtractor;
import com.tidao.wuxia.app.cookie.GameDatabaseReader;
import com.tidao.wuxia.app.cookie.WebViewCookieReader;
import com.tidao.wuxia.app.utils.RootChecker;

/**
 * 主界面 - Cookie读取器
 *
 * 功能：读取天刀助手的Cookie信息
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // 天刀助手包名
    private static final String TIADAO_PACKAGE = "com.tencent.gamehelper.wuxia";

    // 应用宝下载地址
    private static final String APP_SO_URL = "https://sj.qq.com/appdetail/com.tencent.gamehelper.wuxia";

    // UI 组件
    private Button btnInstallTiandao;
    private Button btnOpenTianDao;
    private Button btnReadCookie;
    private Button btnCopyAll;
    private TextView tvStatus;
    private TextView tvLog;
    private TextView tvVersion;
    private ScrollView scrollLog;

    // 数据
    private CookieExtractor.CookieData cookieData;
    private GameDatabaseReader.RoleInfo roleInfo;
    private GameDatabaseReader gameDatabaseReader;
    private Handler mainHandler;
    private GameDatabaseReader.SingleRole selectedRole = null;
    private String dailyWelfareCheckResult = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(getMainLooper());
        cookieData = new CookieExtractor.CookieData();
        roleInfo = new GameDatabaseReader.RoleInfo();
        gameDatabaseReader = new GameDatabaseReader();

        initViews();
        setupListeners();
        checkRootStatus();
    }

    private void initViews() {
        btnInstallTiandao = findViewById(R.id.btn_install_tiandao);
        btnOpenTianDao = findViewById(R.id.btn_open_tiandao);
        btnReadCookie = findViewById(R.id.btn_read_cookie);
        btnCopyAll = findViewById(R.id.btn_copy_all);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        tvVersion = findViewById(R.id.tv_version);
        scrollLog = findViewById(R.id.scroll_log);

        // 显示版本号
        try {
            android.content.pm.PackageInfo pkgInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText("v" + pkgInfo.versionName);
        } catch (Exception e) {
            tvVersion.setText("v1.0");
        }

        updateStatus("准备就绪");
        updateButtons(false);
    }

    /**
     * 检测Root状态
     */
    private void checkRootStatus() {
        boolean isRooted = RootChecker.isDeviceRooted();

        if (!isRooted) {
            appendLog("⚠️ 警告: 未检测到Root权限!");
            appendLog("请在模拟器设置中开启Root");
            appendLog("否则无法读取Cookie");
            updateStatus("⚠️ 需要开启Root");

            // 禁用读取按钮
            btnReadCookie.setEnabled(false);
            btnReadCookie.setText("需要Root权限");

            Toast.makeText(this, "请先开启模拟器Root权限", Toast.LENGTH_LONG).show();
        } else {
            appendLog("✓ Root权限检测正常");
            showTutorial();
        }
    }

    private void setupListeners() {
        btnInstallTiandao.setOnClickListener(v -> installTiandao());
        btnOpenTianDao.setOnClickListener(v -> openTianDao());
        btnReadCookie.setOnClickListener(v -> readWebViewCookie());
        btnCopyAll.setOnClickListener(v -> copyAll());
    }

    /**
     * 显示使用教程
     */
    private void showTutorial() {
        appendLog("===== 使用教程 =====");
        appendLog("1. 首次需要安装天刀助手");
        appendLog("2. 扫码登录QQ");
        appendLog("3. 点击「周周载愿」按钮");
        appendLog("4. 点「读取Cookie」");
        appendLog("5. 点「复制全部」");
        appendLog("===== 每日福利 =====");
        appendLog("首次使用需绑定角色:");
        appendLog("1. 进入「每日福利」活动");
        appendLog("2. 等待加载完成后点绑定角色");
        appendLog("3. 选择角色后点确认");
        appendLog("====================");
    }

    /**
     * 打开应用宝下载天刀助手
     */
    private void installTiandao() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(APP_SO_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            appendLog("正在打开应用宝...");
        } catch (Exception e) {
            Toast.makeText(this, "打开失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            appendLog("打开应用宝失败");
        }
    }

    /**
     * 打开天刀助手
     */
    private void openTianDao() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(TIADAO_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                appendLog("已打开天刀助手");
            } else {
                Toast.makeText(this, "未找到天刀助手，请先安装", Toast.LENGTH_SHORT).show();
                appendLog("错误: 未找到天刀助手");
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            appendLog("打开天刀助手失败");
        }
    }

    /**
     * 读取WebView Cookie
     */
    private void readWebViewCookie() {
        appendLog("正在读取 Cookie...");
        updateStatus("读取中...");

        WebViewCookieReader.readCookies(this, new WebViewCookieReader.OnCookieReadListener() {
            @Override
            public void onCookieReadSuccess(WebViewCookieReader.CookieData data) {
                mainHandler.post(() -> {
                    // 转换为 CookieExtractor.CookieData 格式
                    cookieData.accessToken = data.accessToken;
                    cookieData.openid = data.openid;
                    cookieData.uin = data.uin;
                    cookieData.appid = data.appid;
                    cookieData.acctype = data.acctype;
                    cookieData.eas_sid = data.eas_sid;
                    cookieData.appOpenId = data.appOpenId;

                    appendLog("Cookie读取成功!");
                    appendLog("access_token: " + data.accessToken.substring(0, Math.min(15, data.accessToken.length())) + "...");
                    appendLog("openid: " + data.openid.substring(0, Math.min(15, data.openid.length())) + "...");
                    appendLog("uin: " + data.uin);
                    appendLog("💡 提示：切换账号后请重启天刀助手避免读取旧Cookie");
                    appendLog("💡 每日福利首次使用需先人工绑定角色");

                    // 开始读取角色信息（传入uin以找到正确的数据库）
                    readRoleInfo(data.uin);
                });
            }

            @Override
            public void onCookieReadFailed(String error) {
                mainHandler.post(() -> {
                    appendLog("读取失败: " + error);
                    updateStatus("读取失败");
                    Toast.makeText(MainActivity.this, "读取失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 复制全部Cookie信息
     */
    private void copyAll() {
        if (cookieData.accessToken.isEmpty() && cookieData.openid.isEmpty()) {
            Toast.makeText(this, "还没有读取到 Cookie", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();

        // 添加 Cookie
        String cookie = cookieData.toCookieString();
        sb.append("【Cookie】\n").append(cookie);

        // 添加角色信息
        if (roleInfo.isComplete()) {
            sb.append("\n\n【角色信息】\n");
            sb.append("area=").append(roleInfo.area);
            if (!roleInfo.areaName.isEmpty()) sb.append(" (").append(roleInfo.areaName).append(")");
            sb.append("\n");
            sb.append("playername=").append(roleInfo.playername).append("\n");
            sb.append("roleid=").append(roleInfo.roleid).append("\n");
            if (!roleInfo.roleLevel.isEmpty()) {
                sb.append("roleLevel=").append(roleInfo.roleLevel).append("\n");
            }
            if (!roleInfo.roleJob.isEmpty()) {
                sb.append("roleJob=").append(roleInfo.roleJob).append("\n");
            }
        }

        // 添加每日福利检测结论
        if (!dailyWelfareCheckResult.isEmpty()) {
            sb.append("\n\n【每日福利检测】：").append(dailyWelfareCheckResult);
        }

        copyToClipboard(sb.toString(), "Cookie + 角色信息 + 每日福利检测");
        appendLog("已复制Cookie和角色信息到剪贴板");
        Toast.makeText(this, "已复制!", Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    /**
     * 读取角色信息
     * @param uin 当前登录账号的uin，用于找到正确的数据库
     */
    private void readRoleInfo(String uin) {
        appendLog("正在读取角色信息...");
        updateStatus("读取角色信息中...");

        // 设置当前uin，用于查找正确的数据库
        gameDatabaseReader.setCurrentUin(uin);

        gameDatabaseReader.readRoleInfo(this, new GameDatabaseReader.OnRoleInfoReadListener() {
            @Override
            public void onRoleInfoReadSuccess(GameDatabaseReader.RoleInfo data) {
                mainHandler.post(() -> {
                    // 先保存所有角色信息
                    roleInfo.area = data.area;
                    roleInfo.areaName = data.areaName;
                    roleInfo.uin = data.uin;
                    roleInfo.serverName = data.serverName;
                    roleInfo.gameName = data.gameName;
                    roleInfo.allRoles = data.allRoles;

                    // 检查是否有多个角色需要选择
                    if (data.allRoles != null && data.allRoles.size() > 1) {
                        showRoleSelectionDialog(data.allRoles);
                    } else {
                        // 只有一个角色，直接使用
                        if (data.allRoles != null && data.allRoles.size() == 1) {
                            GameDatabaseReader.SingleRole role = data.allRoles.get(0);
                            roleInfo.playername = role.playername;
                            roleInfo.roleid = role.roleid;
                            roleInfo.roleLevel = role.roleLevel;
                            roleInfo.roleJob = role.roleJob;
                            roleInfo.serverName = role.serverName;
                        }
                        onRoleInfoSelected();
                    }
                });
            }

            @Override
            public void onRoleInfoReadFailed(String error) {
                mainHandler.post(() -> {
                    appendLog("角色信息读取失败: " + error);
                    updateStatus("⚠️ 角色信息读取失败");
                    // 即使角色信息读取失败，也允许复制Cookie
                    updateButtons(true);
                });
            }
        });
    }

    /**
     * 弹出角色选择对话框（单选）
     */
    private void showRoleSelectionDialog(java.util.List<GameDatabaseReader.SingleRole> roles) {
        String[] items = new String[roles.size()];

        for (int i = 0; i < roles.size(); i++) {
            GameDatabaseReader.SingleRole role = roles.get(i);
            String serverInfo = role.areaName.isEmpty() ? role.area : role.areaName;
            items[i] = role.playername + " [" + serverInfo + "-" + role.serverName + "] (" + role.roleJob + ", Lv." + role.roleLevel + ")";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择角色");
        builder.setSingleChoiceItems(items, -1, (dialog, which) -> {
            selectedRole = roles.get(which);
        });
        builder.setPositiveButton("确定", (dialog, which) -> {
            if (selectedRole == null) {
                Toast.makeText(this, "请选择一个角色", Toast.LENGTH_SHORT).show();
                showRoleSelectionDialog(roles);
                return;
            }

            roleInfo.playername = selectedRole.playername;
            roleInfo.roleid = selectedRole.roleid;
            roleInfo.roleLevel = selectedRole.roleLevel;
            roleInfo.roleJob = selectedRole.roleJob;
            roleInfo.area = selectedRole.area;
            roleInfo.areaName = selectedRole.areaName;
            roleInfo.serverName = selectedRole.serverName;

            onRoleInfoSelected();
        });
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * 角色选择完成后的处理
     */
    private void onRoleInfoSelected() {
        appendLog("角色信息读取成功!");
        if (!roleInfo.area.isEmpty()) {
            appendLog("area: " + roleInfo.area + " (" + roleInfo.areaName + ")");
        }
        if (!roleInfo.playername.isEmpty()) {
            appendLog("playername: " + roleInfo.playername);
        }
        if (!roleInfo.roleid.isEmpty()) {
            appendLog("roleid: " + roleInfo.roleid);
        }
        if (!roleInfo.roleLevel.isEmpty()) {
            appendLog("roleLevel: " + roleInfo.roleLevel);
        }
        if (!roleInfo.roleJob.isEmpty()) {
            appendLog("roleJob: " + roleInfo.roleJob);
        }

        // 将角色信息同步到 cookieData（解决 BindingChecker iRet=101 问题）
        cookieData.roleid = roleInfo.roleid;
        cookieData.rolename = roleInfo.playername;
        cookieData.rolelevel = roleInfo.roleLevel;
        cookieData.areaid = roleInfo.area;
        cookieData.userId = roleInfo.uin;

        // 开始检测每日福利绑定状态
        checkDailyWelfareStatus();
    }

    /**
     * 检测每日福利绑定状态
     */
    private void checkDailyWelfareStatus() {
        appendLog("正在检测每日福利绑定状态...");
        updateStatus("检测每日福利...");

        // 设置绑定检测回调
        BindingChecker.setOnBindingCheckListener(new BindingChecker.OnBindingCheckListener() {
            @Override
            public void onBindingCheckSuccess(boolean isBound, String area, String areaName, String roleName) {
                mainHandler.post(() -> {
                    if (isBound) {
                        dailyWelfareCheckResult = "已绑定 | 角色: " + roleName + " | 区服: " + areaName;
                        appendLog("每日福利检测: 已绑定 (" + roleName + ")");
                    } else {
                        dailyWelfareCheckResult = "未绑定";
                        appendLog("每日福利检测: 未绑定");
                    }
                    updateStatus("读取成功！点「复制全部」");
                    updateButtons(true);
                    Toast.makeText(MainActivity.this, "读取完成!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onBindingCheckFailed(String error, boolean isCookieExpired) {
                mainHandler.post(() -> {
                    if (isCookieExpired) {
                        dailyWelfareCheckResult = "Cookie失效: " + error;
                        appendLog("每日福利检测失败: Cookie失效");
                    } else {
                        dailyWelfareCheckResult = "检测失败: " + error;
                        appendLog("每日福利检测失败: " + error);
                    }
                    // 即使检测失败也允许复制（可能实际可用）
                    updateStatus("读取成功！点「复制全部」");
                    updateButtons(true);
                    Toast.makeText(MainActivity.this, "读取完成!", Toast.LENGTH_SHORT).show();
                });
            }
        });

        // 执行绑定检测
        BindingChecker.checkBindingStatus(this, cookieData);
    }

    private void updateStatus(String status) {
        tvStatus.setText(status);
    }

    private void updateButtons(boolean hasCookie) {
        btnReadCookie.setEnabled(true); // 始终可点击
        btnCopyAll.setEnabled(hasCookie);
    }

    private void appendLog(String message) {
        String current = tvLog.getText().toString();
        if (!current.isEmpty()) {
            current += "\n";
        }
        tvLog.setText(current + "[" + getCurrentTime() + "] " + message);
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private String getCurrentTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }
}
