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
import java.util.Map;
import java.util.Set;

/**
 * 追加包列车拦截页（1.3.0 新增）。
 *
 * <h2>交互设计</h2>
 * <ul>
 *   <li><b>包列表页</b>：每个检测到的追加包一行（名称 + 列车数），点击进入明细；
 *       底部「取消」（不保存回入口页）/「完成」（保存回入口页）。</li>
 *   <li><b>包明细页</b>：该包声明的每辆列车一行，勾选 = 拦截；
 *       顶部「全选 / 全不选」只对当前包生效；底部「返回」（回列表，改动暂存）/
 *       「完成」（保存并触发重启确认）。</li>
 * </ul>
 *
 * <h2>数据来源（动态）</h2>
 * 列表来自 {@link AddonPackScanner#addonPacks()} —— 每次启动资源重载时扫描
 * 全部资源包里的 {@code mtr_custom_resources.json}，<b>只有声明了车辆清单的包才会出现</b>
 * （纯材质包、光影包不含该文件，天然不会进列表），每包只统计 transportMode == TRAIN 的车辆。
 * 显示名取自 {@link BuiltinTrainFilter#capturedAddonNames()}（MTR 注册时捕获），取不到退回 ID。
 *
 * <h2>保存语义</h2>
 * 勾选结果写入配置的 {@code blockedAddonIds}；保存时不动 keepIds / keepFamilies。
 * 过滤逻辑在下次启动的资源重载中生效，因此保存后走与内置页相同的「重启确认」弹窗。
 */
public class AddonSelectScreen extends Screen {

    private static final String LANG_PREFIX = "gui.mtr_no_original_trains.";

    private final Screen parent;

    /** 当前勾选拦截的追加包列车 ID。 */
    private final Set<String> blockedAddons = new LinkedHashSet<>();
    /** 打开界面时的快照，用于「没改就不必保存/重启」。 */
    private Set<String> baseline = new LinkedHashSet<>();

    /** 0 = 包列表页；1 = 包明细页。 */
    private int mode = 0;
    /** 明细页当前查看的追加包（资源包原始名）。 */
    private String selectedRaw;
    private String selectedDisplay;

    private AddonListWidget list;

    /**
     * 从重启确认弹窗点「取消」回来时为 true：这次 {@link #init()} 不要从磁盘重载，
     * 保留用户还没保存的勾选与当前所在页（含明细页的包）。
     */
    private boolean preserveState = false;

    /** 由 {@link RestartConfirmScreen#cancel()} 调用，标记「马上要回到本界面，别重载」。 */
    void prepareResume() {
        this.preserveState = true;
    }

