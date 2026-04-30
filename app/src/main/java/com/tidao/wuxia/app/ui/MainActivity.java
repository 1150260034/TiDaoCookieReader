package com.tidao.wuxia.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.tidao.wuxia.app.AutomationReceiver;
import com.tidao.wuxia.app.BuildConfig;
import com.tidao.wuxia.app.R;
import com.tidao.wuxia.app.data.PrefsManager;
import com.tidao.wuxia.app.cookie.BindingChecker;
import com.tidao.wuxia.app.cookie.CookieExtractor;
import com.tidao.wuxia.app.cookie.GameDatabaseReader;
import com.tidao.wuxia.app.cookie.WebViewCookieReader;
import com.tidao.wuxia.app.net.FcUploader;
import com.tidao.wuxia.app.net.ServerChanBinder;
import com.tidao.wuxia.app.utils.RootChecker;
import com.tidao.wuxia.app.utils.UpdateChecker;

import org.json.JSONObject;

import java.io.File;

/**
 * 主界面 - Cookie读取器
 *
 * 功能：读取天刀助手的Cookie信息
 */
public class MainActivity extends Activity implements AutomationReceiver.AutomationListener {

    // 注意: TAG 已经在父类定义

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
    private Button btnCheckUpdate;
    private TextView tvStatus;
    private TextView tvLog;
    private TextView tvVersion;
    private ScrollView scrollLog;
    private TextView tvScKeyStatus;
    private TextView tvEmailStatus;

    // 数据
    private PrefsManager prefsManager;
    private CookieExtractor.CookieData cookieData;
    private GameDatabaseReader.RoleInfo roleInfo;
    private GameDatabaseReader gameDatabaseReader;
    private Handler mainHandler;
    private GameDatabaseReader.SingleRole selectedRole = null;
    private String dailyWelfareCheckResult = "";

    // 更新下载
    private long updateDownloadId = -1;
    private BroadcastReceiver downloadCompleteReceiver;
    private String updateReleasePageUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(getMainLooper());
        prefsManager = new PrefsManager(this);
        cookieData = new CookieExtractor.CookieData();
        roleInfo = new GameDatabaseReader.RoleInfo();
        gameDatabaseReader = new GameDatabaseReader();

