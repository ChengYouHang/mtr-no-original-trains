package com.mtrdisableoriginaltrains;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * ——意即<b>全部 378 辆内置列车都被拦截</b>，与模组名称一致。
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

    /** 搜索框（按车系名 / 车辆名 / ID 过滤，1.3.0 新增）。 */
    private TextFieldWidget searchField;
    /** 搜索框是否展开：默认收起，只留右侧一个「搜索」按钮，省地方。 */
    private boolean searchOpen;
    /** 收起状态下的「搜索」按钮。 */
    private ButtonWidget searchToggle;
    /** 展开状态下输入框右边的「X」收起按钮。 */
    private ButtonWidget searchClose;
    /** 当前搜索词，小写、已 trim。 */
    private String query = "";
    /** 解析后的搜索词（分词、数字边界、ID 前缀剥离、命中打分）。 */
    private SearchQuery needle = SearchQuery.EMPTY;
    /**
     * 状态筛选：0 = 全部，1 = 只看已保留，2 = 只看未保留。
     *
     * <p>「已保留」指整个车系全选（含通过逐辆勾选达成的全选）；
     * 部分勾选的车系在两种筛选下都会出现 —— 它既包含保留的车，也包含没保留的车。
     */
    private int filterMode = 0;
    private ButtonWidget filterButton;

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
        super(Text.translatable(LANG_PREFIX + "title"));
        this.parent = parent;
    }

    static Text tr(String key, Object... args) {
        return Text.translatable(LANG_PREFIX + key, args);
    }

    /** 筛选按钮上的文字，跟随当前筛选状态。 */
    private Text filterLabel() {
        return switch (this.filterMode) {
            case 1 -> tr("filter_kept");
            case 2 -> tr("filter_unkept");
            default -> tr("filter_all");
        };
    }

    /** 名称或 ID 是否命中当前搜索词（空搜索词一律命中）。 */
    private boolean matches(String text) {
        return this.needle.matches(text);
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

        final int top = 84;
        final int bottom = this.height - 52;
        this.list = new TrainListWidget(this.client, this.width, this.height, top, bottom, 24);
        this.addDrawableChild(this.list);

        // 第 1 行（y=22）：搜索（收起时是按钮，展开时是输入框 + X）+ 状态筛选
        // 布局始终按「搜索框展开」的宽度算，这样筛选按钮位置恒定，展开/收起时不会跳。
        final int filterW = 92;
        final int closeW = 20;
        final int toggleW = 56;
        final int searchW = Math.min(210, Math.max(120, this.width - filterW - closeW - 12 - 80));
        final int searchRowW = searchW + 6 + closeW + 6 + filterW;
        final int searchX = (this.width - searchRowW) / 2;
        final int closeX = searchX + searchW + 6;
        final int filterX = closeX + closeW + 6;

        this.searchField = new TextFieldWidget(this.textRenderer, searchX, 22, searchW, 20, tr("search_hint"));
        this.searchField.setMaxLength(64);
        this.searchField.setText(this.query);
        this.searchField.setChangedListener(text -> {
            this.query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            this.needle = new SearchQuery(this.query);
            this.list.refreshRows();
        });
        this.addDrawableChild(this.searchField);

        this.searchClose = ButtonWidget.builder(Text.literal("X"), b -> this.closeSearch())
                .dimensions(closeX, 22, closeW, 20).build();
        this.addDrawableChild(this.searchClose);

        // 收起时：搜索按钮紧贴筛选按钮左侧，两个控件成一组靠右
        this.searchToggle = ButtonWidget.builder(tr("search"), b -> this.openSearch())
                .dimensions(filterX - 6 - toggleW, 22, toggleW, 20).build();
        this.addDrawableChild(this.searchToggle);

        this.filterButton = ButtonWidget.builder(filterLabel(), b -> {
            this.filterMode = (this.filterMode + 1) % 3;
            b.setMessage(filterLabel());
            this.list.refreshRows();
        }).dimensions(filterX, 22, filterW, 20).build();
        this.addDrawableChild(this.filterButton);
        this.applySearchLayout();

        // 第 2 行（y=48）：全选 / 全不选 / 恢复默认
        final int by = 48;
        final int bw = 110;
        final int gap = 6;
        final int totalW = bw * 3 + gap * 2;
        int x = (this.width - totalW) / 2;
        this.addDrawableChild(ButtonWidget.builder(tr("select_all"), b -> {
            keptFamilies.clear();
            keptFamilies.addAll(TrainFamilyCatalog.allFamilyIds());
            keptVehicleIds.clear();
            list.refreshRows();
        }).dimensions(x, by, bw, 20).build());
        x += bw + gap;
        this.addDrawableChild(ButtonWidget.builder(tr("select_none"), b -> {
            keptFamilies.clear();
            keptVehicleIds.clear();
            list.refreshRows();
        }).dimensions(x, by, bw, 20).build());
        x += bw + gap;
        this.addDrawableChild(ButtonWidget.builder(tr("reset"), b -> {
            keptFamilies.clear();
            keptVehicleIds.clear();
            list.refreshRows();
        }).dimensions(x, by, bw, 20).build());

        // 底部：取消（左）+ 完成（右）
        this.addDrawableChild(ButtonWidget.builder(tr("cancel"), b -> this.cancel())
                .dimensions(this.width / 2 - 154, this.height - 40, 150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(tr("done"), b -> this.close())
                .dimensions(this.width / 2 + 4, this.height - 40, 150, 20).build());

        // 打开时自动滚到第一个已保留的车系，让用户一眼看到自己勾过什么
        if (!keptFamilies.isEmpty()) {
            this.list.scrollToFamily(keptFamilies.iterator().next());
        }
    }

    /** 展开搜索框并聚焦，光标直接可以打字。 */
    private void openSearch() {
        this.searchOpen = true;
        this.applySearchLayout();
        if (this.searchField != null) {
            this.searchField.setFocused(true);
            this.setFocused(this.searchField);
        }
    }

    /** 收起搜索框，同时清空搜索词（收起却仍在过滤会让用户以为列表丢了东西）。 */
    private void closeSearch() {
        this.searchOpen = false;
        if (this.searchField != null) {
            this.searchField.setFocused(false);
            this.searchField.setText("");
        }
        this.query = "";
        this.needle = SearchQuery.EMPTY;
        this.applySearchLayout();
        if (this.list != null) {
            this.list.refreshRows();
        }
    }

    /** 按 searchOpen 切换「按钮」和「输入框 + X」两组控件的可见性。 */
    private void applySearchLayout() {
        if (this.searchField != null) {
            this.searchField.setVisible(this.searchOpen);
            if (!this.searchOpen) {
                this.searchField.setFocused(false);
            }
        }
        if (this.searchClose != null) {
            this.searchClose.visible = this.searchOpen;
        }
        if (this.searchToggle != null) {
            this.searchToggle.visible = !this.searchOpen;
        }
    }

    /**
     * ESC 等同于「取消」：丢弃改动回主界面，<b>不能</b>走 {@link #close()}。
     *
     * <p>原版 {@code Screen} 里 ESC 默认调用 {@link #close()}，如果直接继承，
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
            // 搜索框展开时，ESC 先收起搜索（清空搜索词），再按一次才退出界面 ——
            // 否则用户想「清掉搜索词」会把整个页面的改动一起丢掉。
            if (this.searchOpen) {
                this.closeSearch();
                return true;
            }
            this.cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 「取消」/ ESC / 右上角关闭：<b>丢弃本次改动</b>，什么都不写，直接回主界面。
     *
     * <p>与 {@link #close()} 的区别：close 表示用户按了「完成」，是有意的确认行为；
     * cancel 表示用户放弃，不应该把界面上的勾选当成确认结果写进配置。
     */
    public void cancel() {
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 用户取消选择界面，丢弃本次改动");
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /** 把当前勾选写进配置。由「完成」路径或重启弹窗的「是/否」触发，取消路径不调用。 */
    private void persistSelection() {
        ConfigAccess.writeSelection(keptFamilies, keptVehicleIds);
        MtrDisableOriginalTrains.LOGGER.info(
                "[MTR_NoOriginalTrains] 已保存列车保留选择：{} 个车系 / {} 辆单车",
                keptFamilies.size(), keptVehicleIds.size());
    }

    @Override
    public void close() {
        // 用户什么都没改 → 不写盘、不弹重启窗，直接回主界面。
        // 这样「打开看看又关掉」不会白问一次是否重启。
        final boolean changed = !keptFamilies.equals(baselineFamilies)
                || !keptVehicleIds.equals(baselineVehicleIds);
        if (!changed) {
            MtrDisableOriginalTrains.LOGGER.info(
                    "[MTR_NoOriginalTrains] 选择未改动，无需重启，直接返回");
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
            return;
        }

        if (this.client == null) {
            return;
        }
        // 「不再询问」模式下不弹窗：直接落盘并按记住的动作执行
        if (!RestartPromptConfig.isAskEnabled()) {
            persistSelection();
            if (RestartPromptConfig.isAutoRestart()) {
                MtrDisableOriginalTrains.LOGGER.info(
                        "[MTR_NoOriginalTrains] 已记住「立即重启」，正在关闭游戏进程（之后由用户自行启动）");
                this.client.scheduleStop();
            } else {
                this.client.setScreen(this.parent);
            }
            return;
        }
        // 弹窗里点「取消」要退回本界面继续改（此时还不落盘），点「否」才回主菜单
        this.client.setScreen(new RestartConfirmScreen(this.parent, this, this::persistSelection));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        final TextRenderer font = this.textRenderer;

        ctx.drawCenteredTextWithShadow(font, this.title, this.width / 2, 8, 0xFFFFFF);

        final int keptFam = keptFamilies.size();
        final int totalFam = TrainFamilyCatalog.families().size();
        // 摘要画在工具栏按钮（48~68）与列表（top=84）之间的空档 y=74
        ctx.drawCenteredTextWithShadow(font, tr("kept_summary", keptFam, totalFam),
                this.width / 2, 74, 0xA0A0A0);

        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(font, tr("restart_hint"), this.width / 2, this.height - 54, 0xE8C060);
    }

    @Override
    public boolean shouldPause() {
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

    /** 一行：车系行（vehicleId == null）或单辆车行；message != null 时是空状态提示行。 */
    private static final class Row {
        final TrainFamilyCatalog.Family family;
        final String vehicleId;
        final String vehicleName;
        /** 空状态提示文字（搜索无结果时用），非 null 表示这一行只是提示。 */
        final String message;

        Row(TrainFamilyCatalog.Family family, String vehicleId, String vehicleName) {
            this(family, vehicleId, vehicleName, null);
        }

        private Row(TrainFamilyCatalog.Family family, String vehicleId, String vehicleName, String message) {
            this.family = family;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.message = message;
        }

        static Row message(String text) {
            return new Row(null, null, null, text);
        }

        boolean isMessage() {
            return message != null;
        }

        boolean isFamilyRow() {
            return vehicleId == null && message == null;
        }
    }

    private final class TrainListWidget extends ElementListWidget<TrainEntry> {

        TrainListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
            refreshRows();
        }

        /** 车系的最高命中分（车系名本身，或族里任一车辆），用于搜索结果排序。 */
        private int familyScore(TrainFamilyCatalog.Family f) {
            int best = needle.score(f.displayName, f.id);
            for (var e : f.vehicles.entrySet()) {
                best = Math.max(best, needle.score(e.getValue(), e.getKey()));
            }
            return best;
        }

        void refreshRows() {
            final List<Row> rows = new ArrayList<>();
            final List<TrainFamilyCatalog.Family> families = new ArrayList<>(TrainFamilyCatalog.families());
            if (!query.isEmpty()) {
                // 搜索时按命中质量排序：车系名整体命中的排在「只有族里某辆车命中」的前面
                families.sort((x, y) -> Integer.compare(familyScore(y), familyScore(x)));
            }
            for (TrainFamilyCatalog.Family f : families) {
                final boolean familyHit = matches(f.displayName) || matches(f.id);
                // 命中的车辆：搜索词为空时全部命中；否则只要车系名/ID 已命中，整族都算命中
                final List<String> vehicleHits = new ArrayList<>();
                for (var e : f.vehicles.entrySet()) {
                    if (familyHit || needle.score(e.getValue(), e.getKey()) > 0) {
                        vehicleHits.add(e.getKey());
                    }
                }
                if (vehicleHits.isEmpty()) {
                    continue;
                }

                // 状态筛选：部分勾选的车系在「只看已保留」和「只看未保留」下都保留，
                // 因为它两边都有车，藏掉反而让人以为没这个车系。
                final boolean fullyKept = isFamilyFullyKept(f);
                final boolean partlyKept = isFamilyPartiallyKept(f);
                if (filterMode == 1 && !fullyKept && !partlyKept) {
                    continue;
                }
                if (filterMode == 2 && fullyKept) {
                    continue;
                }

                rows.add(new Row(f, null, null));
                // 搜索时自动展开命中的车，让用户直接看到命中在哪一辆；
                // 没搜索时沿用用户自己的展开状态。
                final boolean showVehicles = !query.isEmpty() || expanded.contains(f.id);
                if (showVehicles) {
                    final Iterable<String> ids = query.isEmpty() ? f.vehicles.keySet() : vehicleHits;
                    for (String id : ids) {
                        rows.add(new Row(f, id, f.vehicles.get(id)));
                    }
                }
            }
            if (rows.isEmpty()) {
                rows.add(Row.message(tr("search_no_result").getString()));
            }
            this.clearEntries();
            for (Row r : rows) {
                this.addEntry(new TrainEntry(r));
            }
        }

        /** 滚动到指定车系那一行（车系行之间每行占 itemHeight）。 */
        void scrollToFamily(String familyId) {
            // 有搜索词或筛选时行数与车系顺序不对应，直接不滚（避免算错位置）
            if (!query.isEmpty() || filterMode != 0) {
                return;
            }
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
        protected int getScrollbarPositionX() {
            return (this.width + getRowWidth()) / 2 + 6;
        }
    }

    private final class TrainEntry extends ElementListWidget.Entry<TrainEntry> {
        private final Row row;
        private final ButtonWidget foldButton;
        /** 勾选框的命中区域（render 时更新；车系行额外存展开按钮位置由 foldButton 自管）。 */
        private int hitCheckboxX;
        private int hitCheckboxY;

        TrainEntry(Row row) {
            this.row = row;
            final TrainFamilyCatalog.Family f = row.family;

            if (row.isFamilyRow()) {
                this.foldButton = ButtonWidget.builder(
                        Text.literal(expanded.contains(f.id) ? "−" : "+"),
                        b -> toggleExpand(f)).dimensions(0, 0, 16, 18).build();
            } else {
                this.foldButton = null;
            }
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            final TextRenderer font = TrainSelectScreen.this.textRenderer;

            // 空状态行（搜索/筛选没有命中任何车系）
            if (row.isMessage()) {
                ctx.drawCenteredTextWithShadow(font, Text.literal(row.message),
                        TrainSelectScreen.this.width / 2, y + 7, 0x808080);
                return;
            }

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
            ctx.drawBorder(bx, by, 16, 16, (kept || partial) ? 0xFF6FCF6F : 0xFF808080);
            if (kept) {
                // ✓ 勾：两段线组成对勾
                ctx.drawBorder(bx + 4, by + 7, 3, 3, 0xFF5FBF5F);
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
            ctx.drawTextWithShadow(font,
                    Text.literal(font.trimToWidth(name, entryWidth - (textX - x) - 70)),
                    textX, y + 7, color);

            if (familyRow) {
                final Text right = tr("vehicles", f.size());
                final int rw = font.getWidth(right);
                ctx.drawTextWithShadow(font, right, x + entryWidth - rw - 6, y + 7, 0x808080);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (row.isMessage()) {
                return false;
            }
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
        public List<? extends Element> children() {
            // ParentElement 要求；本行自绘 + 手动 mouseClicked，无内嵌控件
            return List.of();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List selectableChildren() {
            // 1.20.1 yarn 里该抽象方法签名是原始 List（非泛型），必须原样返回原始类型
            return List.of();
        }
    }
}
