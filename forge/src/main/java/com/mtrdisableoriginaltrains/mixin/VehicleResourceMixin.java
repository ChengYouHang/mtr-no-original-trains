package com.mtrdisableoriginaltrains.mixin;

import com.mtrdisableoriginaltrains.GangwayFix;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让车厢之间<b>每一处接缝</b>都渲染连接件（风挡）。
 *
 * <h2>MTR 原本的判定</h2>
 * 连接件渲染的唯一开关在 {@code RenderVehicles.renderConnection(...)}：
 * <pre>
 * private static void renderConnection(boolean shouldRender1, boolean shouldRender2, ...) {
 *     if (shouldRender1 &amp;&amp; previousConnectionPositions.isValid()) { ...画 6 个面... }
 *     if (shouldRender2) { previousConnectionPositions.position1..4 = ...本车端面...; }
 *     else              { previousConnectionPositions.position1..4 = null; }
 * }
 * </pre>
 * 调用处（{@code RenderVehicles.lambda$render$12}）传的是
 * {@code vehicleResource.hasGangway1(), vehicleResource.hasGangway2()}。
 *
 * <p>而 {@code hasGangway1/2} 是 {@code LegacyVehicleResource.convert} 按
 * <b>车厢朝向</b>算出来的（反编译原文第 120-121 行）：
 * <pre>
 * baseObject.addProperty("hasGangway1", (!gangway_connection_id.isEmpty() || has_gangway_connection) &amp;&amp; (i &amp; 1) == 0);
 * baseObject.addProperty("hasGangway2", (!gangway_connection_id.isEmpty() || has_gangway_connection) &amp;&amp; (i &amp; 2) == 0);
 * </pre>
 * {@code i} 是 {@code Variation.ordinal()}（TRAILER=0 / CAB_1=1 / CAB_2=2 / CAB_3=3），于是：
 * <ul>
 *   <li>TRAILER 两端都画；</li>
 *   <li>CAB_1 只画靠内那一端；CAB_2 只画靠内那一端；</li>
 *   <li><b>CAB_3（双头车）两端都不画</b>；</li>
 *   <li>{@code gangway_connection_id} 为空<b>且</b>没写 {@code has_gangway_connection} 时，
 *       <b>整列车一个连接件都不画</b>。</li>
 * </ul>
 * 只要编组方式、车厢朝向和作者预设的不完全一致（单节、两节、头车挨头车、手动拼编组……），
 * 接缝上就什么都看不到 —— 这就是「还是没有连接件」的来源之一。
 *
 * <h2>本模组做的事</h2>
 * 把 {@code hasGangway1()/hasGangway2()} 直接拦成 {@code true}（仅对<b>非内置原版列车</b>）。
 * 因为列车最外侧的头/尾车端没有「上一节车厢」保存下来的端面，
 * {@code previousConnectionPositions.isValid()} 会返回 false，<b>自然什么都不画</b>，
 * 所以强制打开不会在车头车尾多出莫名其妙的风挡，只会把「该有却没有」的接缝补上。
 *
 * <p>副作用核对过：这两个开关在 {@code RenderVehicles} 里只有三处用途 ——
 * ① 画风挡；② 往「可行走地板」里加车端连接处的地板（蓝线，只在玩家手持刷子时显示）；
 * ③ 传给 {@code VehicleRidingMovement.movePlayer} 允许玩家在两节车厢连接处走动。
 * 三者都是「有连接件时本来就该有」的行为，正常游玩没有任何可见副作用。
 */
@Mixin(value = VehicleResource.class, remap = false)
public abstract class VehicleResourceMixin {

    @Inject(method = "hasGangway1", at = @At("HEAD"), cancellable = true, remap = false)
    private void mtrNoOriginalTrains$forceGangway1(CallbackInfoReturnable<Boolean> cir) {
        if (GangwayFix.shouldForceGangway((VehicleResource) (Object) this)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Inject(method = "hasGangway2", at = @At("HEAD"), cancellable = true, remap = false)
    private void mtrNoOriginalTrains$forceGangway2(CallbackInfoReturnable<Boolean> cir) {
        if (GangwayFix.shouldForceGangway((VehicleResource) (Object) this)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
