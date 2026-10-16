package com.mtrdisableoriginaltrains;

import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.resource.CustomResourceTools;
import org.mtr.mod.resource.VehicleResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 车端连接件（风挡 / gangway）的运行时修复 —— 全部在模组内完成，<b>不需要改追加包</b>。
 *
 * <h2>问题：为什么追加包列车的连接件要么不显示、要么紫黑</h2>
 * MTR 4.0.5 用 {@code LegacyVehicleResource.convert} 把追加包里
 * {@code mtr_custom_resources.json → custom_trains} 的旧版车辆定义转成新格式。
 * 转换时连接件贴图的路径是这样拼出来的（反编译原文，第 220-239 行）：
 *
 * <pre>
 * String base = (gangway_connection_id.isEmpty() ? texture_id : gangway_connection_id) + "_connector";
 * boolean legacyName = gangway_connection_id.startsWith("mtr:textures/entity")
 *                      &amp;&amp; 任何一个 legacyGangways 名字（a_train/e44/k_train/s_train/...）
 *                        能让 gangway_connection_id.startsWith("mtr:textures/entity/" + 名字);
 * if (legacyName) {
 *     // 只有走这一支，才会得到“真正存在”的贴图
 *     base = base.replace("/entity", "/gangway").replace("_connector", "");
 * }
 * modelProperties.gangwayInnerSideResource = base + "_side.png";   // 另有 _roof / _floor / _exterior
 * </pre>
 *
 * <p>也就是说，<b>作者必须写 {@code mtr:textures/entity/s_train} 这种「entity 前缀」的旧值</b>，
 * MTR 才会去掉 {@code _connector} 并把 {@code /entity} 换成 {@code /gangway}。
 *
 * <p>而 Shanghai Metro Addon v3.3.10 原包里写的是
 * {@code mtr:textures/gangway/s_train}（62 辆车里 48 辆都是这个），
 * 不满足上面那个条件 → 拼出 {@code mtr:textures/gangway/s_train_connector_side.png}，
 * <b>这个文件在 MTR 里并不存在</b>（MTR 只有 {@code gangway/s_train_side.png}），
 * 于是连接件要么画成紫黑缺省贴图，要么因为贴图缺失而看不见。唯一正确的写法与
 * 它只差一个 {@code _connector}。
 *
 * <h2>修复思路</h2>
 * 在 {@code ModelProperties} 构造完成、12 个贴图 {@link Identifier} 已经算出来之后
 * （见 {@code ModelPropertiesMixin}），逐个做「存在性检查 + 等价改写」：
 * <ol>
 *   <li>声明的贴图<b>确实存在</b> → 原样保留（正常追加包/本体不受影响）；</li>
 *   <li>声明的不存在，但<b>去掉 {@code _connector} 之后存在</b> →
 *       替换成后者。这一步正是把作者写错的
 *       {@code gangway/s_train_connector_side.png} 还原成 {@code gangway/s_train_side.png}，
 *       <b>等于把「改追加包」这件事实时做在内存里</b>；</li>
 *   <li>两种都不存在（例如作者用 {@code mtr:air} / {@code mtr:entity/air} 表示「这车不要连接件」，
 *       或压根没声明）→ 换成 MTR 自带的透明贴图 {@code mtr:textures/block/transparent.png}。
 *       效果等于「什么都没画」，不会出现紫黑块。</li>
 * </ol>
 *
 * <p>配合 {@code VehicleResourceMixin} 把 {@code hasGangway1/2()} 强制为 true，
 * 车厢之间<b>每一处接缝</b>都会尝试画连接件；列车最外侧的头/尾因为没有相邻车厢，
 * 会被 MTR 自身的 {@code previousConnectionPositions.isValid()} 拦住，不会多画东西。
 *
 * <h2>安全性</h2>
 * <ul>
 *   <li>资源管理器还没就绪时（存在性探测一律返回 false），只做上面第 2 步那种
 *       <b>纯字符串</b>改写，绝不会把本来就正常的贴图误判成缺失；等管理器就绪后会自动重新判定；</li>
 *   <li>存在性探测走原版 {@code ResourceManager.getResource(id).isPresent()}，
 *       只查索引、不读文件内容，开销可以忽略；
 *       结果按路径缓存，正向结果永久缓存、反向结果只在确认管理器可用后缓存；</li>
 *   <li>配置 {@code fixGangway=false} 可以整体关闭本修复。</li>
 * </ul>
 */
