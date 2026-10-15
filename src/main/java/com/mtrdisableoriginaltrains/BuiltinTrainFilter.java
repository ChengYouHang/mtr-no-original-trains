package com.mtrdisableoriginaltrains;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.resource.VehicleResource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 判定「某个车辆是否属于 MTR 内置原版列车，应当被移除」。
 *
 * <h2>为什么用 ID 判定，而不是用 registerVehicle 的 boolean</h2>
 * MTR 4.0.5 的 {@code CustomResourceLoader.registerVehicle(VehicleResource, boolean)} 中，
 * 第二个参数 <b>不是</b>「是否追加包」。经反编译核对（4.0.5 字节码）：
 * <ul>
 *   <li>{@code reload()} 用 {@code ResourceManagerHelper.readAllResources(Identifier("mtr","mtr_custom_resources.json"))}
 *       把<b>所有资源包</b>（含 MTR 内置包与全部追加包）中同名的 JSON 全部读入，
 *       统一走 {@code lambda$reload$3 / lambda$reload$9} 注册，<b>两者都传 false</b>；</li>
 *   <li>唯一传 {@code true} 的是 {@code AbstractResourcePackCreatorServlet}
 *       （游戏内「资源包创作器」网页预览），与追加包无关；</li>
 *   <li>该 boolean 的真正作用是：{@code if (!flag) vehicle.writeMinecraftResource(...)}
 *       —— 即「是否跳过写入 Minecraft 模型/纹理资源集」。</li>
 * </ul>
 * 也就是说，<b>MTR 本身并不区分「内置车」与「追加包车」</b>。
 *
 * <h2>判定顺序（五步，「纯黑名单」）</h2>
 * <ol>
 *   <li><b>配置 {@code keepIds}</b> —— 用户显式要保留的 ID，直接放行；</li>
 *   <li><b>配置 {@code extraRemoveIds}</b> —— 用户显式要移除的 ID，直接移除；</li>
 *   <li><b>「其他资源包声明过」一律不拦截</b> —— 见 {@link AddonPackScanner}。
 *       只要某个 ID 被 MTR 本体之外的资源包声明过，就<b>绝不拦截</b>，
 *       这是「只禁止原版的，其他资源包的车一概不动」的核心保证；</li>
 *   <li><b>前缀白名单</b> —— ID 以 {@code mtr_custom_train_} 开头的一律保留。
 *       这是 MTR {@code LegacyVehicleResource.convert} 专门为「追加包 custom_trains」
 *       生成的 ID 前缀（格式 {@code mtr_custom_train_<小写键名>_<trailer|cab_1|cab_2|cab_3>}），
 *       内置车永远不会长这样；</li>
 *   <li><b>纯黑名单</b> —— 只有 ID 出现在随包内置的 {@code builtin_train_ids.txt}
 *       （378 个 MTR 内置列车，精确导出自 MTR 4.0.5 自身的
 *       {@code assets/mtr/mtr_custom_resources.json} 中 {@code transportMode == TRAIN} 的项）
 *       才会被移除；<b>不在清单里的一律原样放行</b>。
 *       <p>{@code transportMode} 这个附加条件不是多余的：MTR 内部存在「同 id、不同 transportMode」
 *       的条目（如 {@code minecart} 同时有 TRAIN 的 Minecart 与 AIRPLANE 的 Flying Minecart），
 *       只按 id 判会误删后者。</li>
 * </ol>
 *
 * <p><b>因此新增任何追加包、资源包都不需要改动本模组</b>：新包里的车要么带
 * {@code mtr_custom_train_} 前缀，要么被 {@link AddonPackScanner} 识别为「其他资源包声明的 ID」，
 * 要么压根不在内置黑名单里 —— 三条路都不会被拦。
 *
 * <h2>配置文件</h2>
 * {@code config/mtr_no_original_trains.json}（首次运行自动生成默认配置）：
 * <pre>
 * {
 *   "enabled": true,       // 总开关，false = 完全不过滤
 *   "fixGangway": true,    // 车端连接件（风挡）运行时修复，见 GangwayFix
 *   "keepIds": [],         // 额外要保留的 ID
 *   "extraRemoveIds": []   // 额外要移除的 ID
 * }
 * </pre>
 * 文件不存在、格式错误或读取失败时一律回退到默认值，不会导致游戏崩溃。
 */
public final class BuiltinTrainFilter {

