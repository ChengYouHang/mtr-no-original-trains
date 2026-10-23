package com.mtrdisableoriginaltrains.mixin;

import com.mtrdisableoriginaltrains.MtrAvailability;
import com.mtrdisableoriginaltrains.TitleButtonPlacer;
import com.mtrdisableoriginaltrains.TrainSelectScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 在主界面（{@link TitleScreen}）加入「MTR 列车保留选择」按钮。
 *
 * <h2>注入点</h2>
 * 选 {@code init()} 的 TAIL：此时原版以及 Mod Menu 等模组已经把各自的按钮
 * 全部 {@code addDrawableChild} 完毕，可以据此算出哪些区域被占用。
 *
 * <h2>按钮位置</h2>
 * 交给 {@link TitleButtonPlacer#find} 扫描所有已有控件，在「Mod 行」与
 * 「选项/退出行」之间腾出一整行放本按钮（必要时把下方按钮整体下移一行）。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mtrNoOriginalTrains$addTrainSelectButton(CallbackInfo ci) {
        if (!MtrAvailability.isPresent()) {
            return;
        }

        // 收集当前界面上所有已有控件的矩形，交给放置器挑位置
        final List<ClickableWidget> widgets = new ArrayList<>();
        for (final Element e : this.children()) {
            if (e instanceof ClickableWidget cw) {
                widgets.add(cw);
            }
        }
        final List<TitleButtonPlacer.Rect> occupied = new ArrayList<>();
        for (final ClickableWidget cw : widgets) {
            occupied.add(new TitleButtonPlacer.Rect() {
                @Override
                public int x() {
                    return cw.getX();
                }

                @Override
                public int y() {
                    return cw.getY();
                }

                @Override
                public int w() {
                    return cw.getWidth();
                }

                @Override
                public int h() {
                    return cw.getHeight();
                }
            });
        }

        final TitleButtonPlacer.Plan plan =
                TitleButtonPlacer.find(this.width, this.height, occupied);
        if (plan == null) {
            return;
        }

        // 按放置器的计划把下方控件整体下移，腾出按钮的位置
        if (plan.shiftDelta > 0) {
            for (final ClickableWidget cw : widgets) {
                if (cw.getY() >= plan.shiftFromY) {
                    cw.setY(cw.getY() + plan.shiftDelta);
                }
            }
        }

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.mtr_no_original_trains.button"),
                        b -> {
                            final MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc != null) {
                                mc.setScreen(new TrainSelectScreen(this));
                            }
                        })
                .dimensions(plan.placement.x, plan.placement.y,
                        plan.placement.w, plan.placement.h)
                .build());
    }
}
