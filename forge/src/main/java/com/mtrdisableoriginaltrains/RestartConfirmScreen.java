package com.mtrdisableoriginaltrains;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 保存列车选择后的「是否立即重启」确认弹窗。
 *
 * <h2>布局（自上而下）</h2>
 * <pre>
 *                 是否重启游戏以生效？        ← 顶部标题
 *          列车保留选择已保存，需要重启……
 *
 *      ┌──────────────────────────────────────┐
 *      │  [是，立即重启]      [否，稍后重启]  │   ← 左下 / 右下两个按钮
 *      │        ☐ 不再显示此界面              │   ← 两按钮正上方的勾选框
 *      │              [取消]                  │   ← 两按钮下方的取消
 *      └──────────────────────────────────────┘
 * </pre>
 *
 * <h2>语义</h2>
 * <ul>
 *   <li><b>是，立即重启</b> —— 立刻结束游戏进程，等用户自己再启动；</li>
 *   <li><b>否，稍后重启</b> —— 关闭弹窗回到主菜单，用户自己择时关闭并重启；</li>
 *   <li><b>不再显示此界面</b> —— 勾选后记住「刚才点的是是还是否」，
 *       以后保存直接执行该动作，不再弹窗；</li>
 *   <li><b>取消</b> —— 什么都不做，原样返回（不记忆、不重启）。</li>
 * </ul>
 *
 * <p>「立即结束」用 {@link Minecraft#stop()}：它会走正常的停止流程，
 * 不会把「崩溃」写进日志。
 */
public class RestartConfirmScreen extends Screen {

    private static final String LANG_PREFIX = "gui.mtr_no_original_trains.";

    /** 按钮标准高度。 */
    private static final int BTN_H = 20;
    /** 面板尺寸。 */
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 148;
    /** 面板内各行的纵向偏移（相对面板顶端）。 */
    private static final int OFF_TITLE = 12;
    private static final int OFF_SUBTITLE = 28;
    private static final int OFF_CHECKBOX = 56;
    private static final int OFF_BUTTONS = 80;
    private static final int OFF_BOTTOM = PANEL_H - 32;

    /** 是否勾选「不再显示此界面」。 */
    private boolean neverAskAgain = false;

    /** 点「否，稍后重启」后回到的界面（主菜单）。 */
    private final Screen parent;
    /** 点「取消」/ ESC 后退回的界面（选择界面）。 */
    private final Screen backTo;
    /**
     * 点「是」或「否」时才执行的落盘动作。
     *
     * <p>故意<b>不</b>在弹出本界面之前写盘：这样用户点「取消」退回选择界面时，
     * 磁盘上还是旧配置，界面上的新勾选也只是内存状态，取消才真的等于「反悔」。
     */
    private final Runnable onConfirm;

    /** 勾选框的屏幕坐标（自绘，用坐标命中检测，避免按钮底图把对勾盖住）。 */
    private int boxX;
    private int boxY;
    /** 勾选框文字起点。 */
    private int boxLabelX;

    public RestartConfirmScreen(Screen parent, Screen backTo, Runnable onConfirm) {
        super(Component.translatable(LANG_PREFIX + "restart_title"));
        this.parent = parent;
        this.backTo = backTo;
        this.onConfirm = onConfirm;
    }

    public RestartConfirmScreen(Screen parent, Screen backTo) {
        this(parent, backTo, null);
    }

    public RestartConfirmScreen(Screen parent) {
        this(parent, parent, null);
    }

    static Component tr(String key, Object... args) {
        return Component.translatable(LANG_PREFIX + key, args);
    }

    // ── 布局计算 ────────────────────────────────────────────────────────────

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        final int px = panelX();
        final int py = panelY();

        // 两按钮行：左「是」右「否」，各占一半（留 8px 中缝）
        final int btnW = (PANEL_W - 8 - 24) / 2;
        final int btnY = py + OFF_BUTTONS;
        final int leftX = px + 12;
        final int rightX = leftX + btnW + 8;

        this.addRenderableWidget(Button.builder(
                        tr("restart_yes"), b -> confirm(true))
                .bounds(leftX, btnY, btnW, BTN_H).build());
        this.addRenderableWidget(Button.builder(
                        tr("restart_no"), b -> confirm(false))
                .bounds(rightX, btnY, btnW, BTN_H).build());

        // 勾选框：位于两按钮正上方（自绘）
        this.boxX = px + 14;
        this.boxY = py + OFF_CHECKBOX;
        this.boxLabelX = this.boxX + 14 + 6;

        // 取消：位于两按钮下方，居中
        final int cancelW = 100;
        this.addRenderableWidget(Button.builder(
                        tr("restart_cancel"), b -> cancel())
                .bounds(px + (PANEL_W - cancelW) / 2, py + OFF_BOTTOM, cancelW, BTN_H).build());
    }

    // ── 动作 ────────────────────────────────────────────────────────────────

    private void confirm(boolean restartNow) {
        // 只有在用户真的做了选择（是 / 否）时才落盘；取消不落盘。
        if (this.onConfirm != null) {
            this.onConfirm.run();
        }
        RestartPromptConfig.remember(restartNow, neverAskAgain);
        if (restartNow) {
            MtrDisableOriginalTrains.LOGGER.info(
                    "[MTR_NoOriginalTrains] 用户选择立即重启，正在关闭游戏进程（之后由用户自行启动）");
            final Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
            mc.stop();
        } else {
            MtrDisableOriginalTrains.LOGGER.info(
                    "[MTR_NoOriginalTrains] 用户选择稍后自己重启，返回主菜单");
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }
    }

    /** 取消：不落盘、不重启，退回选择界面并保留用户当前勾选。 */
    private void cancel() {
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 用户取消重启提示，返回选择界面（本次改动尚未保存）");
        if (this.backTo instanceof TrainSelectScreen select) {
            // 告诉它：这次回来别从磁盘重载，保留内存里的勾选
            select.prepareResume();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.backTo != null ? this.backTo : this.parent);
        }
    }

    @Override
    public void onClose() {
        // ESC 等同「取消」
        cancel();
    }

    // ── 渲染 ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        final int px = panelX();
        final int py = panelY();

        // 面板底
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF2B2B2B);
        ctx.renderOutline(px, py, PANEL_W, PANEL_H, 0xFF5A5A5A);
        ctx.renderOutline(px + 1, py + 1, PANEL_W - 2, PANEL_H - 2, 0xFF1E1E1E);

        // 顶部标题
        ctx.drawCenteredString(this.font, this.title, this.width / 2, py + OFF_TITLE, 0xFFFFFF);
        ctx.drawCenteredString(this.font, tr("restart_subtitle"),
                this.width / 2, py + OFF_SUBTITLE, 0xA0A0A0);

        super.render(ctx, mouseX, mouseY, delta);

        // 勾选框（自绘，画在按钮之后，确保不被任何按钮底图覆盖）
        drawCheckbox(ctx, mouseX, mouseY);
    }

    private void drawCheckbox(GuiGraphics ctx, int mouseX, int mouseY) {
        final boolean hover = isCheckboxHit(mouseX, mouseY);

        final int bg = neverAskAgain ? (hover ? 0xFF2A3A2A : 0xFF223022)
                : (hover ? 0xFF3A3A3A : 0xFF1E1E1E);
        ctx.fill(boxX, boxY, boxX + 14, boxY + 14, bg);
        ctx.renderOutline(boxX, boxY, 14, 14, neverAskAgain ? 0xFF6FCF6F : 0xFF808080);

        if (neverAskAgain) {
            // ✓ 对勾：几笔拼出来（粗一点，一眼能看清）
            ctx.fill(boxX + 3, boxY + 7, boxX + 6, boxY + 10, 0xFF5FBF5F);
            ctx.fill(boxX + 5, boxY + 8, boxX + 8, boxY + 11, 0xFF5FBF5F);
            ctx.fill(boxX + 7, boxY + 7, boxX + 8, boxY + 9, 0xFF5FBF5F);
            ctx.fill(boxX + 8, boxY + 5, boxX + 11, boxY + 8, 0xFF5FBF5F);
            ctx.fill(boxX + 10, boxY + 3, boxX + 12, boxY + 6, 0xFF5FBF5F);
        }

        ctx.drawString(this.font, tr("restart_never"),
                boxLabelX, boxY + 3, hover ? 0xFFFFFF : 0xD0D0D0, false);
    }

    /** 勾选框 + 右侧文字的可点击区域（点文字也能勾上，不用非得点中小方块）。 */
    private boolean isCheckboxHit(double mouseX, double mouseY) {
        final int labelW = this.font.width(tr("restart_never"));
        return mouseX >= boxX && mouseX < boxLabelX + labelW
                && mouseY >= boxY - 2 && mouseY < boxY + 16;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 勾选框命中（自绘，没有对应 Button）
        if (button == 0 && isCheckboxHit(mouseX, mouseY)) {
            neverAskAgain = !neverAskAgain;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
