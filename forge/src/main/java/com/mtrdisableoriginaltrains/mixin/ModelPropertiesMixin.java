package com.mtrdisableoriginaltrains.mixin;

import com.mtrdisableoriginaltrains.GangwayFix;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.resource.ModelProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code ModelProperties(ReaderBase)} 构造完成后，修补 6 张连接件贴图 + 6 张屏蔽门贴图。
 *
 * <h2>为什么选这个注入点</h2>
 * 反编译 4.0.5 原文，{@code ModelProperties} 的公有构造器是<b>唯一</b>把
 * {@code gangwayInnerSideResource} 这类<b>字符串</b>资源路径变成真正 {@code Identifier}
 * 的地方：
 * <pre>
 * public ModelProperties(ReaderBase readerBase) {
 *     super(readerBase);
 *     this.updateData(readerBase);
 *     this.gangwayInnerSideTexture = CustomResourceTools.formatIdentifier(this.gangwayInnerSideResource, "png");
 *     ... 共 12 个 ...
 * }
 * </pre>
 *
 * <p>注入 {@code TAIL} 之后，这 12 个 {@code Identifier} 已经算好但还没被任何地方读走，
 * 正好是「改一次、全局生效」的位置 —— 它同时覆盖<b>追包遗留格式（转换器现场拼路径）</b>与
 * <b>新格式追加包（直接从 model_properties 读）</b>两条路径。
 *
 * <p>{@code RenderVehicles.renderConnection} 只认这几个 {@code Identifier}，
 * 与 {@code gangway_connection_id} 早已无关，所以在这里修补等价于改追加包数据，
 * 但不需要动用户任何一个 zip。
 *
 * <p>这 12 个字段在 MTR 里是 {@code public final}，因此 Shadow 时必须加
 * {@code @Final @Mutable} 才能赋值。MTR 类名不参与 remap，全部 {@code remap = false}。
 */
@Mixin(value = ModelProperties.class, remap = false)
public abstract class ModelPropertiesMixin {

    // ---- 风挡（gangway）：缺失时回退为透明贴图，保证「强制渲染」不会画出紫黑 ----

    @Shadow @Final @Mutable public Identifier gangwayInnerSideTexture;
    @Shadow @Final @Mutable public Identifier gangwayInnerTopTexture;
    @Shadow @Final @Mutable public Identifier gangwayInnerBottomTexture;
    @Shadow @Final @Mutable public Identifier gangwayOuterSideTexture;
    @Shadow @Final @Mutable public Identifier gangwayOuterTopTexture;
    @Shadow @Final @Mutable public Identifier gangwayOuterBottomTexture;

    // ---- 屏蔽门（barrier）：缺失时保持 null（本模组的强制渲染只管风挡） ----

    @Shadow @Final @Mutable public Identifier barrierInnerSideTexture;
    @Shadow @Final @Mutable public Identifier barrierInnerTopTexture;
    @Shadow @Final @Mutable public Identifier barrierInnerBottomTexture;
    @Shadow @Final @Mutable public Identifier barrierOuterSideTexture;
    @Shadow @Final @Mutable public Identifier barrierOuterTopTexture;
    @Shadow @Final @Mutable public Identifier barrierOuterBottomTexture;

    @Inject(method = "<init>(Lorg/mtr/core/serializer/ReaderBase;)V", at = @At("TAIL"), remap = false)
    private void mtrNoOriginalTrains$repairConnectionTextures(ReaderBase readerBase, CallbackInfo ci) {
        gangwayInnerSideTexture = GangwayFix.repair(gangwayInnerSideTexture, true);
        gangwayInnerTopTexture = GangwayFix.repair(gangwayInnerTopTexture, true);
        gangwayInnerBottomTexture = GangwayFix.repair(gangwayInnerBottomTexture, true);
        gangwayOuterSideTexture = GangwayFix.repair(gangwayOuterSideTexture, true);
        gangwayOuterTopTexture = GangwayFix.repair(gangwayOuterTopTexture, true);
        gangwayOuterBottomTexture = GangwayFix.repair(gangwayOuterBottomTexture, true);

        barrierInnerSideTexture = GangwayFix.repair(barrierInnerSideTexture, false);
        barrierInnerTopTexture = GangwayFix.repair(barrierInnerTopTexture, false);
        barrierInnerBottomTexture = GangwayFix.repair(barrierInnerBottomTexture, false);
        barrierOuterSideTexture = GangwayFix.repair(barrierOuterSideTexture, false);
        barrierOuterTopTexture = GangwayFix.repair(barrierOuterTopTexture, false);
        barrierOuterBottomTexture = GangwayFix.repair(barrierOuterBottomTexture, false);
    }
}
