package com.mtrdisableoriginaltrains;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 保存列车选择后的「是否立即重启」提示配置。
 *
 * <p>与主配置同一个文件 {@code mtr_no_original_trains.json}，本类只管两个字段：
 * <pre>
 * {
 *   "askRestart": true,      // 保存后是否弹「是否重启」。false = 已勾选「不再显示」
 *   "autoRestart": false     // 不再弹窗时执行什么：true = 直接结束进程 / false = 直接回主菜单
 * }
 * </pre>
 *
 * <h2>完整行为矩阵（含所有边界情况）</h2>
 * <table border="1">
 *   <tr><th>场景</th><th>结果</th></tr>
 *   <tr><td>选择<b>没有任何改动</b></td><td>不写盘、不弹窗、直接回主界面（与 askRestart 无关）</td></tr>
 *   <tr><td>有改动，askRestart=true</td><td>弹确认窗，由用户点「是 / 否 / 取消」</td></tr>
 *   <tr><td>有改动，askRestart=false 且 autoRestart=true</td><td>无弹窗，直接结束进程</td></tr>
 *   <tr><td>有改动，askRestart=false 且 autoRestart=false</td><td>无弹窗，直接回主界面</td></tr>
 * </table>
 *
 * <h2>勾选「不再显示此界面」的语义</h2>
 * <ul>
 *   <li>勾选 + 点「是」→ 记住「以后直接重启」（askRestart=false, autoRestart=true）；</li>
 *   <li>勾选 + 点「否」→ 记住「以后直接回主菜单」（askRestart=false, autoRestart=false）；</li>
 *   <li>点「取消」→ <b>什么都不改</b>（既不记选择，也不改 askRestart），下次依旧弹窗。</li>
 * </ul>
 */
public final class RestartPromptConfig {

    private static final String FILE_NAME = "mtr_no_original_trains.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();

    private RestartPromptConfig() {
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** 保存后是否要弹提示（默认 true）。 */
    public static boolean isAskEnabled() {
        final JsonObject obj = read();
        if (obj == null || !isPrimitive(obj, "askRestart")) {
            return true;
        }
        try {
            return obj.get("askRestart").getAsBoolean();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 「不再弹窗」时执行的动作：true = 直接结束进程。
     *
     * <p>默认 <b>false</b>（直接回主菜单）—— 关闭进程属于破坏性操作，
     * 在用户没明确选过「是」之前不能默认替他把游戏关掉。
     */
    public static boolean isAutoRestart() {
        final JsonObject obj = read();
        if (obj == null || !isPrimitive(obj, "autoRestart")) {
            return false;
        }
        try {
            return obj.get("autoRestart").getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 记住用户的选择。
     *
     * @param restartNow    用户这次选的是「是（立即重启）」还是「否（稍后自己重启）」
     * @param neverAskAgain 是否勾选了「不再显示此界面」
     */
    public static void remember(boolean restartNow, boolean neverAskAgain) {
        try {
            final Path path = configPath();
            // 用 readOrCreate 而不是 readRaw：首次运行时配置文件可能还不存在，
            // 直接 return 会导致「不再显示」勾了却没记住。
            final JsonObject obj = readOrCreate();
            obj.addProperty("autoRestart", restartNow);
            if (neverAskAgain) {
                obj.addProperty("askRestart", false);
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(obj) + "\n", StandardCharsets.UTF_8);
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.warn(
                    "[MTR_NoOriginalTrains] 保存重启提示设置失败（不影响功能）", t);
        }
    }

    private static boolean isPrimitive(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive();
    }

    /** 读配置；文件不存在或解析失败返回 null。 */
    private static JsonObject read() {
        try {
            final Path path = configPath();
            if (!Files.exists(path)) {
                return null;
            }
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读配置；不存在就返回空对象（供写入前使用，保证字段不会因文件缺失而丢失）。 */
    private static JsonObject readOrCreate() {
        final JsonObject obj = read();
        return obj != null ? obj : new JsonObject();
    }
}
