package com.mtrdisableoriginaltrains.mixin;

import com.mtrdisableoriginaltrains.BuiltinTrainFilter;
import com.mtrdisableoriginaltrains.GangwayFix;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.MinecraftModelResource;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 MTR 4.0.5 的车辆注册入口 {@code CustomResourceLoader.registerVehicle(VehicleResource, boolean)}。
 *
 * <h2>registerVehicle 的真实字节码结构（4.0.5，逐条核对）</h2>
 * <pre>
 *   0-17:  VEHICLES.get(transportMode).add(vehicle)              ← 车场/生成列表的唯一来源
 *   18-47: VEHICLES_CACHE.get(transportMode).put(id, Pair(v, flag))  ← 按 id 反查（getVehicleById）
 *   48-64: vehicle.collectTags(VEHICLES_TAGS.get(transportMode))
 *   65-76: if (!flag) vehicle.writeMinecraftResource(MODEL_RESOURCES, TEXTURE_RESOURCES)
 *   79:    return
 * </pre>
 *
 * <h2>本模组做的两件事（只掐这两处，其余流程原样保留）</h2>
 * <ol>
 *   <li><b>掐掉 0-17 的 {@code add}</b>：命中黑名单的内置列车不进车场列表 →
 *       车上清单里不再出现、也无法生成；</li>
 *   <li><b>掐掉 65-76 的 {@code writeMinecraftResource}</b>：内置列车的模型/贴图不再写入
 *       MTR 动态生成的资源包 → 真正意义上「不加载原版列车模型」。</li>
 * </ol>
 *
 * <p><b>保留 18-47 的 {@code VEHICLES_CACHE.put}</b>：{@code getVehicleById} 仍能查到内置车。
 * 这一点很重要 —— 追加包的 {@code base_train_type} 机制会调用
 * {@code LegacyVehicleResource.convert → CustomResourceLoader.getVehicleById(base + 变体)}，
 * 若把缓存也一起清掉，那些以内置车为底模的追加包列车会解析失败。
 *
 * <h2>为什么按车辆 ID 判定，而不是按那个 boolean</h2>
 * {@code registerVehicle} 的第二个参数 <b>不是</b>「是否追加包」。{@code reload()} 用
 * {@code readAllResources(Identifier("mtr","mtr_custom_resources.json"))} 把<b>所有资源包</b>
 * （MTR 内置包 + 全部追加包）的同名 JSON 全读进来，统一走 {@code lambda$reload$3 / $9} 注册，
 * <b>两者都传 false</b>；唯一传 {@code true} 的是 {@code AbstractResourcePackCreatorServlet}
 * （游戏内资源包创作器的网页预览）。该 boolean 的真实含义是「是否跳过写入 MC 模型/纹理资源集」。
 * 也就是说 <b>MTR 本身不区分「内置车」与「追加包车」</b>，只能按 ID 判。
 *
 * <h2>关于连接件（风挡）—— 已在 v1.5.0 由本模组解决，追加包 zip 无需改动</h2>
 * 追加包 {@code Shanghai Metro Addon} 的 {@code gangway_connection_id} 写的是
 * {@code mtr:textures/gangway/s_train}。而 MTR 的遗留转换器规则是
 * {@code base = gangway_connection_id + "_connector"}，只有当值以
 * {@code mtr:textures/entity/<旧名字>} 开头时才会把 {@code /entity → /gangway} 并去掉
 * {@code _connector}。作者那种写法不满足条件，于是去找并不存在的
 * {@code mtr:textures/gangway/s_train_connector_side.png} → 连接件紫黑/不可见。
 *
 * <p>现在由 {@code ModelPropertiesMixin} + {@link com.mtrdisableoriginaltrains.GangwayFix}
 * 在运行时把这种贴图路径自动还原，并由 {@code VehicleResourceMixin} 保证每处接缝都会渲染，
 * <b>用户手上的追加包一个字节都不用改</b>（改过/没改过都能正常工作）。
 *
 * <p>MTR 使用自己的 mapping 抽象层，类名保持 {@code org.mtr.*} 原样、不参与 remap，
 * 因此这里全部 {@code remap = false}。
 */
@Mixin(value = CustomResourceLoader.class, remap = false)
public abstract class CustomResourceLoaderMixin {

    /** 拦截第 0-17 步：内置列车不进车场/生成列表。 */
    @Redirect(
            method = "registerVehicle(Lorg/mtr/mod/resource/VehicleResource;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;add(Ljava/lang/Object;)Z",
                    remap = false
            ),
            remap = false
    )
    private static boolean mtrNoOriginalTrains$skipBuiltinVehicleAdd(
            ObjectArrayList<VehicleResource> list, Object vehicle, VehicleResource vehicleParam, boolean flag) {
        if (vehicle instanceof VehicleResource && BuiltinTrainFilter.shouldRemove((VehicleResource) vehicle)) {
            return false;
        }
        return list.add((VehicleResource) vehicle);
    }

    /** 拦截第 65-76 步：内置列车的模型/贴图不写入 MTR 动态资源包（不加载原版模型）。 */
    @Redirect(
            method = "registerVehicle(Lorg/mtr/mod/resource/VehicleResource;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/resource/VehicleResource;writeMinecraftResource(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArraySet;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArraySet;)V",
                    remap = false
            ),
            remap = false
    )
    private static void mtrNoOriginalTrains$skipBuiltinVehicleWrite(
            VehicleResource vehicle,
            ObjectArraySet<MinecraftModelResource> modelResources,
            ObjectArraySet<String> textureResources) {
        if (BuiltinTrainFilter.isRemovable(vehicle)) {
            return;
        }
        vehicle.writeMinecraftResource(modelResources, textureResources);
    }

    /**
     * 重载开始前：扫描所有资源包，记录「MTR 本体之外」的包各自声明了哪些车辆 ID。
     *
     * <p>必须在 MTR 真正注册车辆之前跑完，否则 {@code registerVehicle} 判定时拿不到这份名单。
     * MTR 自己就是在 {@code reload()} 体内第一时间调用
     * {@code readAllResources(...)} 读这些 JSON 的，所以在 HEAD 处 {@code ResourceManager} 已经就绪。
     */
    @Inject(method = "reload()V", at = @At("HEAD"), remap = false)
    private static void mtrNoOriginalTrains$beginReload(CallbackInfo ci) {
        BuiltinTrainFilter.beginReload();
    }

    /** 一次资源重载结束后打印汇总，便于在日志里确认生效情况。 */
    @Inject(method = "reload()V", at = @At("TAIL"), remap = false)
    private static void mtrNoOriginalTrains$logSummary(CallbackInfo ci) {
        BuiltinTrainFilter.logReloadSummary();
        GangwayFix.logSummary();
    }
}
