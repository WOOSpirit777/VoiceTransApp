package commonutil;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * PermissionUtils - 检查一组系统/受保护权限的授予状态。
 *
 * 支持通过 checkSelfPermission（运行时权限）和 AppOps 检查（如 PACKAGE_USAGE_STATS / 读取使用情况等）。
 * 注意：某些权限（privileged / signature）即使声明也只能授予给系统或 Device Owner / priv-app。
 */
public final class PermissionUtils {

    private PermissionUtils() {}

    // 常用要检测的权限常量
    public static final String PERM_INSTALL_PACKAGES = "android.permission.INSTALL_PACKAGES";
    public static final String PERM_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE";
    public static final String PERM_READ_SYNC_SETTINGS = "android.permission.READ_SYNC_SETTINGS";
    public static final String PERM_REQUEST_INSTALL_PACKAGES = "android.permission.REQUEST_INSTALL_PACKAGES";
    public static final String PERM_READ_NETWORK_USAGE_HISTORY = "android.permission.READ_NETWORK_USAGE_HISTORY";
    public static final String PERM_READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE";
    public static final String PERM_PACKAGE_USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS";
    public static final String PERM_MODIFY_PHONE_STATE = "android.permission.MODIFY_PHONE_STATE";
    public static final String PERM_WRITE_EMBEDDED_SUBSCRIPTIONS = "android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS";
    public static final String PERM_WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS";

    /**
     * 检查单个权限是否被授予。
     * 逻辑：先使用 checkSelfPermission（API 23+），若未授予则尝试通过 AppOps 检查对应的 op（如果存在）。
     * 对于 PACKAGE_USAGE_STATS 使用专用的 AppOps 字符串进行检查以获得更可靠的结果。
     *
     * @param context 上下文
     * @param permission 权限名称
     * @return true 表示认为已授予，false 表示未授予
     */
    public static boolean isPermissionGranted(@NonNull Context context, @NonNull String permission) {
        // 1) 普通运行时权限检查（API 23+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // 某些 OEM/ROM 在检查未声明权限时可能抛异常，继续尝试 AppOps 检查
            }
        } else {
            // API < 23：安装时授予（除非特殊平台限制），这里认为授予
            return true;
        }

        // 2) AppOps 检查（如果该权限映射为一个 app-op）
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps != null) {
            // PACKAGE_USAGE_STATS 的 AppOps 字符串为 OPSTR_GET_USAGE_STATS
            if (PERM_PACKAGE_USAGE_STATS.equals(permission)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
                    return mode == AppOpsManager.MODE_ALLOWED;
                }
            }

            // 一般权限 -> 通过 permissionToOp 映射到 op 再检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String op = AppOpsManager.permissionToOp(permission);
                if (op != null) {
                    int mode = appOps.checkOpNoThrow(op, Process.myUid(), context.getPackageName());
                    return mode == AppOpsManager.MODE_ALLOWED;
                }
            }
        }

        // 若以上检查都不认为被授予，则认为未授予
        return false;
    }

    /**
     * 批量检查权限，返回每个权限的授予状态。
     * @param context 上下文
     * @param permissions 要检查的权限数组
     * @return Map: permission -> boolean
     */
    @NonNull
    public static Map<String, Boolean> checkPermissions(@NonNull Context context, @NonNull String[] permissions) {
        Map<String, Boolean> result = new HashMap<>();
        for (String p : permissions) {
            result.put(p, isPermissionGranted(context, p));
        }
        return result;
    }

    // 针对用户关注的五个权限提供便捷方法
    public static boolean hasReadNetworkUsageHistory(@NonNull Context context) {
        return isPermissionGranted(context, PERM_READ_NETWORK_USAGE_HISTORY);
    }

    public static boolean hasReadPrivilegedPhoneState(@NonNull Context context) {
        return isPermissionGranted(context, PERM_READ_PRIVILEGED_PHONE_STATE);
    }

    public static boolean hasPackageUsageStats(@NonNull Context context) {
        return isPermissionGranted(context, PERM_PACKAGE_USAGE_STATS);
    }

    public static boolean hasModifyPhoneState(@NonNull Context context) {
        return isPermissionGranted(context, PERM_MODIFY_PHONE_STATE);
    }

    public static boolean hasWriteEmbeddedSubscriptions(@NonNull Context context) {
        return isPermissionGranted(context, PERM_WRITE_EMBEDDED_SUBSCRIPTIONS);
    }

}