    /** 内置列车 ID 黑名单（每行一个），随本模组打包。 */
    private static final String REMOVE_LIST_RESOURCE = "/mtr_no_original_trains/builtin_train_ids.txt";
    /**
     * 早期版本使用过的「追加包 ID 白名单」文件（每行一个键名）。
     *
     * <p><b>现在已不需要维护</b>：追加包 legacy 列车由 {@code mtr_custom_train_} 前缀规则保留，
     * 现代格式追加包由 {@link AddonPackScanner} 自动识别并放行。
     * 该文件仅作为「曾经登记过哪些包」的记录保留，内容为空也不影响功能。
     */
    private static final String KEEP_LIST_RESOURCE = "/mtr_no_original_trains/keep_ids.txt";
    /**
     * 始终保留的 ID 前缀。
     * {@code mtr_custom_train_} 是 MTR {@code LegacyVehicleResource.convert}
     * 为追加包 custom_trains 生成的 ID 前缀（内置车不含此前缀）。
     */
    private static final String[] ALWAYS_KEEP_PREFIXES = {"mtr_custom_train_"};

    private static final String CONFIG_FILE_NAME = "mtr_no_original_trains.json";

    private static volatile Set<String> builtinIds;
    private static volatile Set<String> alwaysKeepIds = Collections.emptySet();
    private static volatile Set<String> configKeepIds = Collections.emptySet();
    private static volatile Set<String> extraRemoveIds = Collections.emptySet();
    private static volatile boolean enabled = true;
    /** 车端连接件（风挡）运行时修复开关，见 {@link GangwayFix}。 */
    private static volatile boolean gangwayFixEnabled = true;

    /** 单次 reload 内已移除的数量，用于结束时打印汇总。 */
    public static volatile int removedThisReload;
    private static volatile boolean reloadSummaryPending;

    private BuiltinTrainFilter() {
    }

    /**
     * 纯判定，<b>无副作用</b>：该车辆是否属于「应被移除的内置原版列车」。
     *
     * <p>{@code registerVehicle} 里有两处需要判断（跳过进列表、跳过写模型资源），
     * 两处都必须得到同一答案，因此把判定与计数分开，避免重复累计。
     *
     * @return true 表示该车辆应当被移除（不进车场/生成列表，也不加载其模型与贴图）
     */
    public static boolean isRemovable(VehicleResource vehicle) {
        if (vehicle == null) {
            return false;
        }
        ensureLoaded();
        if (!enabled) {
            return false;
        }

        final String id;
        try {
            id = vehicle.getId();
        } catch (Throwable t) {
            return false;
        }
        if (id == null || id.isEmpty()) {
            return false;
        }

        // ---- 1. 用户显式要保留 ----
        if (configKeepIds.contains(id)) {
            return false;
        }

        // ---- 2. 用户显式要移除（优先于下面的所有自动放行规则）----
        if (extraRemoveIds.contains(id)) {
            return true;
        }

        // ---- 3. 「其他资源包声明过这个 ID」→ 一律不拦截 ----
        if (AddonPackScanner.isDeclaredByAddon(id)) {
            return false;
        }

        // ---- 4. 追加包 legacy 列车前缀 → 保留 ----
        if (isAlwaysKept(id)) {
            return false;
        }

        // ---- 5. 纯黑名单：不在内置清单里的，一律放行 ----
        if (!builtinIds.contains(id)) {
            return false;
        }

        // 命中黑名单，且确认是列车才移除。
        // 注意：MTR 里存在「同 id 不同 transportMode」的情况（例如 minecart 同时有
        // TRAIN 与 AIRPLANE 两个条目），因此必须同时判 transportMode，否则会误伤
        // 同名的非列车载具。
        final TransportMode transportMode;
        try {
            transportMode = vehicle.getTransportMode();
        } catch (Throwable t) {
            return false;
        }
        return transportMode == TransportMode.TRAIN;
    }

    /**
     * 带计数的版本：除判定外，还会累加「本次 reload 已移除数量」，供结束时打印汇总。
     * 只应由「每个车辆恰好调用一次」的位置（即跳过进列表那一处）调用。
     */
    public static boolean shouldRemove(VehicleResource vehicle) {
        if (!isRemovable(vehicle)) {
            return false;
        }
        removedThisReload++;
        return true;
    }

    /**
     * 每次资源重载<b>开始前</b>调用（挂在 {@code CustomResourceLoader.reload()} 的 HEAD）。
     *
     * <p>做两件事：
     * <ol>
     *   <li>重置本次重载的计数；</li>
     *   <li>扫描所有资源包，收集「MTR 本体之外」的包各自声明了哪些车辆 ID，
     *       供 {@link #isRemovable} 放行 —— 必须在 MTR 注册车辆之前完成。</li>
     * </ol>
     */
    public static void beginReload() {
        ensureLoaded();
        removedThisReload = 0;
        reloadSummaryPending = true;
        if (!enabled) {
            return;
        }
        AddonPackScanner.scan(builtinIds);
    }

    private static boolean isAlwaysKept(String id) {
        for (String prefix : ALWAYS_KEEP_PREFIXES) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return alwaysKeepIds.contains(id);
    }

    /** 车端连接件（风挡）运行时修复是否启用，见 {@link GangwayFix}。 */
    public static boolean isGangwayFixEnabled() {
        ensureLoaded();
        return gangwayFixEnabled;
    }

