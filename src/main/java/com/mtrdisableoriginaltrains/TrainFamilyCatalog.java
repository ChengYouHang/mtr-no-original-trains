package com.mtrdisableoriginaltrains;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置列车的「车系」目录，供选择界面按车系分组展示。
 *
 * <h2>数据来源优先级（重要）</h2>
 * <ol>
 *   <li><b>运行时注册的实际列车</b>（首选）—— MTR 每次 {@code reload()} 都会把车辆注册进
 *       {@code CustomResourceLoader.VEHICLES}。{@link BuiltinTrainFilter} 在注册过程中把
 *       被拦截的车辆 ID 收集到 {@link BuiltinTrainFilter#capturedBuiltin()}，
 *       本类据此建目录。<b>用户装的是哪个 MTR 版本，界面里就是哪个版本的车</b>，
 *       不会有「界面列了但实际没有」或「实际有但界面没列」的问题。</li>
 *   <li><b>打包的静态车系表</b>（兜底）—— 运行时一个车都没捕获到（例如 MTR 未加载、
 *       或用户在其它界面提前打开本界面）时，退回 jar 内的
 *       {@code builtin_families.tsv}（MTR 4.0.5 快照，36 车系 / 378 辆）。</li>
 * </ol>
 *
 * <h2>为什么要按车系分组</h2>
 * 378 辆车让用户逐个勾选不可用，而 MTR 的车辆自带 {@code family:xxx} 标签，
 * 实际只属于 36 个车系。36 个开关正好对应玩家「这套车 / 不要这套车」的心智模型。
 */
public final class TrainFamilyCatalog {

    private static final String RESOURCE = "/mtr_no_original_trains/builtin_families.tsv";

    /** 车系定义。 */
    public static final class Family {
        public final String id;
        public final String displayName;
        /** 车辆 ID → 单辆车显示名（保持 TSV 中的原始顺序）。 */
        public final Map<String, String> vehicles;

        Family(String id, String displayName, Map<String, String> vehicles) {
            this.id = id;
            this.displayName = displayName;
            this.vehicles = vehicles;
        }

        public int size() {
            return vehicles.size();
        }
    }

    private static volatile List<Family> families;
    /** 车辆 ID → 所属车系 ID（运行时重建后的目录；重建前与静态表相同）。 */
    private static volatile Map<String, String> idToFamily;
    /** 静态 TSV 的完整映射，永久保留；运行时目录缩水时 familyOf 用它兜底。 */
    private static volatile Map<String, String> staticIdToFamily;
    /** 当前目录是否由运行时注册的车辆构建（true）还是静态兜底（false）。 */
    private static volatile boolean runtimeBuilt;

    private TrainFamilyCatalog() {
        // 工具类
    }

    /** 当前目录是否来自运行时实际注册的车辆。 */
    public static boolean isRuntimeBuilt() {
        ensureLoaded();
        return runtimeBuilt;
    }

    /**
     * 依据运行时捕获到的车辆重建目录。
     *
     * @param entries {@code 车辆ID → 单辆车显示名}
     * @param familyOf {@code 车辆ID → 车系ID}；返回 null 时该车自成一组
     */
    public static synchronized void rebuildFromRuntime(Map<String, String> entries,
                                                      java.util.function.Function<String, String> familyOf) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        // 先把静态表的车系显示名读出来（"sp1900" → "SP1900 Trailer" 这种），
        // 运行时只提供 车辆ID/车系ID，显示名沿用静态表，避免界面上出现裸 id。
        ensureLoaded();
        final Map<String, String> staticNames = new LinkedHashMap<>();
        if (!families.isEmpty()) {
            for (final Family f : families) {
                staticNames.put(f.id, f.displayName);
            }
        }

        final Map<String, String> names = new LinkedHashMap<>();
        final Map<String, Map<String, String>> bucket = new LinkedHashMap<>();
        final Map<String, String> index = new LinkedHashMap<>();
        for (final Map.Entry<String, String> e : entries.entrySet()) {
            final String vehicleId = e.getKey();
            String family = null;
            try {
                family = familyOf == null ? null : familyOf.apply(vehicleId);
            } catch (Throwable ignored) {
                // 拿不到 family 就自成一组
            }
            if (family == null || family.isEmpty()) {
                family = vehicleId;
            }
            names.putIfAbsent(family, staticNames.getOrDefault(family, family));
            bucket.computeIfAbsent(family, k -> new LinkedHashMap<>()).put(vehicleId, e.getValue());
            index.put(vehicleId, family);
        }

        final List<Family> list = new ArrayList<>();
        for (final Map.Entry<String, Map<String, String>> e : bucket.entrySet()) {
            list.add(new Family(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()),
                    Collections.unmodifiableMap(e.getValue())));
        }
        idToFamily = index;
        families = Collections.unmodifiableList(list);
        runtimeBuilt = true;
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 车系目录已按运行时注册重建：{} 个车系 / {} 辆车",
                list.size(), index.size());
    }

    /** 按 TSV 顺序返回全部车系（首次调用时解析，失败则返回空表，不影响主功能）。 */
    public static List<Family> families() {
        ensureLoaded();
        return families;
    }

    /** 某个车辆 ID 属于哪个车系；未知则返回 {@code null}。
     *
     * <p>先查运行时目录，查不到再退回静态 TSV 表——保证 keepFamilies 的
     * 「车系 → 车辆」匹配不因运行时目录重建而失效。 */
    public static String familyOf(String vehicleId) {
        ensureLoaded();
        if (vehicleId == null) {
            return null;
        }
        final String runtime = idToFamily.get(vehicleId);
        if (runtime != null) {
            return runtime;
        }
        final Map<String, String> statics = staticIdToFamily;
        return statics == null ? null : statics.get(vehicleId);
    }

    public static int totalVehicles() {
        ensureLoaded();
        int n = 0;
        for (final Family f : families) {
            n += f.size();
        }
        return n;
    }

    private static void ensureLoaded() {
        if (families != null) {
            return;
        }
        synchronized (TrainFamilyCatalog.class) {
            if (families != null) {
                return;
            }
            final List<Family> list = new ArrayList<>();
            final Map<String, String> index = new LinkedHashMap<>();
            // 先用临时结构收集，保证 TSV 中车系出现的顺序
            final Map<String, String> names = new LinkedHashMap<>();
            final Map<String, Map<String, String>> bucket = new LinkedHashMap<>();

            try (InputStream in = TrainFamilyCatalog.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty() || line.charAt(0) == '#') {
                                continue;
                            }
                            final String[] parts = line.split("\t", -1);
                            if (parts.length < 4) {
                                continue;
                            }
                            final String family = parts[0].trim();
                            final String familyName = parts[1].trim();
                            final String vehicleId = parts[2].trim();
                            final String vehicleName = parts[3].trim();
                            if (family.isEmpty() || vehicleId.isEmpty()) {
                                continue;
                            }
                            names.putIfAbsent(family, familyName.isEmpty() ? family : familyName);
                            bucket.computeIfAbsent(family, k -> new LinkedHashMap<>()).put(vehicleId, vehicleName);
                            index.put(vehicleId, family);
                        }
                    }
                } else {
                    MtrDisableOriginalTrains.LOGGER.warn(
                            "[MTR_NoOriginalTrains] 找不到车系列表 {}，列车保留选择界面将为空（不影响过滤功能）", RESOURCE);
                }
            } catch (Throwable t) {
                MtrDisableOriginalTrains.LOGGER.warn(
                        "[MTR_NoOriginalTrains] 解析车系列表失败，列车保留选择界面将为空（不影响过滤功能）", t);
            }

            for (final Map.Entry<String, Map<String, String>> e : bucket.entrySet()) {
                list.add(new Family(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()),
                        Collections.unmodifiableMap(e.getValue())));
            }

            idToFamily = index;
            staticIdToFamily = index;
            families = Collections.unmodifiableList(list);
            runtimeBuilt = false;
            MtrDisableOriginalTrains.LOGGER.info(
                    "[MTR_NoOriginalTrains] 车系目录已加载（静态兜底）：{} 个车系 / {} 辆车",
                    list.size(), index.size());
        }
    }

    /** 所有车系 ID 的集合，供「全选」等批量操作使用。 */
    public static Set<String> allFamilyIds() {
        final Set<String> ids = new LinkedHashSet<>();
        for (final Family f : families()) {
            ids.add(f.id);
        }
        return ids;
    }
}
