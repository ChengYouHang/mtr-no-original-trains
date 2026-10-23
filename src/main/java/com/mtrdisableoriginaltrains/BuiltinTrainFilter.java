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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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

    /**
     * MTR 车辆标签里表示「车系」的键名。
     *
     * <p>注意：MTR 的 {@code collectTags} 会把原始标签 {@code "family:sp1900"} 按冒号拆开，
     * 存成 {@code 键="family" → 值={"sp1900"}}。所以这里按键名 {@code "family"} 精确匹配，
     * 不能拿 {@code "family:"} 去匹配键。
     */
    private static final String FAMILY_TAG_KEY = "family";

    /** 调试开关：打印没有 family 标签的车辆（仅在排查车系分组时打开）。 */
    private static final boolean DEBUG_TAGS = false;
    private static final Set<String> DEBUG_TAG_SAMPLES = java.util.Collections.synchronizedSet(new LinkedHashSet<>());

    private static volatile Set<String> builtinIds;
    /**
     * 本次运行<b>实际生效</b>的内置列车清单。
     *
     * <p>优先用 {@code AddonPackScanner} 从当前安装的 MTR 本体清单里运行时提取的结果
     * （版本无关，自动跟随 MTR 升级）；提取不到时才退回 jar 内打包的静态清单。
     */
    private static volatile Set<String> effectiveBuiltinIds = Collections.emptySet();
    /** 生效清单的来源说明，仅用于日志。 */
    private static volatile String builtinSource = "打包内置清单";
    private static volatile Set<String> alwaysKeepIds = Collections.emptySet();
    private static volatile Set<String> configKeepIds = Collections.emptySet();
    private static volatile Set<String> extraRemoveIds = Collections.emptySet();
    /**
     * 用户在「列车保留选择」界面勾选的<b>车系</b>。
     * 命中即保留该车系下的全部车辆（优先级等同 {@link #configKeepIds}）。
     */
    private static volatile Set<String> configKeepFamilies = Collections.emptySet();
    private static volatile boolean enabled = true;
    /** 车端连接件（风挡）运行时修复开关，见 {@link GangwayFix}。 */
    private static volatile boolean gangwayFixEnabled = true;

    /** 单次 reload 内已移除的数量，用于结束时打印汇总。 */
    public static volatile int removedThisReload;
    private static volatile boolean reloadSummaryPending;

    /**
     * 本次 reload 中被拦截的内置列车：{@code 车辆ID → 显示名}。
     *
     * <p>供「列车保留选择」界面在运行时建目录用 —— 这样界面里列出的就是
     * <b>当前安装的 MTR 实际注册的</b>列车，换 MTR 版本也永远对得上。
     */
    private static final Map<String, String> CAPTURED_BUILTIN = new LinkedHashMap<>();

    /** 上一次 reload 捕获的内置列车快照（reload 结束后仍可读，供 GUI 使用）。 */
    private static volatile Map<String, String> capturedSnapshot = Collections.emptyMap();

    /** 是否已捕获过（用于让 GUI 判断能否用运行时目录）。 */
    public static boolean hasCapturedBuiltin() {
        return !capturedSnapshot.isEmpty();
    }

    /** 上一次 reload 捕获到的内置列车（只读）。 */
    public static Map<String, String> capturedBuiltin() {
        return capturedSnapshot;
    }

    /** 捕获时的车系映射：车辆ID → family（从 vehicle 的 tags 里取）。 */
    private static final Map<String, String> CAPTURED_FAMILY = new LinkedHashMap<>();
    private static volatile Map<String, String> capturedFamilySnapshot = Collections.emptyMap();

    public static Map<String, String> capturedFamily() {
        return capturedFamilySnapshot;
    }

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
        // ---- 1b. 用户勾选了该车辆所属的车系 → 保留 ----
        final String family = TrainFamilyCatalog.familyOf(id);
        if (family != null && configKeepFamilies.contains(family)) {
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

        // ---- 5. 纯黑名单：不在生效的内置清单里的，一律放行 ----
        if (!effectiveBuiltinIds.contains(id)) {
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
        capture(vehicle);
        return true;
    }

    /**
     * 记录一辆被拦截的内置列车（ID + 显示名 + 所属车系），供选择界面使用。
     *
     * <p>显示名优先用 MTR 的语言键 {@code train.mtr.<id>}，取不到就用 {@code vehicle.getName()}。
     * 车系从 {@code vehicle.getTags()} 里的 {@code family:xxx} 取，取不到则用 ID 自成一组。
     */
    private static void capture(VehicleResource vehicle) {
        try {
            final String id = vehicle.getId();
            if (id == null || id.isEmpty()) {
                return;
            }
            String name = id;
            try {
                // MTR 的 getName() 返回 org.mtr.mapping.holder.MutableText，用 getString() 取纯文本
                final Object text = vehicle.getName();
                if (text != null) {
                    final String s = (String) text.getClass().getMethod("getString").invoke(text);
                    if (s != null && !s.isEmpty()) {
                        name = s;
                    }
                }
            } catch (Throwable ignored) {
                // 名称拿不到就退回 ID
            }
            CAPTURED_BUILTIN.put(id, name);
            CAPTURED_FAMILY.put(id, readFamilyTag(vehicle, id));
        } catch (Throwable t) {
            // 捕获失败绝不影响主流程
        }
    }

    /**
     * 读车辆的 {@code family:xxx} 标签。
     *
     * <p>MTR 的 {@code VehicleResource.collectTags(Object2ObjectAVLTreeMap)} 会把该车的全部标签
     * 塞进 map：{@code 标签名 → ObjectArrayList<...>}。其中形如 {@code family:class_802} 的标签
     * 就是<b>官方车系</b>，这是唯一可靠的归类依据（按 ID 首段切会把 class_802 / class_345
     * 全并成一个 "class"，实测 378 辆会被错并成 23 组）。
     *
     * <p>整段走反射：{@code collectTags} 的入参是 MTR 自带的 fastutil 类型，
     * 直接 import 会在 MTR 版本变动时编译不过；反射调用更稳。
     * 拿不到标签时退回「ID 首段」，代价只是该车自成一组，不影响功能。
     */
    private static String readFamilyTag(VehicleResource vehicle, String id) {
        try {
            final Object map = Class
                    .forName("org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap",
                            true, vehicle.getClass().getClassLoader())
                    .getConstructor()
                    .newInstance();
            final java.lang.reflect.Method collect = findCollectTags(vehicle.getClass());
            if (collect != null) {
                collect.setAccessible(true);
                collect.invoke(vehicle, map);
                // MTR 的 collectTags 结构（反编译确认）：
                //   Object2ObjectAVLTreeMap<String, Object2ObjectAVLTreeMap<String, ObjectArrayList<String>>>
                // 原始标签 "family:sp1900" → 顶层键 "family"，值是内层 Map，
                // 内层键就是车系 "sp1900"。所以取 get("family") 后再取内层第一个键。
                final Object familyValue = ((java.util.Map<?, ?>) map).get(FAMILY_TAG_KEY);
                final String family = extractFamilyValue(familyValue);
                if (!family.isEmpty()) {
                    return family;
                }
                if (DEBUG_TAGS && DEBUG_TAG_SAMPLES.add(id)) {
                    MtrDisableOriginalTrains.LOGGER.warn(
                            "[MTR_NoOriginalTrains][调试] {} 无 family 标签，键={} family值类型={}",
                            id, ((java.util.Map<?, ?>) map).keySet(),
                            familyValue == null ? "null" : familyValue.getClass().getName());
                }
            } else if (DEBUG_TAGS && DEBUG_TAG_SAMPLES.add(id)) {
                MtrDisableOriginalTrains.LOGGER.warn(
                        "[MTR_NoOriginalTrains][调试] {} 找不到 collectTags 方法", id);
            }
        } catch (Throwable t) {
            if (DEBUG_TAGS && DEBUG_TAG_SAMPLES.add(id)) {
                MtrDisableOriginalTrains.LOGGER.warn(
                        "[MTR_NoOriginalTrains][调试] 读取 {} 的 collectTags 失败", id, t);
            }
        }
        // 兜底：ID 首段（可能把多套车并成一组，但至少不是错误数据）
        final int us = id.indexOf('_');
        return us > 0 ? id.substring(0, us) : id;
    }

    /**
     * 从 {@code collectTags} 里 {@code "family"} 对应的值中取出<b>真正的车系名</b>。
     *
     * <p>MTR 4.0.5 的实际结构是
     * {@code Map<String, Map<String, List<String>>>}：顶层键 {@code "family"}，
     * 值是<b>内层 Map</b>（键 = 车系名，值 = 该车系下的车辆 ID 列表）。
     * 早期版本/其他实现也可能是集合或标量，这里统一兼容三种形态，
     * 以免版本变动后又把 Map 的 {@code toString()} 当成车系名显示（会变成
     * {@code {sp1900=>[sp1900_mini_trailer]}} 这种诡异字符串）。
     */
    private static String extractFamilyValue(Object familyValue) {
        if (familyValue == null) {
            return "";
        }
        // 形态 1（MTR 4.0.5 实际结构）：内层 Map，键即车系名
        if (familyValue instanceof java.util.Map<?, ?> inner) {
            for (final Object k : inner.keySet()) {
                final String s = String.valueOf(k);
                if (!s.isEmpty()) {
                    return s;
                }
            }
            return "";
        }
        // 形态 2：集合 / 数组，取第一个非空元素
        if (familyValue instanceof Iterable<?> it) {
            for (final Object v : it) {
                final String s = String.valueOf(v);
                if (!s.isEmpty()) {
                    return s;
                }
            }
            return "";
        }
        // 形态 3：标量
        final String s = String.valueOf(familyValue);
        return "null".equals(s) ? "" : s;
    }

    /** 在类及其父类里找 {@code collectTags} 方法（参数类型随 MTR 版本可能变化）。 */
    private static java.lang.reflect.Method findCollectTags(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (final java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if ("collectTags".equals(m.getName()) && m.getParameterCount() == 1
                        && java.util.Map.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    return m;
                }
            }
        }
        return null;
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
        CAPTURED_BUILTIN.clear();
        CAPTURED_FAMILY.clear();
        if (!enabled) {
            return;
        }
        // 注意：即使 enabled=false 也保留上一次的捕获快照，界面仍有内容可显示
        AddonPackScanner.scan(builtinIds);
        // 优先采用「从当前安装的 MTR 本体清单里现取」的清单 —— 这样任何 MTR 4.x 版本
        // （含将来新增/改名的内置列车）都自动适配，不依赖 jar 里那份静态快照。
        final Set<String> dynamic = AddonPackScanner.dynamicBuiltinIds();
        if (!dynamic.isEmpty()) {
            effectiveBuiltinIds = dynamic;
            builtinSource = "MTR 本体清单（运行时提取，版本无关）";
        } else {
            effectiveBuiltinIds = builtinIds;
            builtinSource = "打包内置清单（静态兜底）";
        }
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

        // 无论是否启用，都把本次捕获到的内置列车快照固化下来，供选择界面使用
        if (!CAPTURED_BUILTIN.isEmpty()) {
            capturedSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(CAPTURED_BUILTIN));
            capturedFamilySnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(CAPTURED_FAMILY));
            // 用运行时实际注册的车重建车系目录（车系从 family: 标签取，取不到按 ID 自成一组）
            TrainFamilyCatalog.rebuildFromRuntime(capturedSnapshot, capturedFamilySnapshot::get);
        }

        if (!enabled) {
            MtrDisableOriginalTrains.LOGGER.info("[MTR_NoOriginalTrains] 过滤已关闭（enabled=false），本次未移除任何车辆");
            return;
        }
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 车辆注册完成：已移除 MTR 内置原版列车 {} 个（本次生效清单 {} 条，来源：{}）"
                        + "—— 不进车场列表、不写入模型/贴图资源；追加包列车不受影响",
                removedThisReload, effectiveBuiltinIds.size(), builtinSource);
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
            effectiveBuiltinIds = remove;
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
            configKeepFamilies = readStringSet(object, "keepFamilies");
            extraRemoveIds = readStringSet(object, "extraRemoveIds");
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.warn("[MTR_NoOriginalTrains] 读取配置失败，已回退到默认值（仅移除内置原版列车）", t);
            enabled = true;
            gangwayFixEnabled = true;
            configKeepIds = Collections.emptySet();
            configKeepFamilies = Collections.emptySet();
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
                            + "  \"keepFamilies\": [],\n"
                            + "  \"extraRemoveIds\": []\n"
                            + "}\n",
                    StandardCharsets.UTF_8);
            MtrDisableOriginalTrains.LOGGER.info("[MTR_NoOriginalTrains] 已生成默认配置文件 {}", configFile);
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.warn("[MTR_NoOriginalTrains] 写入默认配置文件失败（不影响功能）", t);
        }
    }
}