        initViews();
        setupListeners();
        checkRootStatus();
        bindAutomationReceiver();
        checkForUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AutomationReceiver.clearListener();
        unregisterDownloadReceiver();
    }

    /**
     * 绑定自动化监听器，避免与清单中的接收器重复处理同一广播
     */
    private void bindAutomationReceiver() {
        AutomationReceiver.setListener(this);
        Log.d(TAG, "AutomationReceiver listener bound");
    }

    // ========== AutomationListener 实现 ==========

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public CookieExtractor.CookieData getCookieData() {
        return cookieData;
    }

    @Override
    public GameDatabaseReader.RoleInfo getRoleInfo() {
        return roleInfo;
    }

    @Override
    public String getDailyWelfareCheckResult() {
        return dailyWelfareCheckResult;
    }

    @Override
    public void performReadCookie() {
        readWebViewCookie();
    }

    @Override
    public void performCopyAll() {
        if (cookieData.accessToken.isEmpty() && cookieData.openid.isEmpty()) {
            Log.i("TidaoResult", "[ERROR] No cookie data available");
            return;
        }
        copyAll();
    }

    @Override
    public void performCheckWelfare() {
        if (cookieData.openid.isEmpty()) {
            Log.i("TidaoResult", "[ERROR] No cookie data, please read cookie first");
            return;
        }
        checkDailyWelfareStatus();
    }

    @Override
    public void performUploadCookie() {
        uploadToCloud();
    }

    private void initViews() {
        btnInstallTiandao = findViewById(R.id.btn_install_tiandao);
        btnOpenTianDao = findViewById(R.id.btn_open_tiandao);
        btnReadCookie = findViewById(R.id.btn_read_cookie);
        btnCopyAll = findViewById(R.id.btn_copy_all);
        btnCheckUpdate = findViewById(R.id.btn_check_update);
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
        tvScKeyStatus = findViewById(R.id.tv_sckey_status);
        tvEmailStatus = findViewById(R.id.tv_email_status);
        updateScKeyStatus();
        updateEmailStatus();
    }

    private void updateScKeyStatus() {
        if (prefsManager.hasSckey()) {
            tvScKeyStatus.setText("Server酱：已绑定 ✓");
            tvScKeyStatus.setTextColor(0xFF00CC66);
        } else {
            tvScKeyStatus.setText("Server酱：未绑定");
            tvScKeyStatus.setTextColor(0xFFFF6666);
        }
    }

    private void updateEmailStatus() {
        if (prefsManager.hasEmail()) {
            tvEmailStatus.setText("邮箱提醒：已绑定 " + prefsManager.getEmail());
            tvEmailStatus.setTextColor(0xFF00CC66);
        } else {
            tvEmailStatus.setText("邮箱提醒：未绑定（可选）");
            tvEmailStatus.setTextColor(0xFF888888);
        }
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
        btnCopyAll.setOnClickListener(v -> uploadToCloud());
        btnCopyAll.setOnLongClickListener(v -> {
            copyAll();
            return true;
        });
        btnCheckUpdate.setOnClickListener(v -> checkForUpdatesManual());
        tvScKeyStatus.setOnClickListener(v -> {
            if (prefsManager.hasSckey()) {
                showUnbindScKeyDialog();
            } else {
                Toast.makeText(this, "尚未绑定 Server酱", Toast.LENGTH_SHORT).show();
            }
        });
        tvEmailStatus.setOnClickListener(v -> showEmailDialog());
    }

    private void showUnbindScKeyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("取消绑定")
                .setMessage("确定取消绑定 Server酱吗？\n解绑后上传 Cookie 需重新绑定。")
                .setCancelable(false)
                .setPositiveButton("确定", (dialog, which) -> {
                    prefsManager.clearSckey();
                    updateScKeyStatus();
                    appendLog("已取消绑定 Server酱");
                    Toast.makeText(this, "已取消绑定 Server酱", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEmailDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint("example@qq.com");
        input.setText(prefsManager.getEmail());
        input.setSelection(input.getText().length());
        int padding = dp(20);
        input.setPadding(padding, padding / 2, padding, padding / 2);

        new AlertDialog.Builder(this)
                .setTitle("邮箱提醒（可选）")
                .setMessage("填写后，Cookie 失效时会额外发送邮件。上传时会先发送测试邮件验证。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String email = input.getText().toString().trim();
                    if (!email.isEmpty() && !isValidEmailAddress(email)) {
                        Toast.makeText(this, "邮箱格式不正确", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefsManager.saveEmail(email);
                    updateEmailStatus();
                    appendLog(email.isEmpty() ? "已清空邮箱提醒" : "已保存邮箱提醒：" + email);
                })
                .setNeutralButton("清除", (dialog, which) -> {
                    prefsManager.clearEmail();
                    updateEmailStatus();
                    appendLog("已清空邮箱提醒");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static int dp(int value) {
        return Math.round(value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
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
        appendLog("5. 点「上传到云端」");
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
                    roleInfo.areaId = data.areaId;
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
                            roleInfo.areaId = role.areaId;
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
            roleInfo.areaId = selectedRole.areaId;
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
                    updateStatus("读取成功！点「上传到云端」");
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
                    updateStatus("读取成功！点「上传到云端」");
                    updateButtons(true);
                    Toast.makeText(MainActivity.this, "读取完成!", Toast.LENGTH_SHORT).show();
                });
            }
        });

        // 执行绑定检测
        BindingChecker.checkBindingStatus(this, cookieData);
    }

    /**
     * 手动触发更新检查（按钮点击），三态回调：
     *   - 发现更新：弹出更新对话框
     *   - 无更新：Toast 提示当前已是最新
     *   - 检查失败：恢复按钮状态、写入日志、弹出失败对话框（提供手动打开下载页兜底）
     */
    private void checkForUpdatesManual() {
        btnCheckUpdate.setEnabled(false);
        btnCheckUpdate.setText(getString(R.string.checking_update));
        try {
            String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            UpdateChecker.checkForUpdates(this, currentVersion,
                    (latestVersion, releasePageUrl, apkDownloadUrl) -> {
                        btnCheckUpdate.setEnabled(true);
                        btnCheckUpdate.setText(getString(R.string.check_update));
                        showUpdateDialog(latestVersion, releasePageUrl, apkDownloadUrl);
                    },
                    () -> {
                        btnCheckUpdate.setEnabled(true);
                        btnCheckUpdate.setText(getString(R.string.check_update));
                        Toast.makeText(this, getString(R.string.latest_version), Toast.LENGTH_SHORT).show();
                    },
                    reason -> {
                        btnCheckUpdate.setEnabled(true);
                        btnCheckUpdate.setText(getString(R.string.check_update));
                        appendLog("检查更新失败: " + reason);
                        showCheckUpdateFailedDialog();
                    });
        } catch (Exception e) {
            Log.e(TAG, "检查更新失败", e);
            btnCheckUpdate.setEnabled(true);
            btnCheckUpdate.setText(getString(R.string.check_update));
            Toast.makeText(this, getString(R.string.check_update_failed), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查是否有新版本（静默，失败不提示）
     */
    private void checkForUpdates() {
        try {
            String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            UpdateChecker.checkForUpdates(this, currentVersion,
                    (latestVersion, releasePageUrl, apkDownloadUrl) ->
                            showUpdateDialog(latestVersion, releasePageUrl, apkDownloadUrl),
                    null);
        } catch (Exception e) {
            // 获取版本号失败时静默忽略，不影响主流程
        }
    }

    /**
     * 弹出更新提示对话框
     *
     * @param latestVersion  最新版本号
     * @param releasePageUrl GitHub Releases 页面（兜底）
     * @param apkDownloadUrl APK 直链；为空时只显示「打开下载页」
     */
    private void showUpdateDialog(String latestVersion, String releasePageUrl, String apkDownloadUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("发现新版本 v" + latestVersion)
                .setMessage("当前版本较旧，建议更新到最新版本。")
                .setNegativeButton("忽略", null);

        if (!apkDownloadUrl.isEmpty()) {
            builder.setPositiveButton("直接下载安装", (dialog, which) ->
                    startApkDownload(apkDownloadUrl, releasePageUrl));
            builder.setNeutralButton("打开下载页", (dialog, which) ->
                    openReleasePage(releasePageUrl));
        } else {
            builder.setPositiveButton("打开下载页", (dialog, which) ->
                    openReleasePage(releasePageUrl));
        }

        builder.show();
    }

    /**
     * 打开 GitHub Releases 页面（兜底路径）
     */
    private void openReleasePage(String releasePageUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(releasePageUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "打开链接失败，请手动访问：" + releasePageUrl, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 使用 DownloadManager 下载 APK 并在完成后触发安装；失败时给出兜底链接
     */
    private void startApkDownload(String apkDownloadUrl, String releasePageUrl) {
        // 检查是否允许安装未知来源应用
        if (!getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要开启「安装未知应用」权限")
                    .setMessage("请在设置中允许本应用安装 APK，或前往下载页手动安装。")
                    .setPositiveButton("前往设置", (d, w) -> {
                        try {
                            Intent intent = new Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            openReleasePage(releasePageUrl);
                        }
                    })
                    .setNeutralButton("打开下载页", (d, w) -> openReleasePage(releasePageUrl))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        try {
            // 清理上次残留的 APK 文件（若外部存储可用）
            File externalDir = getExternalFilesDir(null);
            if (externalDir != null) {
                File destFile = new File(externalDir, "update.apk");
                if (destFile.exists()) destFile.delete();
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkDownloadUrl));
            request.setTitle("天刀Cookie助手 新版本下载中...");
            request.setDescription("正在下载更新包，请稍候");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            // 声明 APK 的 MIME 类型，确保系统下载器/安装器按安装包识别和处理
            request.setMimeType("application/vnd.android.package-archive");
            request.setDestinationInExternalFilesDir(this, null, "update.apk");

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            updateDownloadId = dm.enqueue(request);

            Toast.makeText(this, "开始下载更新，完成后将自动提示安装", Toast.LENGTH_SHORT).show();
            appendLog("正在下载新版本...");

            // 注册下载完成广播，同时缓存 releasePageUrl 供安装失败时兜底使用
            updateReleasePageUrl = releasePageUrl;
            registerDownloadReceiver(releasePageUrl);

        } catch (Exception e) {
            Log.w(TAG, "DownloadManager 启动失败: " + e.getMessage());
            showDownloadFailedDialog(releasePageUrl);
        }
    }

    /**
     * 注册下载完成广播接收器
     */
    private void registerDownloadReceiver(String releasePageUrl) {
        unregisterDownloadReceiver(); // 避免重复注册
        downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != updateDownloadId) return;

                try {
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(id);
                    Cursor cursor = dm.query(query);
                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = (statusIdx >= 0) ? cursor.getInt(statusIdx) : -1;
                        cursor.close();

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            installDownloadedApk();
                        } else {
                            appendLog("下载失败（状态码: " + status + "），请手动下载");
                            showDownloadFailedDialog(releasePageUrl);
                        }
                    } else {
                        if (cursor != null) cursor.close();
                        showDownloadFailedDialog(releasePageUrl);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "处理下载完成事件失败: " + e.getMessage());
                    showDownloadFailedDialog(releasePageUrl);
                }
            }
        };
        registerReceiverCompat(downloadCompleteReceiver,
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), true);
    }

    private void registerReceiverCompat(BroadcastReceiver receiver, IntentFilter filter,
                                        boolean exported) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter,
                    exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED);
            return;
        }
        registerReceiver(receiver, filter);
    }

    /**
     * 注销下载完成广播接收器
     */
    private void unregisterDownloadReceiver() {
        if (downloadCompleteReceiver != null) {
            try {
                unregisterReceiver(downloadCompleteReceiver);
            } catch (Exception e) {
                // 未注册时忽略
            }
            downloadCompleteReceiver = null;
        }
    }

    /**
     * 触发系统安装器安装已下载的 APK
     */
    private void installDownloadedApk() {
        try {
            File externalDir = getExternalFilesDir(null);
            if (externalDir == null) {
                appendLog("无法获取外部存储目录，安装失败");
                showDownloadFailedDialog(updateReleasePageUrl);
                return;
            }
            File apkFile = new File(externalDir, "update.apk");
            if (!apkFile.exists()) {
                appendLog("安装包文件不存在，请重试");
                showDownloadFailedDialog(updateReleasePageUrl);
                return;
            }
            Uri apkUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", apkFile);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(installIntent);
            appendLog("安装包下载完成，请按提示安装");
        } catch (Exception e) {
            Log.w(TAG, "触发安装失败: " + e.getMessage());
            appendLog("安装失败: " + e.getMessage());
            showDownloadFailedDialog(updateReleasePageUrl);
        }
    }

    /**
     * 下载或安装失败时弹出兜底对话框
     */
    private void showDownloadFailedDialog(String releasePageUrl) {
        new AlertDialog.Builder(this)
                .setTitle("下载失败")
                .setMessage("下载更新包失败，请前往发布页面手动下载安装。\n\n" + releasePageUrl)
                .setPositiveButton("打开下载页", (d, w) -> openReleasePage(releasePageUrl))
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 手动检查更新失败时弹出兜底对话框，提供手动打开发布页的路径
     */
    private void showCheckUpdateFailedDialog() {
        String releasePageUrl = UpdateChecker.getReleasesPageUrl();
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.check_update_failed))
                .setMessage(getString(R.string.check_update_failed_message))
                .setPositiveButton(getString(R.string.open_release_page), (d, w) -> openReleasePage(releasePageUrl))
                .setNegativeButton(getString(R.string.cancel_text), null)
                .show();
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

    /**
     * 上传 Cookie 到云端（强制 sckey 绑定）
     */
    private void uploadToCloud() {
        if (cookieData == null || !cookieData.isComplete()) {
            Toast.makeText(this, "请先读取 Cookie", Toast.LENGTH_SHORT).show();
            return;
        }
        String uploadUrl = BuildConfig.UPLOAD_COOKIE_URL;
        if (uploadUrl == null || uploadUrl.isEmpty()) {
            Toast.makeText(this, "未配置上传地址，请使用 CI 构建版本", Toast.LENGTH_LONG).show();
            return;
        }
        if (roleInfo == null || !roleInfo.isComplete()) {
            Toast.makeText(this, "角色信息不完整，请重新读取", Toast.LENGTH_SHORT).show();
            return;
        }
        String accountName = roleInfo.playername;
        if (accountName == null || accountName.isEmpty()) {
            accountName = cookieData.rolename;
        }
        if (accountName == null || accountName.isEmpty()) {
            Toast.makeText(this, "无法确定角色名，请重新读取", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!prefsManager.hasSckey()) {
            final String finalName = accountName;
            ServerChanBinder.startBinding(this, prefsManager, mainHandler, new ServerChanBinder.BindCallback() {
                @Override
                public void onSuccess(String sckey) {
                    updateScKeyStatus();
                    appendLog("Server酱绑定成功，开始上传...");
                    if (!isSavedEmailValidForUpload()) {
                        return;
                    }
                    doUpload(finalName);
                }

                @Override
                public void onFailed(String reason) {
                    appendLog("Server酱绑定失败：" + reason);
                    Toast.makeText(MainActivity.this, "绑定失败：" + reason, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCancelled() {
                    appendLog("Server酱绑定已取消，上传中止");
                    Toast.makeText(MainActivity.this, "已取消，上传中止", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        if (!isSavedEmailValidForUpload()) {
            return;
        }
        doUpload(accountName);
    }

    private boolean isSavedEmailValidForUpload() {
        String email = prefsManager.getEmail();
        if (email != null && !email.trim().isEmpty() && !isValidEmailAddress(email)) {
            Toast.makeText(this, "邮箱格式不正确，请先修改或清空", Toast.LENGTH_SHORT).show();
            appendLog("邮箱格式不正确，上传中止");
            return false;
        }
        return true;
    }

    static boolean isValidEmailAddress(String email) {
        String value = email == null ? "" : email.trim();
        if (value.isEmpty() || value.length() > 254) {
            return false;
        }
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || at >= value.length() - 1) {
            return false;
        }
        String domain = value.substring(at + 1);
        return domain.contains(".") && !value.matches(".*\\s+.*");
    }

    /**
     * 执行实际上传（sckey 已确保存在）
     */
    private void doUpload(String accountName) {
        btnCopyAll.setEnabled(false);
        btnCopyAll.setText("上传中...");
        appendLog("正在上传 Cookie...");
        try {
            JSONObject roleParams = new JSONObject();
            roleParams.put("area", roleInfo.area);
            roleParams.put("playername", roleInfo.playername);
            roleParams.put("roleid", roleInfo.roleid);
            String rawUin = cookieData.uin;
            if (rawUin != null && rawUin.startsWith("o0")) {
                rawUin = rawUin.substring(2);
            }
            roleParams.put("uin", rawUin != null ? rawUin : "");
            roleParams.put("roleLevel", roleInfo.roleLevel);
            roleParams.put("roleJob", roleInfo.roleJob);
            roleParams.put("serverName", roleInfo.serverName);
            roleParams.put("areaName", roleInfo.areaName);
            roleParams.put("areaId", roleInfo.areaId);

            FcUploader.upload(accountName, cookieData.toCookieString(), roleParams,
                    prefsManager.getSckey(), prefsManager.getOwner(), prefsManager.getEmail(), mainHandler, new FcUploader.UploadCallback() {
                        @Override
                        public void onSuccess(String status, String name) {
                            btnCopyAll.setEnabled(true);
                            btnCopyAll.setText("③ 上传到云端");
                            String msg;
                            if ("updated".equals(status)) {
                                msg = "Cookie 已更新：" + name;
                            } else if ("pending_approval".equals(status)) {
                                msg = "新账号，待管理员审批：" + name;
                            } else {
                                msg = "上传成功：" + status;
                            }
                            appendLog("✓ " + msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onFailed(String message) {
                            btnCopyAll.setEnabled(true);
                            btnCopyAll.setText("③ 上传到云端");
                            // sendkey 失效时自动解绑，让用户重新点击上传按钮绑定新 key
                            if (message != null && (message.contains("sendkey validation failed")
                                    || message.contains("invalid_key"))) {
                                prefsManager.clearSckey();
                                updateScKeyStatus();
                                appendLog("✗ Server酱 sendkey 已失效，已自动解绑，请重新上传");
                                Toast.makeText(MainActivity.this,
                                        "Server酱 sendkey 已失效，请重新点击「上传到云端」按钮",
                                        Toast.LENGTH_LONG).show();
                            } else if (message != null && message.contains("email validation failed")) {
                                appendLog("✗ 邮箱测试发送失败，请检查邮箱地址或稍后重试");
                                Toast.makeText(MainActivity.this,
                                        "邮箱测试发送失败，请检查邮箱地址或稍后重试",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                appendLog("✗ 上传失败：" + message);
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        } catch (Exception e) {
            btnCopyAll.setEnabled(true);
            btnCopyAll.setText("③ 上传到云端");
            appendLog("✗ 构建请求失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            Toast.makeText(this, "构建请求失败", Toast.LENGTH_SHORT).show();
        }
    }
}
