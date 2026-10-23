package com.mtrdisableoriginaltrains;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 列车保留选择界面。
 *
 * <h2>交互设计</h2>
 * <ul>
 *   <li>列表按<b>车系</b>分组，每个车系一行；</li>
 *   <li>车系行的「+ / −」按钮可<b>展开</b>该车系下的每一辆车，逐辆勾选；</li>
 *   <li>车系勾选框反映「整族是否全选」，部分勾选时显示为半选（横杠）；</li>
 *   <li>顶部工具栏：全选 / 全不选 / 恢复默认，对<b>全部车系</b>生效；</li>
 *   <li>底部：完成按钮 + 重启提示。</li>
 * </ul>
 *
 * <h2>默认全部拦截</h2>
 * 首次安装时配置文件里 {@code keepFamilies} / {@code keepIds} 都是空的
 * ——意即<b>全部内置列车都被拦截</b>，与模组名称一致。
 * 用户想留哪套车，就在这里勾选，保存后重启生效。
 *
 * <h2>为什么改完要重启</h2>
 * 车辆注册发生在资源包重载阶段，且 MTR 的 {@code VEHICLES} / {@code VEHICLES_CACHE}
 * 等静态表在运行中重跑 reload 会有残留。本界面只负责<b>写配置</b>，
 * 真正的过滤在下次启动的 {@code reload()} 中由 {@link BuiltinTrainFilter} 完成。
 *
 * <p>界面文案全部走 {@code assets/mtr_no_original_trains/lang/*.json}（42 种语言），
 * 跟随游戏语言自动切换。
 */
public class TrainSelectScreen extends Screen {

    private static final String LANG_PREFIX = "gui.mtr_no_original_trains.";

    private final Screen parent;

    /** 当前勾选的车系（整族保留）。 */
    private final Set<String> keptFamilies = new LinkedHashSet<>();
    /** 当前勾选、走单辆车级别的 ID。 */
    private final Set<String> keptVehicleIds = new LinkedHashSet<>();
    /** 已展开的车系。 */
    private final Set<String> expanded = new LinkedHashSet<>();

    /** 打开界面时的选择快照，用于判断用户是否真的改过东西（没改就不必重启）。 */
    private Set<String> baselineFamilies = new LinkedHashSet<>();
    private Set<String> baselineVehicleIds = new LinkedHashSet<>();

    /**
     * 从重启确认弹窗点「取消」回来时为 true：这一次 {@link #init()} 不要从磁盘重载，
     * 保留用户还没保存的勾选，否则「取消」会变成「把改动吃掉」。
     */
    private boolean preserveState = false;

    /** 由 {@link RestartConfirmScreen#cancel()} 调用，标记「马上要回到本界面，别重载」。 */
    void prepareResume() {
        this.preserveState = true;
    }

    private TrainListWidget list;

    public TrainSelectScreen(Screen parent) {
        super(Component.translatable(LANG_PREFIX + "title"));
        this.parent = parent;
    }

    static Component tr(String key, Object... args) {
        return Component.translatable(LANG_PREFIX + key, args);
    }

    @Override
    protected void init() {
        final ConfigAccess.LiveState state = ConfigAccess.readLiveState();
        // 从重启弹窗「取消」回来时保留内存里的勾选与原始快照；
        // 正常打开（或从主菜单进来）则一律以磁盘为准。
        if (this.preserveState) {
            this.preserveState = false;
        } else {
            keptFamilies.clear();
            keptFamilies.addAll(state.keepFamilies);
            keptVehicleIds.clear();
            keptVehicleIds.addAll(state.keepIds);
            // 记下打开时的状态：保存时用它与当前勾选比对，
            // 完全一致说明用户没改任何东西 —— 那就没必要写盘、更没必要弹「是否重启」。
            baselineFamilies = new LinkedHashSet<>(keptFamilies);
            baselineVehicleIds = new LinkedHashSet<>(keptVehicleIds);
        }

        final int top = 60;
        final int bottom = this.height - 52;
        this.list = new TrainListWidget(this.minecraft, this.width, this.height, top, bottom, 24);
        this.addRenderableWidget(this.list);

        // 顶部工具栏
        final int by = 24;
        final int bw = 110;
        final int gap = 6;
        final int totalW = bw * 3 + gap * 2;
        int x = (this.width - totalW) / 2;
        this.addRenderableWidget(Button.builder(tr("select_all"), b -> {
            keptFamilies.clear();
            keptFamilies.addAll(TrainFamilyCatalog.allFamilyIds());
            keptVehicleIds.clear();
            list.refreshRows();
        }).bounds(x, by, bw, 20).build());
        x += bw + gap;
        this.addRenderableWidget(Button.builder(tr("select_none"), b -> {
            keptFamilies.clear();
            keptVehicleIds.clear();
            list.refreshRows();
        }).bounds(x, by, bw, 20).build());
        x += bw + gap;
        this.addRenderableWidget(Button.builder(tr("reset"), b -> {
            keptFamilies.clear();
            keptVehicleIds.clear();
            list.refreshRows();
        }).bounds(x, by, bw, 20).build());

        // 底部：取消（左）+ 完成（右）
        this.addRenderableWidget(Button.builder(tr("cancel"), b -> this.cancel())
                .bounds(this.width / 2 - 154, this.height - 40, 150, 20).build());
        this.addRenderableWidget(Button.builder(tr("done"), b -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 40, 150, 20).build());

        // 打开时自动滚到第一个已保留的车系，让用户一眼看到自己勾过什么
        if (!keptFamilies.isEmpty()) {
            this.list.scrollToFamily(keptFamilies.iterator().next());
        }
    }

    /**
     * 「取消」/ ESC / 右上角关闭：<b>丢弃本次改动</b>，什么都不写，直接回主界面。
     *
     * <p>与 {@link #onClose()} 的区别：onClose 表示用户按了「完成」，是有意的确认行为；
     * cancel 表示用户放弃，不应该把界面上的勾选当成确认结果写进配置。
     */
    public void cancel() {
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 用户取消选择界面，丢弃本次改动");
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /**
     * ESC 等同于「取消」：丢弃改动回主界面，<b>不能</b>走 {@link #onClose()}。
     *
     * <p>原版 {@code Screen} 里 ESC 默认调用 {@code onClose()}，如果直接继承，
     * 用户按 ESC 想退出时反而会把改动写进配置并弹出重启确认窗 —— 这是错的。
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // GLFW_KEY_ESCAPE == 256
        if (keyCode == 256) {
            this.cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 把当前勾选写进配置。由「完成」路径或重启弹窗的「是/否」触发，取消路径不调用。 */
    private void persistSelection() {
        ConfigAccess.writeSelection(keptFamilies, keptVehicleIds);
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 已保存列车保留选择：{} 个车系 / {} 辆单车",
                keptFamilies.size(), keptVehicleIds.size());
    }

    @Override
    public void onClose() {
        // 用户什么都没改 → 不写盘、不弹重启窗，直接回主界面。
        // 这样「打开看看又关掉」不会白问一次是否重启。
        final boolean changed = !keptFamilies.equals(baselineFamilies)
                || !keptVehicleIds.equals(baselineVehicleIds);
        if (!changed) {
            MtrDisableOriginalTrains.LOGGER.info(
                    "[MTR_NoOriginalTrains] 选择未改动，无需重启，直接返回");
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
            return;
        }

        if (this.minecraft == null) {
            return;
        }
        // 「不再询问」模式下不弹窗：直接落盘并按记住的动作执行
        if (!RestartPromptConfig.isAskEnabled()) {
            persistSelection();
            if (RestartPromptConfig.isAutoRestart()) {
                MtrDisableOriginalTrains.LOGGER.info(
                        "[MTR_NoOriginalTrains] 已记住「立即重启」，正在关闭游戏进程（之后由用户自行启动）");
                this.minecraft.stop();
            } else {
                this.minecraft.setScreen(this.parent);
            }
            return;
        }
        // 弹窗里点「取消」要退回本界面继续改（此时还不落盘），点「否」才回主菜单
        this.minecraft.setScreen(new RestartConfirmScreen(this.parent, this, this::persistSelection));
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        final var font = this.font;

        ctx.drawCenteredString(font, this.title, this.width / 2, 8, 0xFFFFFF);

        final int keptFam = keptFamilies.size();
        final int totalFam = TrainFamilyCatalog.families().size();
        // 摘要画在工具栏按钮（24~44）与列表（top=60）之间的空档 y=48
        ctx.drawCenteredString(font, tr("kept_summary", keptFam, totalFam),
                this.width / 2, 48, 0xA0A0A0);

        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredString(font, tr("restart_hint"), this.width / 2, this.height - 54, 0xE8C060);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // 状态查询 / 修改
    // ------------------------------------------------------------------

    boolean isFamilyFullyKept(TrainFamilyCatalog.Family f) {
        if (keptFamilies.contains(f.id)) {
            return true;
        }
        for (String id : f.vehicles.keySet()) {
            if (!keptVehicleIds.contains(id)) {
                return false;
            }
        }
        // 全部车辆都通过单车勾选保留，也算整族已保留
        return !f.vehicles.isEmpty();
    }

    boolean isFamilyPartiallyKept(TrainFamilyCatalog.Family f) {
        if (keptFamilies.contains(f.id)) {
            return false;
        }
        for (String id : f.vehicles.keySet()) {
            if (keptVehicleIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    boolean isVehicleKept(TrainFamilyCatalog.Family f, String vehicleId) {
        return keptFamilies.contains(f.id) || keptVehicleIds.contains(vehicleId);
    }

    void toggleFamily(TrainFamilyCatalog.Family f) {
        if (isFamilyFullyKept(f)) {
            keptFamilies.remove(f.id);
            for (String id : f.vehicles.keySet()) {
                keptVehicleIds.remove(id);
            }
        } else {
            keptFamilies.add(f.id);
            for (String id : f.vehicles.keySet()) {
                keptVehicleIds.remove(id);
            }
        }
        list.refreshRows();
    }

    void toggleVehicle(TrainFamilyCatalog.Family f, String vehicleId) {
        if (keptFamilies.contains(f.id)) {
            // 整族已选，取消这一辆 → 转为「除这辆外全部单车保留」
            keptFamilies.remove(f.id);
            for (String id : f.vehicles.keySet()) {
                if (!id.equals(vehicleId)) {
                    keptVehicleIds.add(id);
                }
            }
        } else if (!keptVehicleIds.remove(vehicleId)) {
            keptVehicleIds.add(vehicleId);
        }
        list.refreshRows();
    }

    void toggleExpand(TrainFamilyCatalog.Family f) {
        if (!expanded.remove(f.id)) {
            expanded.add(f.id);
        }
        list.refreshRows();
    }

    // ------------------------------------------------------------------
    // 列表控件
    // ------------------------------------------------------------------

    /** 一行：车系行（vehicleId == null）或单辆车行。 */
    private static final class Row {
        final TrainFamilyCatalog.Family family;
        final String vehicleId;
        final String vehicleName;

        Row(TrainFamilyCatalog.Family family, String vehicleId, String vehicleName) {
            this.family = family;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
        }

        boolean isFamilyRow() {
            return vehicleId == null;
        }
    }

    private final class TrainListWidget extends ObjectSelectionList<TrainEntry> {

        TrainListWidget(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
            refreshRows();
        }

        void refreshRows() {
            final List<Row> rows = new ArrayList<>();
            for (TrainFamilyCatalog.Family f : TrainFamilyCatalog.families()) {
                rows.add(new Row(f, null, null));
                if (expanded.contains(f.id)) {
                    for (var e : f.vehicles.entrySet()) {
                        rows.add(new Row(f, e.getKey(), e.getValue()));
                    }
                }
            }
            this.clearEntries();
            for (Row r : rows) {
                this.addEntry(new TrainEntry(r));
            }
        }

        /** 滚动到指定车系那一行（车系行之间每行占 itemHeight）。 */
        void scrollToFamily(String familyId) {
            int index = 0;
            for (TrainFamilyCatalog.Family f : TrainFamilyCatalog.families()) {
                if (f.id.equals(familyId)) {
                    setScrollAmount((double) index * 24);
                    return;
                }
                // 未展开时每个车系占 1 行；展开的占 1 + 车辆数
                index += 1 + (expanded.contains(f.id) ? f.vehicles.size() : 0);
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(520, TrainSelectScreen.this.width - 60);
        }

        @Override
        protected int getScrollbarPosition() {
            return (this.width + getRowWidth()) / 2 + 6;
        }
    }

    private final class TrainEntry extends ObjectSelectionList.Entry<TrainEntry> {
        private final Row row;
        private final Button foldButton;
        /** 勾选框的命中区域（render 时更新）。 */
        private int hitCheckboxX;
        private int hitCheckboxY;

        TrainEntry(Row row) {
            this.row = row;
            final TrainFamilyCatalog.Family f = row.family;

            if (row.isFamilyRow()) {
                this.foldButton = Button.builder(
                        Component.literal(expanded.contains(f.id) ? "−" : "+"),
                        b -> toggleExpand(f)).bounds(0, 0, 16, 18).build();
            } else {
                this.foldButton = null;
            }
        }

        @Override
        public void render(GuiGraphics ctx, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            final var font = TrainSelectScreen.this.font;
            final TrainFamilyCatalog.Family f = row.family;
            final boolean familyRow = row.isFamilyRow();

            if (hovered) {
                ctx.fill(x - 2, y, x + entryWidth, y + entryHeight - 2, 0x30FFFFFF);
            }

            // 勾选框（完全自绘；点击在 mouseClicked 里做命中检测，不再叠按钮）
            final boolean kept = familyRow ? isFamilyFullyKept(f) : isVehicleKept(f, row.vehicleId);
            final boolean partial = familyRow && isFamilyPartiallyKept(f);
            final int bx = x + 2;
            final int by = y + 2;
            ctx.fill(bx, by, bx + 16, by + 16, 0xFF1A1A1A);
            ctx.renderOutline(bx, by, 16, 16, (kept || partial) ? 0xFF6FCF6F : 0xFF808080);
            if (kept) {
                // ✓ 勾：两段线组成对勾
                ctx.renderOutline(bx + 4, by + 7, 3, 3, 0xFF5FBF5F);
                ctx.fill(bx + 6, by + 9, bx + 13, by + 12, 0xFF5FBF5F);
                ctx.fill(bx + 4, by + 6, bx + 7, by + 9, 0xFF5FBF5F);
            } else if (partial) {
                ctx.fill(bx + 3, by + 7, bx + 13, by + 10, 0xFFE8C060);
            }
            hitCheckboxX = bx;
            hitCheckboxY = by;

            int textX = bx + 22;
            if (this.foldButton != null) {
                this.foldButton.setX(textX);
                this.foldButton.setY(y + 1);
                this.foldButton.render(ctx, mouseX, mouseY, delta);
                textX += 22;
            } else {
                textX += 10;
            }

            final String name = familyRow ? f.displayName : row.vehicleName;
            final int color = familyRow ? (kept ? 0xFFFFFF : 0xC0C0C0) : (kept ? 0xC0C0C0 : 0x808080);
            ctx.drawString(font,
                    font.plainSubstrByWidth(name, entryWidth - (textX - x) - 70),
                    textX, y + 7, color);

            if (familyRow) {
                final Component right = tr("vehicles", f.size());
                final int rw = font.width(right);
                ctx.drawString(font, right, x + entryWidth - rw - 6, y + 7, 0x808080);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 1) 勾选框命中检测（自绘，无按钮）
            if (button == 0
                    && mouseX >= hitCheckboxX && mouseX < hitCheckboxX + 16
                    && mouseY >= hitCheckboxY && mouseY < hitCheckboxY + 16) {
                final TrainFamilyCatalog.Family f = row.family;
                if (row.isFamilyRow()) {
                    toggleFamily(f);
                } else {
                    toggleVehicle(f, row.vehicleId);
                }
                return true;
            }
            // 2) 展开/折叠按钮
            if (this.foldButton != null && this.foldButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            // 3) 点行内其他区域：车系行 = 切换整族；单车辆行 = 切换该辆
            if (button == 0) {
                final TrainFamilyCatalog.Family f = row.family;
                if (row.isFamilyRow()) {
                    toggleFamily(f);
                } else {
                    toggleVehicle(f, row.vehicleId);
                }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public Component getNarration() {
            return Component.literal(row.isFamilyRow() ? row.family.displayName : row.vehicleName);
        }
    }
}