    /** 一次 reload 结束后的汇总日志。 */
    public static void logReloadSummary() {
        if (!reloadSummaryPending) {
            return;
        }
        reloadSummaryPending = false;
        if (!enabled) {
            MtrDisableOriginalTrains.LOGGER.info("[MTR_NoOriginalTrains] 过滤已关闭（enabled=false），本次未移除任何车辆");
            return;
        }
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 车辆注册完成：已移除 MTR 内置原版列车 {} 个（内置黑名单 {} 个）"
                        + "—— 不进车场列表、不写入模型/贴图资源；追加包列车不受影响",
                removedThisReload, builtinIds.size());
        if (AddonPackScanner.isActive()) {
            MtrDisableOriginalTrains.LOGGER.info(
                    "[MTR_NoOriginalTrains] 黑名单模式：本体清单来自资源包「{}」；"
                            + "另外 {} 个资源包声明的 {} 个车辆 ID 已自动放行（绝不拦截），来源：{}",
                    AddonPackScanner.builtinPackName(),
                    AddonPackScanner.packCount(),
                    AddonPackScanner.declaredCount(),
                    AddonPackScanner.packCount() == 0 ? "（无）" : AddonPackScanner.packList());
        }
    }

    private static void ensureLoaded() {
        if (builtinIds != null) {
            return;
        }
        synchronized (BuiltinTrainFilter.class) {
            if (builtinIds != null) {
                return;
            }
            final Set<String> remove = readIdList(REMOVE_LIST_RESOURCE);
            alwaysKeepIds = readIdList(KEEP_LIST_RESOURCE);
            builtinIds = remove;
            reloadSummaryPending = true;
            loadConfig();
            MtrDisableOriginalTrains.LOGGER.info(
                    "[MTR_NoOriginalTrains] 已启用（纯黑名单模式）：内置黑名单 {} 个，"
                            + "追加包前缀白名单 {}，记录用清单 {} 条，配置 keepIds={} 个，extraRemoveIds={} 个，连接件修复={}",
                    remove.size(), java.util.Arrays.toString(ALWAYS_KEEP_PREFIXES),
                    alwaysKeepIds.size(), configKeepIds.size(), extraRemoveIds.size(),
                    gangwayFixEnabled ? "开" : "关");
        }
    }

    private static Set<String> readIdList(String resource) {
        final Set<String> ids = new LinkedHashSet<>();
        try (InputStream in = BuiltinTrainFilter.class.getResourceAsStream(resource)) {
            if (in == null) {
                MtrDisableOriginalTrains.LOGGER.error("[MTR_NoOriginalTrains] 找不到清单文件 {}，将按空清单处理", resource);
                return ids;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        ids.add(trimmed);
                    }
                }
            }
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.error("[MTR_NoOriginalTrains] 读取清单 {} 失败，将按空清单处理", resource, t);
        }
        return ids;
    }

    private static void loadConfig() {
        try {
            final Path configFile = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
            if (!Files.exists(configFile)) {
                writeDefaultConfig(configFile);
                return;
            }
            final String content = Files.readString(configFile, StandardCharsets.UTF_8);
            final JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonObject()) {
                throw new IllegalStateException("根节点不是 JSON 对象");
            }
            final JsonObject object = root.getAsJsonObject();
            if (object.has("enabled") && object.get("enabled").isJsonPrimitive()) {
                enabled = object.get("enabled").getAsBoolean();
            }
            if (object.has("fixGangway") && object.get("fixGangway").isJsonPrimitive()) {
                gangwayFixEnabled = object.get("fixGangway").getAsBoolean();
            }
            configKeepIds = readStringSet(object, "keepIds");
            extraRemoveIds = readStringSet(object, "extraRemoveIds");
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.warn("[MTR_NoOriginalTrains] 读取配置失败，已回退到默认值（仅移除内置原版列车）", t);
            enabled = true;
            gangwayFixEnabled = true;
            configKeepIds = Collections.emptySet();
            extraRemoveIds = Collections.emptySet();
        }
    }

    private static Set<String> readStringSet(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return Collections.emptySet();
        }
        final JsonArray array = object.getAsJsonArray(key);
        final Set<String> result = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) {
                final String value = element.getAsString();
                if (value != null && !value.isEmpty()) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private static void writeDefaultConfig(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile,
                    "{\n"
                            + "  \"enabled\": true,\n"
                            + "  \"fixGangway\": true,\n"
                            + "  \"keepIds\": [],\n"
                            + "  \"extraRemoveIds\": []\n"
                            + "}\n",
                    StandardCharsets.UTF_8);
            MtrDisableOriginalTrains.LOGGER.info("[MTR_NoOriginalTrains] 已生成默认配置文件 {}", configFile);
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.warn("[MTR_NoOriginalTrains] 写入默认配置文件失败（不影响功能）", t);
        }
    }
}
