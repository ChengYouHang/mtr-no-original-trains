package com.mtrdisableoriginaltrains;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 列车选择入口页：原版列车 / 追加包列车 / 取消。
 *
 * <ul>
 *   <li>「原版列车」→ {@link TrainSelectScreen}（1.2.0 起的保留选择界面，行为不变）；</li>
 *   <li>「追加包列车」→ {@link AddonSelectScreen}（1.3.0 新增，按追加包分组拦截）；</li>
 *   <li>「取消」→ 返回主菜单。</li>
 * </ul>
 *
 * <p>单独拆一页而不是在原界面上加页签，是为了让 1.2.0 的「保留」语义
 * （勾选 = 留下）与追加包页的「拦截」语义（勾选 = 移除）互不干扰。
 */
public class TrainSelectHubScreen extends Screen {

    private static final String LANG_PREFIX = "gui.mtr_no_original_trains.";

    private final Screen parent;

    public TrainSelectHubScreen(Screen parent) {
        super(Text.translatable(LANG_PREFIX + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int bw = 200;
        final int x = this.width / 2 - bw / 2;
        int y = Math.max(40, this.height / 2 - 50);

        this.addDrawableChild(ButtonWidget.builder(tr("tab_builtin"), b ->
                        this.client.setScreen(new TrainSelectScreen(this)))
                .dimensions(x, y, bw, 20).build());
        y += 24;
        this.addDrawableChild(ButtonWidget.builder(tr("tab_addon"), b ->
                        this.client.setScreen(new AddonSelectScreen(this)))
                .dimensions(x, y, bw, 20).build());
        y += 30;
        this.addDrawableChild(ButtonWidget.builder(tr("cancel"), b -> this.goBack())
                .dimensions(x, y, bw, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 16, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void goBack() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        this.goBack();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    static Text tr(String key, Object... args) {
        return Text.translatable(LANG_PREFIX + key, args);
    }
}
