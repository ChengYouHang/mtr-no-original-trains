package com.mtrdisableoriginaltrains;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 扫描「除 MTR 本体之外的所有资源包」各自声明了哪些车辆 ID，供黑名单过滤时「绕行」用。
 *
 * <h2>要解决的问题</h2>
 * MTR 的 {@code CustomResourceLoader.reload()} 把<b>所有资源包</b>里同名的
 * {@code mtr:mtr_custom_resources.json} <b>逐个</b>读进来，各自注册自己的车辆
 * —— 也就是说，<b>MTR 本体清单与追加包清单是并列关系，不是覆盖关系</b>。
 *
 * <p>问题在于 {@code registerVehicle(VehicleResource, boolean)} 拿到的只有一个
 * {@code VehicleResource}，<b>不带「这个 ID 来自哪个资源包」的信息</b>。
 * 如果某个追加包（尤其是现代格式、直接写 {@code vehicles[].id} 的包）声明的 ID
 * 恰好和 MTR 内置列车重名，仅按 ID 判黑名单就会把这个追加包的车也一起删掉。
 *
 * <h2>做法</h2>
 * 在每次资源重载<b>开始前</b>（{@code reload()} 的 HEAD）自己把
 * {@code mtr:mtr_custom_resources.json} 的所有来源枚举一遍：
 * <ul>
 *   <li>用 {@link ResourceManager#getResourceStack(ResourceLocation)} 拿到每个资源包各自的副本
 *       （对应 Fabric/Yarn 的 {@code getAllResources}），
 *       每份都带 {@link Resource#sourcePackId()}，能看出它属于哪个包；</li>
 *   <li>把每份里声明的车辆 ID 全部抽出来（现代格式 {@code vehicles[].id}，
 *       以及 legacy 格式 {@code custom_trains} 的键名展开）；</li>
 *   <li><b>识别哪一份是 MTR 本体</b>：两条判据任一命中即可（<b>版本无关</b>）——
 *       ① 资源包名是 MTR 本体；② 该份清单声明了打包内置清单里的全部 ID。</li>
 * </ul>
 * 之后黑名单过滤时，只要一个 ID 出现在「其他资源包」里，就<b>绝不拦截</b>。
 *
 * <h2>失败即降级</h2>
 * 任何一步出错（拿不到 ResourceManager、解析失败、没识别出本体清单）都只会
 * <b>关闭这个守卫</b>，退回「纯按内置黑名单移除」的旧行为，不会影响模组主功能，
 * 也不会导致空指针或崩溃。
 */
public final class AddonPackScanner {

    private static final ResourceLocation CUSTOM_RESOURCES_ID =
            ResourceLocation.tryParse("mtr:mtr_custom_resources.json");
    /**
     * {@code LegacyVehicleResource.convert} 为每个 legacy 车展开的 4 个变体后缀
     * （{@code Variation.key = toString().toLowerCase()}）。
     */
    private static final String[] LEGACY_VARIANTS = {"trailer", "cab_1", "cab_2", "cab_3"};

    /** 「其他资源包」声明过的全部车辆 ID。 */
    private static volatile Set<String> declaredIds = Collections.emptySet();
    /**
     * 运行时从 <b>MTR 本体清单</b>里提取出来的内置列车 ID。
     *
     * <p>这是「支持全部 MTR 4.x 版本」的关键：内置清单不再是过期就失效的静态快照，
     * 而是每次重载时按当前安装的 MTR 版本现取现用 —— MTR 新增、改名、删除内置列车
     * 都会自动跟随，无需本模组跟着发版。
     */
    private static volatile Set<String> dynamicBuiltinIds = Collections.emptySet();
    /** 参与扫描（非本体）的资源包名，仅用于日志。 */
    private static volatile Set<String> sourcePacks = Collections.emptySet();
    /** MTR 本体资源包的名称，仅用于日志。 */
    private static volatile String builtinPackName = "未识别";
    /** 守卫是否生效：只有确实识别出本体清单时才为 true。 */
    private static volatile boolean active;
    /** 本次是否识别出了 MTR 本体那份清单。 */
    private static volatile boolean builtinFound;

    private AddonPackScanner() {
    }

    /**
     * 扫描所有资源包。应在每次 {@code CustomResourceLoader.reload()} 的开始处调用一次。
     *
     * <p>识别 MTR 本体那份清单走两条判据，任一命中即可（<b>版本无关</b>）：
     * <ol>
     *   <li><b>包名判据</b>：资源包名是 MTR 本体（{@code mtr} / {@code mod/mtr} 等）。</li>
     *   <li><b>清单判据</b>：该份清单声明了打包内置清单里的全部 ID（老版本兜底）。</li>
     * </ol>
     * 识别出来后，本体清单里 <b>transportMode 为 TRAIN</b> 的 ID 会被提取成
     * {@link #dynamicBuiltinIds()}，当作本次运行的黑名单 —— 因此 MTR 升级后新增/改名
     * 的内置列车都会被自动纳入，不需要本模组跟着更新。
     *
     * @param fallbackBuiltinIds 打包内置清单，仅用于第 2 条判据与识别失败时的兜底
     */
    public static void scan(Set<String> fallbackBuiltinIds) {
        final List<String> packNames = new ArrayList<>();
        final List<Set<String>> packAllIds = new ArrayList<>();
        final List<Set<String>> packTrainIds = new ArrayList<>();
        int skipped = 0;

        try {
            final ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            // Forge（官方映射）里取「同一 ID 的全部来源」是 getResourceStack，
            // 对应 Fabric/Yarn 的 getAllResources。
            final List<Resource> resources = resourceManager.getResourceStack(CUSTOM_RESOURCES_ID);
            for (Resource resource : resources) {
                // Forge 的 Resource#sourcePackId() 提供资源包标识（如 "mtr" / "file/xxx.zip"）。
                final String packName = String.valueOf(resource.sourcePackId());
                final Set<String> allIds = new LinkedHashSet<>();
                final Set<String> trainIds = new LinkedHashSet<>();
                try (InputStream in = resource.open();
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    final JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) {
                        skipped++;
                        continue;
                    }
                    final JsonObject root = parsed.getAsJsonObject();
                    collectDeclaredIds(root, allIds);
                    collectTrainIds(root, trainIds);
                } catch (Throwable t) {
                    // 单份解析失败只跳过这一份，不影响其它包
                    skipped++;
                    continue;
                }
                if (allIds.isEmpty()) {
                    continue;
                }
                packNames.add(packName);
                packAllIds.add(allIds);
                packTrainIds.add(trainIds);
            }
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.warn(
                    "[MTR_NoOriginalTrains] 扫描 mtr_custom_resources.json 失败，本次不启用「不拦截其他资源包」守卫"
                            + "（退回仅按打包内置清单移除）", t);
            reset();
            return;
        }

        // ---- 识别哪一份是 MTR 本体清单：先按「包含全部已知内置 ID」，再按资源包名 ----
        int builtinIndex = -1;
        if (fallbackBuiltinIds != null && !fallbackBuiltinIds.isEmpty()) {
            for (int i = 0; i < packAllIds.size(); i++) {
                if (packAllIds.get(i).containsAll(fallbackBuiltinIds)) {
                    builtinIndex = i;
                    break;
                }
            }
        }
        if (builtinIndex < 0) {
            for (int i = 0; i < packNames.size(); i++) {
                if (isMtrPackName(packNames.get(i))) {
                    builtinIndex = i;
                    break;
                }
            }
        }

        // ---- 除本体外的所有包，其声明的 ID 一律登记为「绝不拦截」 ----
        final Set<String> collectedIds = new LinkedHashSet<>();
        final Set<String> collectedPacks = new LinkedHashSet<>();
        for (int i = 0; i < packNames.size(); i++) {
            if (i == builtinIndex) {
                continue;
            }
            collectedIds.addAll(packAllIds.get(i));
            collectedPacks.add(packNames.get(i));
        }

        declaredIds = collectedIds;
        sourcePacks = collectedPacks;
        builtinFound = builtinIndex >= 0;
        builtinPackName = builtinFound ? packNames.get(builtinIndex) : "未识别";
        dynamicBuiltinIds = builtinFound ? packTrainIds.get(builtinIndex) : Collections.emptySet();
        // 识别不出本体清单时保守起见不启用守卫：宁可继续移除内置车，也不要因为误判而全部保留
        active = builtinFound;

        if (!builtinFound) {
            MtrDisableOriginalTrains.LOGGER.warn(
                    "[MTR_NoOriginalTrains] 未能在资源包中识别出 MTR 本体清单"
                            + "（共读到 {} 份 mtr_custom_resources.json，解析失败 {} 份），"
                            + "本次改用打包内置清单，且不启用「不拦截其他资源包」守卫",
                    packNames.size(), skipped);
        }
    }

    /** 空状态重置（扫描失败时用）。 */
    private static void reset() {
        declaredIds = Collections.emptySet();
        sourcePacks = Collections.emptySet();
        builtinFound = false;
        builtinPackName = "未识别";
        dynamicBuiltinIds = Collections.emptySet();
        active = false;
    }

    /**
     * 资源包名看起来是不是 MTR 本体。
     *
     * <p>Forge 的 {@code sourcePackId()} 对模组自带资源包同样返回 {@code mtr}
     * （与 Fabric 一致），但为保险起见这里放宽匹配，避免因为加载器命名差异就整个守卫失效。
     */
    private static boolean isMtrPackName(String packName) {
        if (packName == null || packName.isEmpty()) {
            return false;
        }
        final String name = packName.trim().toLowerCase(Locale.ENGLISH);
        return name.equals("mtr")
                || name.equals("mod/mtr")
                || name.equals("minecraft-transit-railway")
                || name.endsWith("/mtr");
    }

    /**
     * 该 ID 是否被「MTR 本体之外」的资源包声明过。
     * 返回 true 时，黑名单过滤必须放行 —— 这是「只禁止原版、其他资源包一律不拦」的核心保证。
     */
    public static boolean isDeclaredByAddon(String id) {
        return active && id != null && !id.isEmpty() && declaredIds.contains(id);
    }

    /** 守卫是否生效，仅用于日志。 */
    public static boolean isActive() {
        return active;
    }

    /** 「其他资源包」声明的 ID 总数，仅用于日志。 */
    public static int declaredCount() {
        return declaredIds.size();
    }

    /** 参与扫描的资源包数量，仅用于日志。 */
    public static int packCount() {
        return sourcePacks.size();
    }

    /** 参与扫描的资源包名列表，仅用于日志。 */
    public static String packList() {
        return String.join("、", sourcePacks);
    }

    /** MTR 本体资源包名，仅用于日志。 */
    public static String builtinPackName() {
        return builtinPackName;
    }

    /**
     * 本次运行从 MTR 本体清单里提取到的内置列车 ID（已排除船/飞艇/缆车等非列车）。
     * 提取失败（未识别出本体清单）时返回空集，调用方应退回打包内置清单。
     */
    public static Set<String> dynamicBuiltinIds() {
        return dynamicBuiltinIds;
    }

    /** 单份 JSON 里属于「列车」的车辆 ID（现代格式判 transportMode，legacy 车一律算列车）。 */
    private static void collectTrainIds(JsonObject root, Set<String> out) {
        final JsonElement vehicles = root.get("vehicles");
        if (vehicles != null && vehicles.isJsonArray()) {
            for (JsonElement element : vehicles.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                final JsonObject vehicle = element.getAsJsonObject();
                final JsonElement mode = vehicle.get("transportMode");
                if (mode != null && mode.isJsonPrimitive()) {
                    final String value = mode.getAsString();
                    // 明确声明为 TRAIN 之外的（BOAT / AIRPLANE / CABLE_CAR …）一律不算内置列车；
                    // 未声明 transportMode 的条目按列车处理（MTR 内置清单里默认就是列车）
                    if (value != null && !value.isEmpty() && !"TRAIN".equalsIgnoreCase(value)) {
                        continue;
                    }
                }
                addString(out, vehicle.get("id"));
            }
        }

        // legacy 格式的 custom_trains 本身就是列车
        final JsonElement legacy = root.get("custom_trains");
        if (legacy != null) {
            if (legacy.isJsonObject()) {
                for (String key : legacy.getAsJsonObject().keySet()) {
                    addLegacyIds(out, key);
                }
            } else if (legacy.isJsonArray()) {
                for (JsonElement element : legacy.getAsJsonArray()) {
                    if (element != null && element.isJsonObject()) {
                        addString(out, element.getAsJsonObject().get("id"));
                    }
                }
            }
        }
    }

    /** 单份 JSON 里声明的全部车辆 ID。 */
    private static void collectDeclaredIds(JsonObject root, Set<String> out) {
        // ---- 现代格式：vehicles[].id ----
        final JsonElement vehicles = root.get("vehicles");
        if (vehicles != null && vehicles.isJsonArray()) {
            for (JsonElement element : vehicles.getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    addString(out, element.getAsJsonObject().get("id"));
                }
            }
        }

        // ---- legacy 格式：custom_trains 的键名（键名会被 toLowerCase）----
        final JsonElement legacy = root.get("custom_trains");
        if (legacy != null) {
            if (legacy.isJsonObject()) {
                for (String key : legacy.getAsJsonObject().keySet()) {
                    addLegacyIds(out, key);
                }
            } else if (legacy.isJsonArray()) {
                for (JsonElement element : legacy.getAsJsonArray()) {
                    if (element != null && element.isJsonObject()) {
                        addString(out, element.getAsJsonObject().get("id"));
                    }
                }
            }
        }
    }

    /** 把一个 legacy {@code custom_trains} 键名展开成 4 个真实车辆 ID。 */
    private static void addLegacyIds(Set<String> out, String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        final String lowerKey = key.toLowerCase(Locale.ENGLISH);
        for (String variant : LEGACY_VARIANTS) {
            out.add("mtr_custom_train_" + lowerKey + "_" + variant);
        }
    }

    private static void addString(Set<String> out, JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            final String value = element.getAsString();
            if (value != null && !value.isEmpty()) {
                out.add(value);
            }
        }
    }
}