public final class GangwayFix {

    /**
     * 连接件贴图缺失时的兜底贴图。
     * {@code mtr:textures/block/transparent.png} 是 MTR 自带的完全透明贴图，
     * 和 {@code CustomResourceTools.formatIdentifierWithDefault} 用的兜底是同一张。
     */
    private static final String TRANSPARENT_PATH = "mtr:textures/block/transparent.png";

    /** 旧版转换器会给连接件基名补上的后缀（也是唯一需要还原的东西）。 */
    private static final String LEGACY_CONNECTOR_SUFFIX = "_connector";

    private static final Map<String, Boolean> EXISTENCE_CACHE = new ConcurrentHashMap<>();

    private static volatile Boolean resourceManagerUsable;
    private static volatile Identifier transparentIdentifier;

    private static volatile int repairedConnectorCount;
    private static volatile int transparentFallbackCount;
    private static volatile int forcedVehicleCount;

    private GangwayFix() {
    }

    // ------------------------------------------------------------------
    // 对外入口
    // ------------------------------------------------------------------

    /**
     * 修补一个连接件（或屏蔽门）贴图。
     *
     * @param current    MTR 已经算好的贴图；{@code null} 表示该车辆根本没声明这个贴图
     * @param isGangway  true = 风挡（缺失时兜底为透明贴图并保持可渲染）；
     *                   false = 屏蔽门（缺失时保持 null，即按原样不渲染）
     * @return 修补后的贴图（可能仍是 {@code current}，也可能是替换/兜底结果）
     */
    public static Identifier repair(Identifier current, boolean isGangway) {
        if (current == null) {
            // 没声明：给透明贴图，这样「强制渲染」不会画出紫黑缺省贴图
            return isGangway ? transparent() : null;
        }
        if (!BuiltinTrainFilter.isGangwayFixEnabled()) {
            return current;
        }
        if (!isResourceManagerUsable()) {
            // 资源管理器还没就绪：存在性判断不可信，只做确定性改写
            return repairByStringOnly(current);
        }
        final String original = name(current);
        if (exists(original)) {
            return current;
        }
        final String stripped = stripConnector(original);
        if (!stripped.equals(original)) {
            final Identifier candidate = parse(stripped);
            if (candidate != null && exists(name(candidate))) {
                repairedConnectorCount++;
                MtrDisableOriginalTrains.LOGGER.info(
                        "[MTR_NoOriginalTrains] 连接件贴图已自动修正：{} → {}（追加包把旧的 entity 写法写成了 gangway 写法）",
                        original, name(candidate));
                return candidate;
            }
        }
        if (isGangway) {
            transparentFallbackCount++;
            return transparent();
        }
        return null;
    }

