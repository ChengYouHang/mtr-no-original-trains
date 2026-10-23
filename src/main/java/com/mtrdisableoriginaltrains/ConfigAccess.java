package com.mtrdisableoriginaltrains;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 配置文件读写（界面侧）。
 *
 * <p>与 {@link BuiltinTrainFilter} 的分工：过滤逻辑只<b>读</b>配置，本类负责界面需要的
 * <b>读原始值 + 写回</b>。字段：
 * <pre>
 * {
 *   "enabled": true,
 *   "fixGangway": true,
 *   "keepIds": [],        // 逐辆车保留（界面单车辆勾选写这里）
 *   "keepFamilies": [],   // 整族保留（界面车系勾选写这里）
 *   "extraRemoveIds": []
 * }
 * </pre>
 */
public final class ConfigAccess {

    private static final String FILE_NAME = "mtr_no_original_trains.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 界面读到的当前选择。 */
    public static final class LiveState {
        public boolean enabled = true;
        public boolean fixGangway = true;
        public final Set<String> keepIds = new LinkedHashSet<>();
        public final Set<String> keepFamilies = new LinkedHashSet<>();
        public final Set<String> extraRemoveIds = new LinkedHashSet<>();
    }

    private ConfigAccess() {
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** 读当前配置；文件不存在或损坏时返回默认值。 */
    public static LiveState readLiveState() {
        final LiveState state = new LiveState();
        try {
            final Path path = configPath();
            if (!Files.exists(path)) {
                return state;
            }
            final String text = Files.readString(path, StandardCharsets.UTF_8);
            final JsonObject obj = GSON.fromJson(text, JsonObject.class);
            if (obj == null) {
                return state;
            }
            if (obj.has("enabled") && obj.get("enabled").isJsonPrimitive()) {
                state.enabled = obj.get("enabled").getAsBoolean();
            }
            if (obj.has("fixGangway") && obj.get("fixGangway").isJsonPrimitive()) {
                state.fixGangway = obj.get("fixGangway").getAsBoolean();
            }
            collect(obj, "keepIds", state.keepIds);
            collect(obj, "keepFamilies", state.keepFamilies);
            collect(obj, "extraRemoveIds", state.extraRemoveIds);
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.warn("[MTR_NoOriginalTrains] 界面读取配置失败，按默认值显示", t);
        }
        return state;
    }

    private static void collect(JsonObject obj, String key, Collection<String> out) {
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (var e : obj.getAsJsonArray(key)) {
                if (e != null && e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                }
            }
        }
    }

    /** 写回选择结果（保留其他字段原样）。 */
    public static void writeSelection(Set<String> keepFamilies, Set<String> keepIds) {
        try {
            final Path path = configPath();
            final LiveState current = readLiveState();
            final JsonObject obj = new JsonObject();
            obj.addProperty("enabled", current.enabled);
            obj.addProperty("fixGangway", current.fixGangway);
            obj.add("keepIds", GSON.toJsonTree(keepIds));
            obj.add("keepFamilies", GSON.toJsonTree(keepFamilies));
            obj.add("extraRemoveIds", GSON.toJsonTree(current.extraRemoveIds));
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(obj) + "\n", StandardCharsets.UTF_8);
            MtrDisableOriginalTrains.LOGGER.info(
                    "[MTR_NoOriginalTrains] 已保存列车保留选择：车系 {} 个，单车 {} 辆（重启后生效）",
                    keepFamilies.size(), keepIds.size());
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.error("[MTR_NoOriginalTrains] 保存列车保留选择失败", t);
        }
    }
}
