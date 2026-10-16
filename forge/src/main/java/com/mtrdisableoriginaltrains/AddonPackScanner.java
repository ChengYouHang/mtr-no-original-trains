package com.mtrdisableoriginaltrains;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 扫描「除 MTR 本体之外的所有资源包」各自声明了哪些车辆 ID，供黑名单过滤时「绕行」用。
 *
 * <h2>要解决的问题</h2>
 * MTR 的 {@code CustomResourceLoader.reload()} 用
 * {@code ResourceManagerHelper.readAllResources(Identifier("mtr","mtr_custom_resources.json"))}
 * 把<b>所有资源包</b>里同名的这份 JSON <b>逐个</b>读进来，各自注册自己的车辆
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
 *   <li>用 {@link ResourceManager#getAllResources(Identifier)} 拿到每个资源包各自的副本，
 *       每份都带 {@link Resource#getResourcePackName()}，能看出它属于哪个包；</li>
 *   <li>把每份里声明的车辆 ID 全部抽出来（现代格式 {@code vehicles[].id}，
 *       以及 legacy 格式 {@code custom_trains} 的键名展开）；</li>
 *   <li><b>识别哪一份是 MTR 本体</b>：本体那份必然包含内置黑名单里的全部 ID
 *       （{@code idsOfPack.containsAll(builtinIds)}），其余一律视为「其他资源包」。</li>
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
     * @param builtinIds 内置黑名单（用于识别哪一份是 MTR 本体清单）
     */
    public static void scan(Set<String> builtinIds) {
        final Set<String> collectedIds = new LinkedHashSet<>();
        final Set<String> collectedPacks = new LinkedHashSet<>();
        boolean foundBuiltin = false;
        String builtinName = "未识别";
        int skipped = 0;

        try {
            final ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            // Forge（官方映射）里取「同一 ID 的全部来源」是 getResourceStack，
            // 对应 Fabric/Yarn 的 getAllResources。
            final List<Resource> resources = resourceManager.getResourceStack(CUSTOM_RESOURCES_ID);
            for (Resource resource : resources) {
                // Forge 的 Resource#sourcePackId() 提供资源包标识（如 "mtr" / "file/xxx.zip"）。
                final String packName = String.valueOf(resource.sourcePackId());
                final Set<String> idsOfPack = new LinkedHashSet<>();
                try (InputStream in = resource.open();
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    final JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) {
                        skipped++;
                        continue;
                    }
                    collectDeclaredIds(parsed.getAsJsonObject(), idsOfPack);
                } catch (Throwable t) {
                    // 单份解析失败只跳过这一份，不影响其它包
                    skipped++;
                    continue;
                }

                if (idsOfPack.isEmpty()) {
                    continue;
                }

                if (!builtinIds.isEmpty() && !foundBuiltin && idsOfPack.containsAll(builtinIds)) {
                    // 同时声明了全部内置列车 ID —— 这一份就是 MTR 本体自带的那份清单，不参与守卫
                    foundBuiltin = true;
                    builtinName = packName;
                    continue;
                }

                collectedIds.addAll(idsOfPack);
                collectedPacks.add(packName);
            }
        } catch (Throwable t) {
            MtrDisableOriginalTrains.LOGGER.warn(
                    "[MTR_NoOriginalTrains] 扫描 mtr_custom_resources.json 失败，本次不启用「不拦截其他资源包」守卫"
                            + "（退回仅按内置黑名单移除）", t);
            declaredIds = Collections.emptySet();
            sourcePacks = Collections.emptySet();
            builtinFound = false;
            builtinPackName = "未识别";
            active = false;
            return;
        }

        declaredIds = collectedIds;
        sourcePacks = collectedPacks;
        builtinFound = foundBuiltin;
        builtinPackName = builtinName;
        // 识别不出本体清单时保守起见不启用守卫：宁可继续移除内置车，也不要因为误判而全部保留
        active = builtinIds.isEmpty() || foundBuiltin;

        if (!foundBuiltin && !builtinIds.isEmpty()) {
            MtrDisableOriginalTrains.LOGGER.warn(
                    "[MTR_NoOriginalTrains] 未能在资源包中识别出 MTR 本体清单"
                            + "（共读到 {} 份 mtr_custom_resources.json，解析失败 {} 份），"
                            + "本次不启用「不拦截其他资源包」守卫",
                    collectedPacks.size() + skipped + 1, skipped);
        }
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
