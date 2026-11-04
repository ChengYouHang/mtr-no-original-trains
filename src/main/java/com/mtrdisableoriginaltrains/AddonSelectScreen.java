package com.mtrdisableoriginaltrains;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** 展开中的车系（键 = 包原始名 + "|" + 车系键），明细页用。 */
    private final Set<String> expandedFamilies = new LinkedHashSet<>();

    /**
     * 推导追加包车辆所属的「车系」。
     *
     * <p>追加包通常<b>不提供</b> {@code family:} 标签（实测上海地铁 legacy 包与合肥现代包
     * 的 tags 都是空的），因此只能按规则推：
     * <ol>
     *   <li><b>按显示名</b>：去掉括号里的方向/变体说明，如
     *       {@code 合肥轨道交通1号线1期(Forwards)} 与 {@code …(Backwards)} 归一为同一车系；</li>
     *   <li><b>按 ID 兜底</b>：剥掉 {@code mtr_custom_train_} 前缀与
     *       {@code _cab_N} / {@code _trailer} / {@code _fN} / {@code _bN} 变体后缀，
     *       如 {@code mtr_custom_train_ac01_cab_1} → {@code ac01}。</li>
     * </ol>
     * 名称里看不出共性时，每辆车自成一组 —— 退化成平铺列表，不影响拦截功能。
     */
    static String familyKeyOf(String id, String name) {
        if (name != null) {
            String base = name.trim();
            // ① 去掉结尾的括号说明（可能多层，如「…(Trailer受电弓)」「…(Forwards)」）
            String prev = null;
            while (prev == null || !prev.equals(base)) {
                prev = base;
                base = base.replaceAll("\\s*[（(][^（()）]*[)）]\\s*$", "").trim();
            }
            // ② 去掉结尾的显示变体标记（lcd 车厢等）
            base = base.replaceAll("(?i)\\s*[_\\-\\s]?lcd$", "").trim();
            // ③ 去掉开头的括号说明（如「(彩蛋车)合肥…」）
            prev = null;
            while (prev == null || !prev.equals(base)) {
                prev = base;
                base = base.replaceAll("^\\s*[（(][^（()）]*[)）]\\s*", "").trim();
            }
            if (!base.isEmpty() && !base.equals(id)) {
                return base;
            }
        }
        String key = id == null ? "" : id;
        if (key.startsWith("mtr_custom_train_")) {
            key = key.substring("mtr_custom_train_".length());
        }
        key = key.replaceAll("(?i)_(cab_\\d+|trailer|f\\d*|b\\d*|t\\d*t?)$", "");
        return key.isEmpty() ? String.valueOf(id) : key;
    }

    /**
     * 取一辆车的显示名：运行时捕获的名字 → 追加包 JSON 自带的名字 → 退回 ID。
     *
     * <p>中间那一层很关键：车辆被拦截后就不会注册，运行时名称捕不到，
     * 而 JSON 里的名字是扫描阶段读到的，<b>永远在</b>。
     */
    static String labelOf(String id, Map<String, String> captured) {
        String name = captured == null ? null : captured.get(id);
        if (name == null || name.isEmpty()) {
            name = AddonPackScanner.displayTrainName(id);
        }
        if (name == null || name.isEmpty()) {
            name = id;
        }
        return name;
    }

    /** ID 的最后一个 {@code _xxx} 段（用于区分 legacy 包里同名的不带/带弓/头车/尾车）。 */
    static String tailOf(String id) {
        if (id == null) {
            return "?";
        }
        final int us = id.lastIndexOf('_');
        return us >= 0 && us < id.length() - 1 ? id.substring(us + 1) : id;
    }

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
        super(Text.translatable(LANG_PREFIX + "tab_addon"));
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
        this.clearChildren();

        final int top = 60;
        final int bottom = this.height - 52;
        this.list = new AddonListWidget(this.client, this.width, this.height, top, bottom, 24);
        this.addDrawableChild(this.list);

        if (mode == 1) {
            // 明细页：顶部 全选 / 全不选（只作用于当前包）
            final int by = 24;
            final int bw = 110;
            final int gap = 6;
            final int totalW = bw * 2 + gap;
            int x = (this.width - totalW) / 2;
            this.addDrawableChild(ButtonWidget.builder(tr("select_all"), b -> {
                final Set<String> ids = AddonPackScanner.addonPacks().get(selectedRaw);
                if (ids != null) {
                    blockedAddons.addAll(ids);
                }
                list.refreshRows();
            }).dimensions(x, by, bw, 20).build());
            x += bw + gap;
            this.addDrawableChild(ButtonWidget.builder(tr("select_none"), b -> {
                final Set<String> ids = AddonPackScanner.addonPacks().get(selectedRaw);
                if (ids != null) {
                    blockedAddons.removeAll(ids);
                }
                list.refreshRows();
            }).dimensions(x, by, bw, 20).build());
        }

        // 底部：左 返回/取消，右 完成
        this.addDrawableChild(ButtonWidget.builder(tr(mode == 1 ? "back" : "cancel"),
                        b -> this.onBack())
                .dimensions(this.width / 2 - 154, this.height - 40, 150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(tr("done"), b -> this.onDone())
                .dimensions(this.width / 2 + 4, this.height - 40, 150, 20).build());

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
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private void onDone() {
        final boolean changed = !blockedAddons.equals(baseline);
        if (!changed) {
            // 没改：不写盘不弹窗，直接回入口页
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
            return;
        }
        if (this.client == null) {
            return;
        }
        if (!RestartPromptConfig.isAskEnabled()) {
            persistSelection();
            if (RestartPromptConfig.isAutoRestart()) {
                this.client.scheduleStop();
            } else {
                this.client.setScreen(this.parent);
            }
            return;
        }
        this.client.setScreen(new RestartConfirmScreen(this.parent, this, this::persistSelection));
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
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        final TextRenderer font = this.textRenderer;

        final Text title = mode == 1
                ? Text.literal(AddonPackScanner.displayPackName(selectedDisplay))
                : this.title;
        ctx.drawCenteredTextWithShadow(font, title, this.width / 2, 8, 0xFFFFFF);

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
            ctx.drawCenteredTextWithShadow(font, tr("addon_pack_summary", blockedInPack, total),
                    this.width / 2, 48, 0xA0A0A0);
        } else {
            ctx.drawCenteredTextWithShadow(font, tr("addon_list_summary",
                            AddonPackScanner.addonPacks().size(), AddonPackScanner.addonTrainTotal()),
                    this.width / 2, 48, 0xA0A0A0);
        }

        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(font, tr("restart_hint"), this.width / 2, this.height - 54, 0xE8C060);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    // 列表控件
    // ------------------------------------------------------------------

    /** 一行：包头（vehicleId == null）或单辆追加包列车。 */
    private static final class Row {
        static final int KIND_PACK = 0;
        static final int KIND_FAMILY = 1;
        static final int KIND_VEHICLE = 2;
        static final int KIND_EMPTY = 3;

        final int kind;
        final String rawPack;
        final String packDisplay;
        /** 车系键（KIND_FAMILY 用）。 */
        final String familyKey;
        final String familyDisplay;
        /** 该车系下的全部列车 ID（KIND_FAMILY 用，便于整族勾选）。 */
        final List<String> familyIds;
        final String vehicleId;
        final String vehicleName;

        private Row(int kind, String rawPack, String packDisplay, String familyKey, String familyDisplay,
                    List<String> familyIds, String vehicleId, String vehicleName) {
            this.kind = kind;
            this.rawPack = rawPack;
            this.packDisplay = packDisplay;
            this.familyKey = familyKey;
            this.familyDisplay = familyDisplay;
            this.familyIds = familyIds;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
        }

        static Row pack(String rawPack, String packDisplay) {
            return new Row(KIND_PACK, rawPack, packDisplay, null, null, null, null, null);
        }

        static Row family(String rawPack, String packDisplay, String familyKey, String familyDisplay,
                          List<String> ids) {
            return new Row(KIND_FAMILY, rawPack, packDisplay, familyKey, familyDisplay, ids, null, null);
        }

        static Row vehicle(String rawPack, String packDisplay, String vehicleId, String vehicleName) {
            return new Row(KIND_VEHICLE, rawPack, packDisplay, null, null, null, vehicleId, vehicleName);
        }

        static Row empty() {
            return new Row(KIND_EMPTY, null, null, null, null, null, null, null);
        }
    }

    private final class AddonListWidget extends ElementListWidget<AddonEntry> {

        AddonListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        void refreshRows() {
            final List<Row> rows = new ArrayList<>();
            final Map<String, Set<String>> packs = AddonPackScanner.addonPacks();
            final Map<String, String> names = BuiltinTrainFilter.capturedAddonNames();
            if (mode == 0) {
                if (packs.isEmpty()) {
                    rows.add(Row.empty());
                } else {
                    for (var e : packs.entrySet()) {
                        rows.add(Row.pack(e.getKey(), AddonPackScanner.displayPackName(e.getKey())));
                    }
                }
            } else {
                final Set<String> ids = packs.get(selectedRaw);
                if (ids != null) {
                    // 按推导出的车系分组；组内保持原始声明顺序
                    final Map<String, List<String>> byFamily = new LinkedHashMap<>();
                    for (String id : ids) {
                        final String key = familyKeyOf(id, labelOf(id, names));
                        byFamily.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
                    }
                    for (var e : byFamily.entrySet()) {
                        final String key = e.getKey();
                        rows.add(Row.family(selectedRaw, selectedDisplay, key, key, e.getValue()));
                        if (expandedFamilies.contains(selectedRaw + "|" + key)) {
                            // legacy 包的 4 个变体（trailer / cab_1..3）共用同一个名字，
                            // 展开时补上变体后缀，否则司机看到 4 行一模一样的字
                            final Map<String, Integer> nameCount = new HashMap<>();
                            for (String id : e.getValue()) {
                                nameCount.merge(labelOf(id, names), 1, Integer::sum);
                            }
                            for (String id : e.getValue()) {
                                final String base = labelOf(id, names);
                                final String label = nameCount.getOrDefault(base, 0) > 1
                                        ? base + "  ·  " + tailOf(id) : base;
                                rows.add(Row.vehicle(selectedRaw, selectedDisplay, id, label));
                            }
                        }
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
        protected int getScrollbarPositionX() {
            return (this.width + getRowWidth()) / 2 + 6;
        }
    }

    private final class AddonEntry extends ElementListWidget.Entry<AddonEntry> {
        private final Row row;
        private final ButtonWidget foldButton;
        /** 勾选框命中区域（render 时更新）。 */
        private int hitX;
        private int hitY;

        AddonEntry(Row row) {
            this.row = row;
            if (row.kind == Row.KIND_FAMILY) {
                final String key = row.rawPack + "|" + row.familyKey;
                this.foldButton = ButtonWidget.builder(
                        Text.literal(expandedFamilies.contains(key) ? "−" : "+"),
                        b -> {
                            if (!expandedFamilies.remove(key)) {
                                expandedFamilies.add(key);
                            }
                            list.refreshRows();
                        }).dimensions(0, 0, 16, 18).build();
            } else {
                this.foldButton = null;
            }
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            final TextRenderer font = AddonSelectScreen.this.textRenderer;

            // 空提示行（没有检测到任何追加包）
            if (row.kind == Row.KIND_EMPTY) {
                ctx.drawCenteredTextWithShadow(font, tr("addon_empty"),
                        AddonSelectScreen.this.width / 2, y + 7, 0x808080);
                return;
            }

            if (hovered) {
                ctx.fill(x - 2, y, x + entryWidth, y + entryHeight - 2, 0x30FFFFFF);
            }

            final boolean packRow = row.kind == Row.KIND_PACK;
            final boolean checked;
            final boolean partial;
            if (packRow) {
                checked = isPackFullyBlocked(row.rawPack);
                partial = isPackPartiallyBlocked(row.rawPack);
            } else if (row.kind == Row.KIND_FAMILY) {
                checked = isFamilyFullyBlocked(row.familyIds);
                partial = isFamilyPartiallyBlocked(row.familyIds);
            } else {
                checked = blockedAddons.contains(row.vehicleId);
                partial = false;
            }

            int textX;
            if (packRow) {
                // 包行：无勾选框，直接文字 + 右侧计数
                textX = x + 8;
            } else {
                final int bx = x + 2;
                final int by = y + 2;
                ctx.fill(bx, by, bx + 16, by + 16, 0xFF1A1A1A);
                ctx.drawBorder(bx, by, 16, 16,
                        (checked || partial) ? 0xFFE08060 : 0xFF808080);
                if (checked) {
                    // 拦截用暖色（红橙系）：勾 = 该车将从游戏里移除
                    ctx.drawBorder(bx + 4, by + 7, 3, 3, 0xFFE08060);
                    ctx.fill(bx + 6, by + 9, bx + 13, by + 12, 0xFFE08060);
                    ctx.fill(bx + 4, by + 6, bx + 7, by + 9, 0xFFE08060);
                } else if (partial) {
                    ctx.fill(bx + 3, by + 7, bx + 13, by + 10, 0xFFE8C060);
                }
                hitX = bx;
                hitY = by;
                textX = bx + 22;
            }

            // 车系行多一个展开按钮
            if (this.foldButton != null) {
                this.foldButton.setX(textX);
                this.foldButton.setY(y + 1);
                this.foldButton.render(ctx, mouseX, mouseY, delta);
                textX += 22;
            }

            final String name;
            if (packRow) {
                name = row.packDisplay;
            } else if (row.kind == Row.KIND_FAMILY) {
                name = row.familyDisplay;
            } else {
                name = row.vehicleName;
            }
            final boolean groupRow = packRow || row.kind == Row.KIND_FAMILY;
            final int color = groupRow ? (checked ? 0xFFFFFF : 0xC0C0C0)
                    : (checked ? 0xE0C0B0 : 0xA0A0A0);
            ctx.drawTextWithShadow(font,
                    Text.literal(font.trimToWidth(name, entryWidth - (textX - x) - 70)),
                    textX, y + 7, color);

            if (packRow) {
                final Set<String> ids = AddonPackScanner.addonPacks().get(row.rawPack);
                final Text right = tr("vehicles", ids == null ? 0 : ids.size());
                final int rw = font.getWidth(right);
                ctx.drawTextWithShadow(font, right, x + entryWidth - rw - 6, y + 7, 0x808080);
            } else if (row.kind == Row.KIND_FAMILY) {
                final Text right = tr("vehicles", row.familyIds.size());
                final int rw = font.getWidth(right);
                ctx.drawTextWithShadow(font, right, x + entryWidth - rw - 6, y + 7, 0x808080);
            }
        }

        private boolean isFamilyFullyBlocked(List<String> ids) {
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

        private boolean isFamilyPartiallyBlocked(List<String> ids) {
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
            if (row.kind == Row.KIND_EMPTY) {
                return false;
            }
            if (button != 0) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (row.kind == Row.KIND_PACK) {
                // 包行任意点击 → 进入明细
                enterPack(row.rawPack, row.packDisplay);
                return true;
            }
            // 展开/折叠按钮优先
            if (this.foldButton != null && this.foldButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (row.kind == Row.KIND_FAMILY) {
                // 车系行：整族切换
                if (isFamilyFullyBlocked(row.familyIds)) {
                    blockedAddons.removeAll(row.familyIds);
                } else {
                    blockedAddons.addAll(row.familyIds);
                }
                list.refreshRows();
                return true;
            }
            // 车辆行：整行任意位置点击都算切换（明细页就一个操作，不必分区）
            if (!blockedAddons.remove(row.vehicleId)) {
                blockedAddons.add(row.vehicleId);
            }
            list.refreshRows();
            return true;
        }

        @Override
        public List<? extends Element> children() {
            return List.of();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List selectableChildren() {
            // 1.20.1 里该抽象方法签名是原始 List（非泛型），必须原样返回原始类型
            return List.of();
        }
    }

    static Text tr(String key, Object... args) {
        return Text.translatable(LANG_PREFIX + key, args);
    }
}