    /**
     * 是否要把该车辆的 {@code hasGangway1()/hasGangway2()} 强制为 true。
     *
     * <p>MTR 原本按「车厢朝向」（{@code Variation} 的位）关掉某些车端，例如
     * {@code CAB_2/CAB_3} 两端全关。只要车厢编组/朝向稍有出入，接缝上就没有连接件。
     * 强制打开后，<b>每一处有相邻车厢的接缝都会画</b>；列车最外侧的车端因为
     * {@code previousConnectionPositions} 还是空的不满足 {@code isValid()}，
     * 自然什么也不画，所以不会出现「车头莫名其妙挂了张风挡」。
     *
     * <p>本模组只对「不是被移除的内置原版列车」的车辆这样做，内置列车保持 MTR 原状。
     */
    public static boolean shouldForceGangway(VehicleResource vehicle) {
        if (vehicle == null || !BuiltinTrainFilter.isGangwayFixEnabled()) {
            return false;
        }
        try {
            if (!BuiltinTrainFilter.isRemovable(vehicle)) {
                forcedVehicleCount++;
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    /** 本次资源重载的修复汇总（由 CustomResourceLoaderMixin 在 reload 结束时调用）。 */
    public static void logSummary() {
        if (repairedConnectorCount == 0 && transparentFallbackCount == 0) {
            return;
        }
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 连接件修复：自动修正贴图路径 {} 处，"
                        + "回退为透明贴图 {} 处（作者未声明连接件贴图的车辆）—— 追加包 zip 无需改动",
                repairedConnectorCount, transparentFallbackCount);
        repairedConnectorCount = 0;
        transparentFallbackCount = 0;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 只做字符串层面的等价改写（不依赖资源管理器），用于管理器还没就绪时。 */
    private static Identifier repairByStringOnly(Identifier current) {
        final String original = name(current);
        final String stripped = stripConnector(original);
        if (stripped.equals(original)) {
            return current;
        }
        final Identifier candidate = parse(stripped);
        return candidate == null ? current : candidate;
    }

    private static String stripConnector(String path) {
        return path.contains(LEGACY_CONNECTOR_SUFFIX) ? path.replace(LEGACY_CONNECTOR_SUFFIX, "") : path;
    }

    /** MTR 的 {@code Identifier} 包装类没有覆写 toString，字符串要自己拼。 */
    private static String name(Identifier identifier) {
        return identifier.getNamespace() + ":" + identifier.getPath();
    }

    private static Identifier parse(String path) {
        try {
            return CustomResourceTools.formatIdentifier(path, "png");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Identifier transparent() {
        Identifier cached = transparentIdentifier;
        if (cached == null) {
            cached = parse(TRANSPARENT_PATH);
            if (cached != null) {
                transparentIdentifier = cached;
            }
        }
        return cached;
    }

    /**
     * 资源是否存在（带缓存）。
     *
     * <p>资源管理器没就绪时，所有探测都会失败，此时<b>不缓存否定结果</b>，
     * 免得把「暂时读不到」永久记成「不存在」。
     */
    public static boolean exists(String path) {
        final Boolean cached = EXISTENCE_CACHE.get(path);
        if (cached != null) {
            return cached;
        }
        final boolean found = rawExists(parse(path));
        if (found) {
            EXISTENCE_CACHE.put(path, Boolean.TRUE);
        } else if (isResourceManagerUsable()) {
            EXISTENCE_CACHE.put(path, Boolean.FALSE);
        }
        return found;
    }

    /**
     * 不做任何缓存的存在性探测。
     *
     * <p>这里直接用原版 API（{@code Minecraft.getResourceManager().getResource(...)}）
     * 而不是 MTR 的 {@code ResourceManagerHelper.readResource(Identifier, Consumer)}：
     * 后者在 MTR 里还有一个签名为「原版中间名类」的私有重载，直接调容易在解析重载时踩坑。
     */
    private static boolean rawExists(Identifier identifier) {
        if (identifier == null) {
            return false;
        }
        try {
            final net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client == null) {
                return false;
            }
            final net.minecraft.server.packs.resources.ResourceManager resourceManager = client.getResourceManager();
            if (resourceManager == null) {
                return false;
            }
            final net.minecraft.resources.ResourceLocation vanillaIdentifier =
                    net.minecraft.resources.ResourceLocation.tryParse(
                            identifier.getNamespace() + ":" + identifier.getPath());
            return resourceManager.getResource(vanillaIdentifier).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 资源管理器是否已经可用。
     * 用「MTR 必定自带的透明贴图能不能读到」当探针；一旦确认可用就永久记住。
     */
    private static boolean isResourceManagerUsable() {
        if (Boolean.TRUE.equals(resourceManagerUsable)) {
            return true;
        }
        if (rawExists(transparent())) {
            resourceManagerUsable = Boolean.TRUE;
            return true;
        }
        return false;
    }
}
