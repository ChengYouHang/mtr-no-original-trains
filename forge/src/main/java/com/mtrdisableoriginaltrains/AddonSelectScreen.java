package com.mtrdisableoriginaltrains;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    /** 展开中的「系列」（键 = 包原始名 + "|S|" + 系列键）。 */
    private final Set<String> expandedSeries = new LinkedHashSet<>();

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
     * 由「车型」名推出所属「系列」（线路 / 车系大类），用于列表的第二层归并。
     *
     * <p>{@link #familyKeyOf} 得到的是<b>车型</b>（如「上海地铁1号线-01A02伪八二世」），
     * 一个车型在追加包里通常只有 4 辆车；但玩家真正想一键拦掉的是<b>一条线</b>。
     * 实测两种命名习惯：
     * <ol>
     *   <li><b>「线路-车型」</b>（上海地铁包、多数日系包）：第一个 {@code -} 之前就是线路，
     *       如 {@code 上海地铁16号线-16A01(AC19)抹茶-大站车} → {@code 上海地铁16号线}；</li>
     *   <li><b>「线路 + 批次」</b>（合肥这类国产包）：线路后面直接跟期数/批次，
     *       如 {@code 合肥轨道交通2号线增购车} → {@code 合肥轨道交通2号线}，
     *       {@code 合肥轨道S1号线xcan+314} → {@code 合肥轨道S1号线}。</li>
     * </ol>
     * 两种都匹配不上时，车型自己就是一个系列（列表里少一层，不会凭空合并）。
     */
    static String seriesKeyOf(String familyKey) {
        if (familyKey == null || familyKey.isEmpty()) {
            return familyKey;
        }
        // ① 「线路-车型」写法：第一个 - 之前
        final int dash = familyKey.indexOf('-');
        if (dash > 0) {
            final String head = familyKey.substring(0, dash).trim();
            if (head.length() >= 2) {
                return head;
            }
        }
        // ② 「线路 + 批次」写法：反复剥结尾的批次词
        String base = familyKey;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String token : SERIES_SUFFIX_TOKENS) {
                final String next = base.replaceAll("(?i)\\s*" + token + "\\s*$", "").trim();
                if (!next.isEmpty() && !next.equals(base)) {
                    base = next;
                    changed = true;
                }
            }
        }
        return base;
    }

    /** 把「系列前缀 + 分隔符」从车型名里去掉，缩进显示时不再重复写线路名。 */
    static String stripSeriesPrefix(String familyKey) {
        final String series = seriesKeyOf(familyKey);
        if (series == null || series.equals(familyKey) || !familyKey.startsWith(series)) {
            return familyKey;
        }
        String rest = familyKey.substring(series.length()).trim();
        rest = rest.replaceAll("^[\\s\\-\u2014\u00b7\u3001]+", "").trim();
        return rest.isEmpty() ? familyKey : rest;
    }

    /** {@link #seriesKeyOf} 第 ② 条要剥掉的批次词。 */
    private static final String[] SERIES_SUFFIX_TOKENS = {
            "第?\\d+期", "\\d+期", "增购车", "增购", "神秘列车", "彩蛋车", "扩编",
            "xcan\\+?\\d*", "xcan", "lcd"
    };

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

    /** 一行：包头 / 系列行 / 车型行 / 单车行。 */
    private static final class Row {
        static final int KIND_PACK = 0;
        static final int KIND_SERIES = 1;
        static final int KIND_FAMILY = 2;
        static final int KIND_VEHICLE = 3;
        static final int KIND_EMPTY = 4;

        final int kind;
        final String rawPack;
        final String packDisplay;
        /** 系列键或车型键（KIND_SERIES / KIND_FAMILY 用）。 */
        final String familyKey;
        final String familyDisplay;
        /** 该行覆盖的全部列车 ID（整行勾选时用）。 */
        final List<String> familyIds;
        final String vehicleId;
        final String vehicleName;
        /** 缩进层级：0 = 顶层，1 = 车型在系列下，2 = 单车在车型下。 */
        final int indent;

        private Row(int kind, String rawPack, String packDisplay, String familyKey, String familyDisplay,
                    List<String> familyIds, String vehicleId, String vehicleName, int indent) {
            this.kind = kind;
            this.rawPack = rawPack;
            this.packDisplay = packDisplay;
            this.familyKey = familyKey;
            this.familyDisplay = familyDisplay;
            this.familyIds = familyIds;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.indent = indent;
        }

        static Row pack(String rawPack, String packDisplay) {
            return new Row(KIND_PACK, rawPack, packDisplay, null, null, null, null, null, 0);
        }

        static Row series(String rawPack, String packDisplay, String seriesKey, List<String> ids) {
            return new Row(KIND_SERIES, rawPack, packDisplay, seriesKey, seriesKey, ids, null, null, 0);
        }

        static Row family(String rawPack, String packDisplay, String familyKey, String familyDisplay,
                          List<String> ids, int indent) {
            return new Row(KIND_FAMILY, rawPack, packDisplay, familyKey, familyDisplay, ids, null, null, indent);
        }

        static Row vehicle(String rawPack, String packDisplay, String vehicleId, String vehicleName, int indent) {
            return new Row(KIND_VEHICLE, rawPack, packDisplay, null, null, null, vehicleId, vehicleName, indent);
        }

        static Row empty() {
            return new Row(KIND_EMPTY, null, null, null, null, null, null, null, 0);
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
                    rows.add(Row.empty());
                } else {
                    for (var e : packs.entrySet()) {
                        rows.add(Row.pack(e.getKey(), AddonPackScanner.displayPackName(e.getKey())));
                    }
                }
            } else {
                final Set<String> ids = packs.get(selectedRaw);
                if (ids != null) {
                    // 第一层：车型（组内保持原始声明顺序）
                    final Map<String, List<String>> byFamily = new LinkedHashMap<>();
                    for (String id : ids) {
                        final String key = familyKeyOf(id, labelOf(id, names));
                        byFamily.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
                    }
                    // 第二层：车型再归并成系列（线路）
                    final Map<String, List<String>> seriesToFamilies = new LinkedHashMap<>();
                    for (String familyKey : byFamily.keySet()) {
                        seriesToFamilies.computeIfAbsent(seriesKeyOf(familyKey), k -> new ArrayList<>())
                                .add(familyKey);
                    }
                    for (var se : seriesToFamilies.entrySet()) {
                        final String seriesKey = se.getKey();
                        final List<String> families = se.getValue();
                        if (families.size() == 1) {
                            // 这条线下只有一个车型，省掉中间层，直接显示车型行
                            addFamilyRows(rows, byFamily, names, families.get(0), 0);
                            continue;
                        }
                        final List<String> seriesIds = new ArrayList<>();
                        for (String familyKey : families) {
                            seriesIds.addAll(byFamily.get(familyKey));
                        }
                        rows.add(Row.series(selectedRaw, selectedDisplay, seriesKey, seriesIds));
                        if (expandedSeries.contains(selectedRaw + "|S|" + seriesKey)) {
                            for (String familyKey : families) {
                                addFamilyRows(rows, byFamily, names, familyKey, 1);
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

        /** 追加一个车型行（必要时连带它展开的单车行）。 */
        private void addFamilyRows(List<Row> rows, Map<String, List<String>> byFamily,
                                   Map<String, String> names, String familyKey, int indent) {
            final List<String> familyIds = byFamily.get(familyKey);
            if (familyIds == null || familyIds.isEmpty()) {
                return;
            }
            // 嵌在系列下面时省掉重复的线路前缀：「上海地铁1号线-01A02伪八二世」 → 「01A02伪八二世」
            rows.add(Row.family(selectedRaw, selectedDisplay, familyKey,
                    indent > 0 ? stripSeriesPrefix(familyKey) : familyKey, familyIds, indent));
            if (!expandedFamilies.contains(selectedRaw + "|F|" + familyKey)) {
                return;
            }
            // legacy 包的 4 个变体（trailer / cab_1..3）共用同一个名字，
            // 展开时补上变体后缀，否则司机看到 4 行一模一样的字
            final Map<String, Integer> nameCount = new HashMap<>();
            for (String id : familyIds) {
                nameCount.merge(labelOf(id, names), 1, Integer::sum);
            }
            for (String id : familyIds) {
                final String base = labelOf(id, names);
                final String label = nameCount.getOrDefault(base, 0) > 1
                        ? base + "  ·  " + tailOf(id) : base;
                rows.add(Row.vehicle(selectedRaw, selectedDisplay, id, label, indent + 1));
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
        private final Button foldButton;
        /** 勾选框命中区域（render 时更新）。 */
        private int hitX;
        private int hitY;

        AddonEntry(Row row) {
            this.row = row;
            if (row.kind == Row.KIND_FAMILY) {
                final String key = row.rawPack + "|F|" + row.familyKey;
                this.foldButton = Button.builder(
                        Component.literal(expandedFamilies.contains(key) ? "−" : "+"),
                        b -> {
                            if (!expandedFamilies.remove(key)) {
                                expandedFamilies.add(key);
                            }
                            AddonSelectScreen.this.list.refreshRows();
                        }).bounds(0, 0, 16, 18).build();
            } else if (row.kind == Row.KIND_SERIES) {
                final String key = row.rawPack + "|S|" + row.familyKey;
                this.foldButton = Button.builder(
                        Component.literal(expandedSeries.contains(key) ? "−" : "+"),
                        b -> {
                            if (!expandedSeries.remove(key)) {
                                expandedSeries.add(key);
                            }
                            AddonSelectScreen.this.list.refreshRows();
                        }).bounds(0, 0, 16, 18).build();
            } else {
                this.foldButton = null;
            }
        }

        @Override
        public void render(GuiGraphics ctx, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            final var font = AddonSelectScreen.this.font;

            // 空提示行（没有检测到任何追加包）
            if (row.kind == Row.KIND_EMPTY) {
                ctx.drawCenteredString(font, tr("addon_empty"),
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
            } else if (row.kind == Row.KIND_FAMILY || row.kind == Row.KIND_SERIES) {
                checked = isFamilyFullyBlocked(row.familyIds);
                partial = isFamilyPartiallyBlocked(row.familyIds);
            } else {
                checked = blockedAddons.contains(row.vehicleId);
                partial = false;
            }

            // 缩进：系列 > 车型 > 单车
            final int rowX = x + row.indent * 10;
            int textX;
            if (packRow) {
                // 包行：无勾选框，直接文字 + 右侧计数
                textX = rowX + 8;
            } else {
                final int bx = rowX + 2;
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

            // 系列行 / 车型行多一个展开按钮
            if (this.foldButton != null) {
                this.foldButton.setX(textX);
                this.foldButton.setY(y + 1);
                this.foldButton.render(ctx, mouseX, mouseY, delta);
                textX += 22;
            }

            final String name;
            if (packRow) {
                name = row.packDisplay;
            } else if (row.kind == Row.KIND_FAMILY || row.kind == Row.KIND_SERIES) {
                name = row.familyDisplay;
            } else {
                name = row.vehicleName;
            }
            final boolean groupRow = packRow || row.kind == Row.KIND_FAMILY || row.kind == Row.KIND_SERIES;
            final int color = row.kind == Row.KIND_SERIES ? (checked ? 0xFFFFFF : 0xD8D8D8)
                    : groupRow ? (checked ? 0xFFFFFF : 0xC0C0C0)
                    : (checked ? 0xE0C0B0 : 0xA0A0A0);
            ctx.drawString(font,
                    font.plainSubstrByWidth(name, entryWidth - (textX - x) - 70),
                    textX, y + 7, color);

            if (packRow) {
                final Set<String> ids = AddonPackScanner.addonPacks().get(row.rawPack);
                final Component right = tr("vehicles", ids == null ? 0 : ids.size());
                final int rw = font.width(right);
                ctx.drawString(font, right, x + entryWidth - rw - 6, y + 7, 0x808080);
            } else if (row.kind == Row.KIND_FAMILY || row.kind == Row.KIND_SERIES) {
                final Component right = tr("vehicles", row.familyIds.size());
                final int rw = font.width(right);
                ctx.drawString(font, right, x + entryWidth - rw - 6, y + 7, 0x808080);
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
            if (row.kind == Row.KIND_FAMILY || row.kind == Row.KIND_SERIES) {
                // 系列行 / 车型行：整行切换（系列 = 它下面所有车型的所有车）
                if (isFamilyFullyBlocked(row.familyIds)) {
                    AddonSelectScreen.this.blockedAddons.removeAll(row.familyIds);
                } else {
                    AddonSelectScreen.this.blockedAddons.addAll(row.familyIds);
                }
                AddonSelectScreen.this.list.refreshRows();
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
            return Component.literal(row.kind == Row.KIND_PACK ? row.packDisplay
                    : (row.kind == Row.KIND_FAMILY || row.kind == Row.KIND_SERIES)
                    ? row.familyDisplay : row.vehicleName);
        }
    }

    static Component tr(String key, Object... args) {
        return Component.translatable(LANG_PREFIX + key, args);
    }
}