    public AddonSelectScreen(Screen parent) {
        super(Component.translatable(LANG_PREFIX + "tab_addon"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.preserveState) {
            this.preserveState = false;
        } else {
            final ConfigAccess.LiveState state = ConfigAccess.readLiveState();
            blockedAddons.clear();
            blockedAddons.addAll(state.blockedAddonIds);
            baseline = new LinkedHashSet<>(blockedAddons);
            selectedRaw = null;
            selectedDisplay = null;
            mode = 0;
        }
        rebuild();
    }

    /** 按当前 mode 重建按钮与列表（init 与页间跳转共用）。 */
    private void rebuild() {
        this.clearWidgets();

        final int top = 60;
        final int bottom = this.height - 52;
        this.list = new AddonListWidget(this.minecraft, this.width, this.height, top, bottom, 24);
        this.addRenderableWidget(this.list);

        if (mode == 1) {
            // 明细页：顶部 全选 / 全不选（只作用于当前包）
            final int by = 24;
            final int bw = 110;
            final int gap = 6;
            final int totalW = bw * 2 + gap;
            int x = (this.width - totalW) / 2;
            this.addRenderableWidget(Button.builder(tr("select_all"), b -> {
                final Set<String> ids = AddonPackScanner.addonPacks().get(selectedRaw);
                if (ids != null) {
                    blockedAddons.addAll(ids);
                }
                list.refreshRows();
            }).bounds(x, by, bw, 20).build());
            x += bw + gap;
            this.addRenderableWidget(Button.builder(tr("select_none"), b -> {
                final Set<String> ids = AddonPackScanner.addonPacks().get(selectedRaw);
                if (ids != null) {
                    blockedAddons.removeAll(ids);
                }
                list.refreshRows();
            }).bounds(x, by, bw, 20).build());
        }

        // 底部：左 返回/取消，右 完成
        this.addRenderableWidget(Button.builder(tr(mode == 1 ? "back" : "cancel"),
                        b -> this.onBack())
                .bounds(this.width / 2 - 154, this.height - 40, 150, 20).build());
        this.addRenderableWidget(Button.builder(tr("done"), b -> this.onDone())
                .bounds(this.width / 2 + 4, this.height - 40, 150, 20).build());

        this.list.refreshRows();
    }

    private void onBack() {
        if (mode == 1) {
            // 明细页返回列表：改动暂存在 blockedAddons，不落盘
            mode = 0;
            selectedRaw = null;
            selectedDisplay = null;
            rebuild();
            return;
        }
        // 列表页取消：丢弃本次改动
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void onDone() {
        final boolean changed = !blockedAddons.equals(baseline);
        if (!changed) {
            // 没改：不写盘不弹窗，直接回入口页
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
            return;
        }
        if (this.minecraft == null) {
            return;
        }
        if (!RestartPromptConfig.isAskEnabled()) {
            persistSelection();
            if (RestartPromptConfig.isAutoRestart()) {
                this.minecraft.stop();
            } else {
                this.minecraft.setScreen(this.parent);
            }
            return;
        }
        this.minecraft.setScreen(new RestartConfirmScreen(this.parent, this, this::persistSelection));
    }

    /** 只写追加包拦截键；keepIds / keepFamilies 保持磁盘上的值不动。 */
    private void persistSelection() {
        final ConfigAccess.LiveState current = ConfigAccess.readLiveState();
        ConfigAccess.writeSelection(current.keepFamilies, current.keepIds, blockedAddons);
    }

    void enterPack(String raw, String display) {
        this.mode = 1;
        this.selectedRaw = raw;
        this.selectedDisplay = display;
        this.rebuild();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // GLFW_KEY_ESCAPE == 256：明细页回列表，列表页回入口页（等同取消）
        if (keyCode == 256) {
            this.onBack();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        final var font = this.font;

        final Component title = mode == 1
                ? Component.literal(AddonPackScanner.displayPackName(selectedDisplay))
                : this.title;
        ctx.drawCenteredString(font, title, this.width / 2, 8, 0xFFFFFF);

        // 摘要（y=48，工具栏与列表之间）
        if (mode == 1) {
            final Set<String> ids = AddonPackScanner.addonPacks().get(selectedRaw);
            final int total = ids == null ? 0 : ids.size();
            int blockedInPack = 0;
            if (ids != null) {
                for (String id : ids) {
                    if (blockedAddons.contains(id)) {
                        blockedInPack++;
                    }
                }
            }
            ctx.drawCenteredString(font, tr("addon_pack_summary", blockedInPack, total),
                    this.width / 2, 48, 0xA0A0A0);
        } else {
            ctx.drawCenteredString(font, tr("addon_list_summary",
                            AddonPackScanner.addonPacks().size(), AddonPackScanner.addonTrainTotal()),
                    this.width / 2, 48, 0xA0A0A0);
        }

        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredString(font, tr("restart_hint"), this.width / 2, this.height - 54, 0xE8C060);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // 列表控件
    // ------------------------------------------------------------------

    /** 一行：包头（vehicleId == null）或单辆追加包列车；rawPack == null 且 vehicleId == null 是空提示行。 */
    private static final class Row {
        final String rawPack;
        final String packDisplay;
        final String vehicleId;
        final String vehicleName;

        Row(String rawPack, String packDisplay, String vehicleId, String vehicleName) {
            this.rawPack = rawPack;
            this.packDisplay = packDisplay;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
        }

        boolean isPackRow() {
            return vehicleId == null;
        }
    }

    private final class AddonListWidget extends ObjectSelectionList<AddonEntry> {

        AddonListWidget(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        void refreshRows() {
            final List<Row> rows = new ArrayList<>();
            final Map<String, Set<String>> packs = AddonPackScanner.addonPacks();
            final Map<String, String> names = BuiltinTrainFilter.capturedAddonNames();
            if (mode == 0) {
                if (packs.isEmpty()) {
                    rows.add(new Row(null, null, null, null)); // 空提示行
                } else {
                    for (var e : packs.entrySet()) {
                        rows.add(new Row(e.getKey(), AddonPackScanner.displayPackName(e.getKey()), null, null));
                    }
                }
            } else {
                final Set<String> ids = packs.get(selectedRaw);
                if (ids != null) {
                    for (String id : ids) {
                        rows.add(new Row(selectedRaw, selectedDisplay, id, names.getOrDefault(id, id)));
                    }
                }
            }
            this.clearEntries();
            for (Row r : rows) {
                this.addEntry(new AddonEntry(r));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(520, AddonSelectScreen.this.width - 60);
        }

        @Override
        protected int getScrollbarPosition() {
            return (this.width + getRowWidth()) / 2 + 6;
        }
    }

    private final class AddonEntry extends ObjectSelectionList.Entry<AddonEntry> {
        private final Row row;
        /** 勾选框命中区域（render 时更新）。 */
        private int hitX;
        private int hitY;

        AddonEntry(Row row) {
            this.row = row;
        }

        @Override
        public void render(GuiGraphics ctx, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            final var font = AddonSelectScreen.this.font;

            // 空提示行（没有检测到任何追加包）
            if (row.rawPack == null && row.vehicleId == null) {
                ctx.drawCenteredString(font, tr("addon_empty"),
                        AddonSelectScreen.this.width / 2, y + 7, 0x808080);
                return;
            }

            if (hovered) {
                ctx.fill(x - 2, y, x + entryWidth, y + entryHeight - 2, 0x30FFFFFF);
            }

            final boolean packRow = row.isPackRow();
            final boolean checked = packRow
                    ? isPackFullyBlocked(row.rawPack)
                    : blockedAddons.contains(row.vehicleId);
            final boolean partial = packRow && isPackPartiallyBlocked(row.rawPack);

            int textX;
            if (packRow) {
                // 包行：无勾选框，直接文字 + 右侧计数
                textX = x + 8;
            } else {
                final int bx = x + 2;
                final int by = y + 2;
                ctx.fill(bx, by, bx + 16, by + 16, 0xFF1A1A1A);
                ctx.renderOutline(bx, by, 16, 16,
                        (checked || partial) ? 0xFFE08060 : 0xFF808080);
                if (checked) {
                    // 拦截用暖色（红橙系）：勾 = 该车将从游戏里移除
                    ctx.renderOutline(bx + 4, by + 7, 3, 3, 0xFFE08060);
                    ctx.fill(bx + 6, by + 9, bx + 13, by + 12, 0xFFE08060);
                    ctx.fill(bx + 4, by + 6, bx + 7, by + 9, 0xFFE08060);
                } else if (partial) {
                    ctx.fill(bx + 3, by + 7, bx + 13, by + 10, 0xFFE8C060);
                }
                hitX = bx;
                hitY = by;
                textX = bx + 22;
            }

            final String name = packRow ? row.packDisplay : row.vehicleName;
            final int color = packRow ? 0xFFFFFF : (checked ? 0xE0C0B0 : 0xA0A0A0);
            ctx.drawString(font,
                    font.plainSubstrByWidth(name, entryWidth - (textX - x) - 70),
                    textX, y + 7, color);

            if (packRow) {
                final Set<String> ids = AddonPackScanner.addonPacks().get(row.rawPack);
                final Component right = tr("vehicles", ids == null ? 0 : ids.size());
                final int rw = font.width(right);
                ctx.drawString(font, right, x + entryWidth - rw - 6, y + 7, 0x808080);
            }
        }

        private boolean isPackFullyBlocked(String raw) {
            final Set<String> ids = AddonPackScanner.addonPacks().get(raw);
            if (ids == null || ids.isEmpty()) {
                return false;
            }
            for (String id : ids) {
                if (!blockedAddons.contains(id)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isPackPartiallyBlocked(String raw) {
            final Set<String> ids = AddonPackScanner.addonPacks().get(raw);
            if (ids == null) {
                return false;
            }
            for (String id : ids) {
                if (blockedAddons.contains(id)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (row.rawPack == null && row.vehicleId == null) {
                return false;
            }
            if (button != 0) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (row.isPackRow()) {
                // 包行任意点击 → 进入明细
                enterPack(row.rawPack, row.packDisplay);
                return true;
            }
            // 车辆行：整行任意位置点击都算切换（明细页就一个操作，不必分区）
            // 注意：ObjectSelectionList.Entry 自带名为 list 的字段（指向宿主列表），
            // 必须用 AddonSelectScreen.this.list 限定，否则会命中继承字段。
            if (!AddonSelectScreen.this.blockedAddons.remove(row.vehicleId)) {
                AddonSelectScreen.this.blockedAddons.add(row.vehicleId);
            }
            AddonSelectScreen.this.list.refreshRows();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(row.isPackRow() ? row.packDisplay : row.vehicleName);
        }
    }

    static Component tr(String key, Object... args) {
        return Component.translatable(LANG_PREFIX + key, args);
    }
}
