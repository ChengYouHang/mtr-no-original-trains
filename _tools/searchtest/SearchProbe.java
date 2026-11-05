import java.util.*;

/** 1.3.0 搜索/筛选/冲突 逻辑探针：被切出的方法体来自真实源码，未做算法改动。 */
public class SearchProbe {
    static int pass = 0, fail = 0;

    static void check(boolean cond, String name, String detail) {
        if (cond) { pass++; System.out.println("PASS  " + name); }
        else { fail++; System.out.println("FAIL  " + name + "   <<< " + detail); }
    }

    // ---- 桩环境 ----
    static String tr(String key) { return "<" + key + ">"; }
    static String displayPackName(String raw) { return raw; }
    static Map<String, String> jsonNames = new HashMap<>();
    static String displayTrainName(String id) { return jsonNames.get(id); }
    static Map<String, Set<String>> PACKS = new LinkedHashMap<>();
    static Map<String, Set<String>> addonPacks() { return PACKS; }
    static Map<String, String> CAPTURED = new HashMap<>();
    static Map<String, String> capturedAddonNames() { return CAPTURED; }
    static List<Family> FAMILIES = new ArrayList<>();
    static List<Family> families() { return FAMILIES; }

    public static class Family {
        public String id; public String displayName;
        public LinkedHashMap<String, String> vehicles = new LinkedHashMap<>();
        Family(String id, String dn) { this.id = id; this.displayName = dn; }
    }

    // ================= 追加包页逻辑（切自 AddonSelectScreen） =================
    public static class AddonLogic {
        String query = ""; int filterMode = 0; int mode = 1;
        String selectedRaw = "上海地铁追加包"; String selectedDisplay = "上海地铁追加包";
        Set<String> keptIds = new HashSet<>();
        Map<String, Integer> packCounts = new HashMap<>();
        Set<String> blockedAddons = new HashSet<>();
        Set<String> expandedFamilies = new HashSet<>();
        Set<String> expandedSeries = new HashSet<>();
        SearchQuery needle = SearchQuery.EMPTY;
        void q(String s) { query = s; needle = new SearchQuery(s); }

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
static String stripSeriesPrefix(String familyKey) {
        final String series = seriesKeyOf(familyKey);
        if (series == null || series.equals(familyKey) || !familyKey.startsWith(series)) {
            return familyKey;
        }
        String rest = familyKey.substring(series.length()).trim();
        rest = rest.replaceAll("^[\\s\\-\u2014\u00b7\u3001]+", "").trim();
        return rest.isEmpty() ? familyKey : rest;
    }
static String labelOf(String id, Map<String, String> captured) {
        String name = captured == null ? null : captured.get(id);
        if (name == null || name.isEmpty()) {
            name = displayTrainName(id);
        }
        if (name == null || name.isEmpty()) {
            name = id;
        }
        return name;
    }
static String tailOf(String id) {
        if (id == null) {
            return "?";
        }
        final int us = id.lastIndexOf('_');
        return us >= 0 && us < id.length() - 1 ? id.substring(us + 1) : id;
    }
private boolean matches(String text) {
        return this.needle.matches(text);
    }
private int conflictOf(String id) {
        if (this.keptIds.contains(id)) {
            return 1;
        }
        return this.packCounts.getOrDefault(id, 0) > 1 ? 2 : 0;
    }
private boolean packHasSharedCars(String raw) {
        final Set<String> ids = addonPacks().get(raw);
        if (ids == null) {
            return false;
        }
        for (String id : ids) {
            if (this.packCounts.getOrDefault(id, 0) > 1) {
                return true;
            }
        }
        return false;
    }
private int conflictCountInPack() {
        final Set<String> ids = addonPacks().get(selectedRaw);
        if (ids == null) {
            return 0;
        }
        int n = 0;
        for (String id : ids) {
            if (conflictOf(id) != 0) {
                n++;
            }
        }
        return n;
    }
List<Row> buildRows() {
            final List<Row> rows = new ArrayList<>();
            final Map<String, Set<String>> packs = addonPacks();
            final Map<String, String> names = capturedAddonNames();
            if (mode == 0) {
                if (packs.isEmpty()) {
                    rows.add(Row.empty());
                } else {
                    // 搜索时按命中质量排序：包名整体命中排在只命中一部分的前面
                    final List<Map.Entry<String, Set<String>>> hits = new ArrayList<>();
                    for (var e : packs.entrySet()) {
                        final String display = displayPackName(e.getKey());
                        if (!matches(display) && !matches(e.getKey())) {
                            continue;
                        }
                        if (!blockedFilterOk(new ArrayList<>(e.getValue()))) {
                            continue;
                        }
                        hits.add(e);
                    }
                    if (!query.isEmpty()) {
                        hits.sort((x, y) -> Integer.compare(
                                needle.score(displayPackName(y.getKey()), y.getKey()),
                                needle.score(displayPackName(x.getKey()), x.getKey())));
                    }
                    for (var e : hits) {
                        rows.add(Row.pack(e.getKey(), displayPackName(e.getKey())));
                    }
                    if (hits.isEmpty()) {
                        rows.add(Row.message(tr("search_no_result")));
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
                    // 搜索时给每个车型打命中分：车型名本身命中，或族里有车命中，取较高者
                    final Map<String, Integer> familyScore = new HashMap<>();
                    for (var fe : byFamily.entrySet()) {
                        int s = needle.score(fe.getKey(), null);
                        for (String id : fe.getValue()) {
                            s = Math.max(s, needle.score(labelOf(id, names), id));
                        }
                        familyScore.put(fe.getKey(), s);
                    }
                    final List<Map.Entry<String, List<String>>> seriesOrder =
                            new ArrayList<>(seriesToFamilies.entrySet());
                    if (!query.isEmpty()) {
                        // 系列内：命中程度高的车型排前面（同分保持原声明顺序）
                        for (var so : seriesOrder) {
                            so.getValue().sort((x, y) -> Integer.compare(
                                    familyScore.getOrDefault(y, 0), familyScore.getOrDefault(x, 0)));
                        }
                        // 系列之间：整条线的最好命中分高的排前面
                        seriesOrder.sort((x, y) -> Integer.compare(
                                bestScore(y.getValue(), familyScore), bestScore(x.getValue(), familyScore)));
                    }
                    for (var se : seriesOrder) {
                        final String seriesKey = se.getKey();
                        final List<String> families = se.getValue();
                        // 搜索/筛选后仍然可见的车型
                        final List<String> visible = new ArrayList<>();
                        for (String familyKey : families) {
                            if (familyVisible(familyKey, byFamily.get(familyKey), names)) {
                                visible.add(familyKey);
                            }
                        }
                        if (visible.isEmpty()) {
                            continue;
                        }
                        if (families.size() == 1) {
                            // 这条线下只有一个车型，省掉中间层，直接显示车型行
                            addFamilyRows(rows, byFamily, names, families.get(0), 0);
                            continue;
                        }
                        final List<String> seriesIds = new ArrayList<>();
                        for (String familyKey : visible) {
                            seriesIds.addAll(byFamily.get(familyKey));
                        }
                        rows.add(Row.series(selectedRaw, selectedDisplay, seriesKey, seriesIds));
                        // 搜索或有筛选时强制展开，否则用户会看到「命中了但里面是空的」
                        if (expandedSeries.contains(selectedRaw + "|S|" + seriesKey)
                                || !query.isEmpty() || filterMode != 0) {
                            for (String familyKey : visible) {
                                addFamilyRows(rows, byFamily, names, familyKey, 1);
                            }
                        }
                    }
                }
                if (rows.isEmpty()) {
                    rows.add(Row.message(tr("search_no_result")));
                }
            }
            return rows;
        }
private boolean familyVisible(String familyKey, List<String> ids, Map<String, String> names) {
            if (ids == null || ids.isEmpty()) {
                return false;
            }
            if (!matches(familyKey) && matchingIds(ids, names).isEmpty()) {
                return false;
            }
            return blockedFilterOk(ids);
        }
private int bestScore(List<String> families, Map<String, Integer> familyScore) {
            int best = 0;
            for (String f : families) {
                best = Math.max(best, familyScore.getOrDefault(f, 0));
            }
            return best;
        }
private List<String> matchingIds(List<String> ids, Map<String, String> names) {
            if (query.isEmpty()) {
                return ids;
            }
            final List<String> out = new ArrayList<>();
            for (String id : ids) {
                if (needle.score(labelOf(id, names), id) > 0) {
                    out.add(id);
                }
            }
            return out;
        }
private boolean blockedFilterOk(List<String> ids) {
            if (filterMode == 0) {
                return true;
            }
            boolean anyBlocked = false;
            boolean allBlocked = true;
            for (String id : ids) {
                if (blockedAddons.contains(id)) {
                    anyBlocked = true;
                } else {
                    allBlocked = false;
                }
            }
            return filterMode == 1 ? anyBlocked : !allBlocked;
        }
private void addFamilyRows(List<Row> rows, Map<String, List<String>> byFamily,
                                   Map<String, String> names, String familyKey, int indent) {
            final List<String> familyIds = byFamily.get(familyKey);
            if (familyIds == null || familyIds.isEmpty()) {
                return;
            }
            // 嵌在系列下面时省掉重复的线路前缀：「上海地铁1号线-01A02伪八二世」 → 「01A02伪八二世」
            rows.add(Row.family(selectedRaw, selectedDisplay, familyKey,
                    indent > 0 ? stripSeriesPrefix(familyKey) : familyKey, familyIds, indent));
            // 搜索时强制展开命中的车辆；没搜索时沿用用户自己的展开状态
            if (query.isEmpty() && !expandedFamilies.contains(selectedRaw + "|F|" + familyKey)) {
                return;
            }
            // 搜到的是「这个车型」本身 → 整族展开；只是族里某几辆车命中 → 只展开命中的
            final List<String> shown = (!query.isEmpty() && needle.hitsName(familyKey))
                    ? familyIds : matchingIds(familyIds, names);
            if (shown.isEmpty()) {
                return;
            }
            // legacy 包的 4 个变体（trailer / cab_1..3）共用同一个名字，
            // 展开时补上变体后缀，否则司机看到 4 行一模一样的字
            final Map<String, Integer> nameCount = new HashMap<>();
            for (String id : shown) {
                nameCount.merge(labelOf(id, names), 1, Integer::sum);
            }
            for (String id : shown) {
                final String base = labelOf(id, names);
                final String label = nameCount.getOrDefault(base, 0) > 1
                        ? base + "  ·  " + tailOf(id) : base;
                rows.add(Row.vehicle(selectedRaw, selectedDisplay, id, label, indent + 1));
            }
        }
private static final String[] SERIES_SUFFIX_TOKENS = {
            "第?\\d+期", "\\d+期", "增购车", "增购", "神秘列车", "彩蛋车", "扩编",
            "xcan\\+?\\d*", "xcan", "lcd"
    };

static final class Row {
        static final int KIND_PACK = 0;
        static final int KIND_SERIES = 1;
        static final int KIND_FAMILY = 2;
        static final int KIND_VEHICLE = 3;
        static final int KIND_EMPTY = 4;
        static final int KIND_MESSAGE = 5;

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
        /** 空状态提示文字（KIND_MESSAGE 用）。 */
        final String message;

        private Row(int kind, String rawPack, String packDisplay, String familyKey, String familyDisplay,
                    List<String> familyIds, String vehicleId, String vehicleName, int indent, String message) {
            this.kind = kind;
            this.rawPack = rawPack;
            this.packDisplay = packDisplay;
            this.familyKey = familyKey;
            this.familyDisplay = familyDisplay;
            this.familyIds = familyIds;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.indent = indent;
            this.message = message;
        }

        static Row pack(String rawPack, String packDisplay) {
            return new Row(KIND_PACK, rawPack, packDisplay, null, null, null, null, null, 0, null);
        }

        static Row series(String rawPack, String packDisplay, String seriesKey, List<String> ids) {
            return new Row(KIND_SERIES, rawPack, packDisplay, seriesKey, seriesKey, ids, null, null, 0, null);
        }

        static Row family(String rawPack, String packDisplay, String familyKey, String familyDisplay,
                          List<String> ids, int indent) {
            return new Row(KIND_FAMILY, rawPack, packDisplay, familyKey, familyDisplay, ids, null, null,
                    indent, null);
        }

        static Row vehicle(String rawPack, String packDisplay, String vehicleId, String vehicleName, int indent) {
            return new Row(KIND_VEHICLE, rawPack, packDisplay, null, null, null, vehicleId, vehicleName,
                    indent, null);
        }

        static Row empty() {
            return new Row(KIND_EMPTY, null, null, null, null, null, null, null, 0, null);
        }

        static Row message(String text) {
            return new Row(KIND_MESSAGE, null, null, null, null, null, null, null, 0, text);
        }
    }
    }

    // ================= 内置页逻辑（切自 TrainSelectScreen） =================
    public static class BuiltinLogic {
        String query = ""; int filterMode = 0;
        Set<String> expanded = new HashSet<>();
        Set<String> keptFamilies = new HashSet<>();
        Set<String> keptVehicleIds = new HashSet<>();
        SearchQuery needle = SearchQuery.EMPTY;
        void q(String s) { query = s; needle = new SearchQuery(s); }

private boolean matches(String text) {
        return this.needle.matches(text);
    }
private int familyScore(Family f) {
            int best = needle.score(f.displayName, f.id);
            for (var e : f.vehicles.entrySet()) {
                best = Math.max(best, needle.score(e.getValue(), e.getKey()));
            }
            return best;
        }
List<Row> buildRows() {
            final List<Row> rows = new ArrayList<>();
            final List<Family> families = new ArrayList<>(families());
            if (!query.isEmpty()) {
                // 搜索时按命中质量排序：车系名整体命中的排在「只有族里某辆车命中」的前面
                families.sort((x, y) -> Integer.compare(familyScore(y), familyScore(x)));
            }
            for (Family f : families) {
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
                rows.add(Row.message(tr("search_no_result")));
            }
            return rows;
        }
boolean isFamilyFullyKept(Family f) {
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
boolean isFamilyPartiallyKept(Family f) {
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

static final class Row {
        final Family family;
        final String vehicleId;
        final String vehicleName;
        /** 空状态提示文字（搜索无结果时用），非 null 表示这一行只是提示。 */
        final String message;

        Row(Family family, String vehicleId, String vehicleName) {
            this(family, vehicleId, vehicleName, null);
        }

        private Row(Family family, String vehicleId, String vehicleName, String message) {
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
    }

    // ================= 用例 =================
    static String ar(AddonLogic.Row r) {
        if (r.message != null) return "MSG:" + r.message;
        switch (r.kind) {
            case AddonLogic.Row.KIND_PACK: return "PACK:" + r.packDisplay;
            case AddonLogic.Row.KIND_SERIES: return "SER[" + r.indent + "]:" + r.familyKey;
            case AddonLogic.Row.KIND_FAMILY: return "FAM[" + r.indent + "]:" + r.familyDisplay;
            case AddonLogic.Row.KIND_VEHICLE: return "VEH[" + r.indent + "]:" + r.vehicleName;
            default: return "EMPTY";
        }
    }
    static String joinA(List<AddonLogic.Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (AddonLogic.Row r : rows) sb.append(ar(r)).append(" | ");
        return sb.toString();
    }
    static String br(BuiltinLogic.Row r) {
        if (r.isMessage()) return "MSG:" + r.message;
        return r.isFamilyRow() ? "FAM:" + r.family.displayName : "VEH:" + r.vehicleName;
    }
    static String joinB(List<BuiltinLogic.Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (BuiltinLogic.Row r : rows) sb.append(br(r)).append(" | ");
        return sb.toString();
    }

    public static void main(String[] args) {
        // ===== SearchQuery 相关度（真实上海包车名）=====
        check(new SearchQuery("2号线").score("上海地铁2号线-02A01(AC02)西瓜", null) > 0,
            "Q1 搜2号线命中2号线", "");
        check(new SearchQuery("2号线").score("上海地铁12号线-12A01", null) == 0,
            "Q2 搜2号线不命中12号线", "");
        check(new SearchQuery("1号线").score("上海地铁1号线-01A02(DC01b)伪八二世", null) > 0,
            "Q3 搜1号线命中1号线", "");
        check(new SearchQuery("1号线").score("上海地铁11号线-11A01", null) == 0,
            "Q4 搜1号线不命中11号线", "");
        check(new SearchQuery("1号线").score("上海地铁10号线-10A01", null) == 0,
            "Q5 搜1号线不命中10号线", "");
        check(new SearchQuery("16").score("上海地铁16号线-16A01(AC19)抹茶-大站车", null) > 0,
            "Q6 纯数字16命中16A01", "");
        check(new SearchQuery("1").score("上海地铁16号线", null) == 0,
            "Q7 单字1不命中16号线", "");
        check(new SearchQuery("上海 16").score("上海地铁16号线-16A01(AC19)抹茶-大站车", null) > 0,
            "Q8 空格分词AND命中", "");
        check(new SearchQuery("上海 2").score("合肥轨道交通2号线1期(Forwards)", null) == 0,
            "Q9 分词AND要求每词都命中", "");
        check(new SearchQuery("ac19").score("上海地铁16号线-16A01(AC19)抹茶-大站车", null) > 0,
            "Q10 大小写不敏感", "");
        check(new SearchQuery("train").score("上海地铁2号线", "mtr_custom_train_ac02_trailer") == 0,
            "Q11 搜train不命中ID前缀", "");
        check(new SearchQuery("ac02").score("上海地铁2号线", "mtr_custom_train_ac02_trailer") > 0,
            "Q12 搜ac02命中ID主体", "");
        check(new SearchQuery("mtr").score("上海地铁2号线", "mtr_custom_train_ac02_trailer") == 0,
            "Q13 搜mtr不命中ID前缀", "");
        {
            int exact = new SearchQuery("上海地铁2号线").score("上海地铁2号线", null);
            int prefix = new SearchQuery("上海地铁2号线").score("上海地铁2号线-02A01", null);
            int idOnly = new SearchQuery("ac02").score("西瓜", "mtr_custom_train_ac02_trailer");
            check(exact == 4 && prefix == 3 && idOnly == 1,
                "Q14 命中质量分层(全等4/前缀3/仅ID1)", exact + "/" + prefix + "/" + idOnly);
        }

        // -------- 数据：3 个车型分属 3 条线 + 第 2 个包共享一辆车 --------
        Set<String> pack1 = new LinkedHashSet<>(Arrays.asList(
                "mtr_custom_train_sh1_a_cab_1", "mtr_custom_train_sh1_a_trailer",
                "mtr_custom_train_sh16_a_cab_1", "mtr_custom_train_he2_a_cab_1"));
        PACKS.put("上海地铁追加包", pack1);
        PACKS.put("共享测试包2", new LinkedHashSet<>(Arrays.asList("mtr_custom_train_sh1_a_cab_1", "mtr_custom_train_extra_cab_1")));
        CAPTURED.put("mtr_custom_train_sh1_a_cab_1", "上海地铁1号线-01A02");
        CAPTURED.put("mtr_custom_train_sh1_a_trailer", "上海地铁1号线-01A02");
        CAPTURED.put("mtr_custom_train_sh16_a_cab_1", "上海地铁16号线-16A01(AC19)抹茶-大站车");
        CAPTURED.put("mtr_custom_train_he2_a_cab_1", "合肥轨道交通2号线增购车");
        jsonNames.put("mtr_custom_train_extra_cab_1", "额外测试车");

        AddonLogic a = new AddonLogic();

        // A1 基线：无搜索无筛选 → 3 条线各 1 行车型行（单车型系列省掉中间层），不展开
        List<AddonLogic.Row> r1 = a.buildRows();
        check(r1.size() == 3
                && ar(r1.get(0)).equals("FAM[0]:上海地铁1号线-01A02")
                && ar(r1.get(1)).equals("FAM[0]:上海地铁16号线-16A01(AC19)抹茶-大站车")
                && ar(r1.get(2)).equals("FAM[0]:合肥轨道交通2号线增购车"),
            "A1 追加包基线分组", joinA(r1));

        // A2 搜索命中（大小写不敏感 + ID 命中）→ 只留 16 号线并强制展开到车
        a.q("16a01");
        List<AddonLogic.Row> r2 = a.buildRows();
        check(r2.size() == 2 && ar(r2.get(0)).startsWith("FAM[0]:上海地铁16号线")
                && ar(r2.get(1)).startsWith("VEH[1]:"),
            "A2 搜索强制展开(显示名小写命中)", joinA(r2));

        // A3 搜索命中显示名（含括号原文）
        a.q("抹茶");
        List<AddonLogic.Row> r3 = a.buildRows();
        check(r3.size() == 2 && ar(r3.get(0)).startsWith("FAM[0]:上海地铁16号线"),
            "A3 搜索命中显示名", joinA(r3));

        // A4 搜索无结果 → KIND_MESSAGE
        a.q("不存在的词zzz");
        List<AddonLogic.Row> r4 = a.buildRows();
        check(r4.size() == 1 && r4.get(0).message != null
                && r4.get(0).message.equals("<search_no_result>"),
            "A4 搜索无结果空状态", joinA(r4));
        a.q("");

        // A5 筛选=只看已拦：只显示有被拦车辆的车型；系列层强制展开但车不展开(点+才看)
        a.blockedAddons.add("mtr_custom_train_sh1_a_cab_1");
        a.filterMode = 1;
        List<AddonLogic.Row> r5 = a.buildRows();
        check(r5.size() == 1 && ar(r5.get(0)).equals("FAM[0]:上海地铁1号线-01A02"),
            "A5 筛选只看已拦", joinA(r5));

        // A6 筛选=只看未拦：3 个车型都保留（1号线没全拦、另两台没拦）
        a.filterMode = 2;
        List<AddonLogic.Row> r6 = a.buildRows();
        check(r6.size() == 3, "A6 筛选只看未拦", joinA(r6));
        a.filterMode = 0; a.blockedAddons.clear();

        // A7 多车系归并 + 系列行出现：给 1 号线加第二个车型
        CAPTURED.put("mtr_custom_train_sh1_b_cab_1", "上海地铁1号线-01A03");
        pack1.add("mtr_custom_train_sh1_b_cab_1");
        List<AddonLogic.Row> r7 = a.buildRows();
        check(ar(r7.get(0)).equals("SER[0]:上海地铁1号线"),
            "A7 两车型并一条线出系列行", joinA(r7));
        a.expandedSeries.add("上海地铁追加包|S|上海地铁1号线");
        List<AddonLogic.Row> r7b = a.buildRows();
        check(r7b.size() >= 3 && ar(r7b.get(1)).startsWith("FAM[1]:01A02")
                && ar(r7b.get(2)).startsWith("FAM[1]:01A03"),
            "A7b 系列展开后车型行去前缀", joinA(r7b));

        // A8 展开车型 → 同名两辆车补变体后缀
        a.expandedFamilies.add("上海地铁追加包|F|上海地铁1号线-01A02");
        List<AddonLogic.Row> r8 = a.buildRows();
        boolean ok8 = false;
        for (AddonLogic.Row rr : r8) {
            if (rr.kind == AddonLogic.Row.KIND_VEHICLE) {
                String n = rr.vehicleName;
                if (n.contains("cab_1") || n.contains("trailer")) ok8 = true;
            }
        }
        check(ok8, "A8 同名车变体后缀", joinA(r8));

        // A9 冲突判定：保留优先=1，多包共用=2；包内冲突计数
        a.keptIds.add("mtr_custom_train_sh16_a_cab_1");
        a.packCounts.put("mtr_custom_train_sh1_a_cab_1", 2);
        check(a.conflictOf("mtr_custom_train_sh16_a_cab_1") == 1, "A9a 保留优先冲突=1", "");
        check(a.conflictOf("mtr_custom_train_sh1_a_cab_1") == 2, "A9b 多包共用冲突=2", "");
        check(a.conflictOf("mtr_custom_train_he2_a_cab_1") == 0, "A9c 无冲突=0", "");
        check(a.conflictCountInPack() == 2, "A9d 包内冲突计数=2(sh1_a+sh16)", "count=" + a.conflictCountInPack());
        check(a.packHasSharedCars("上海地铁追加包"), "A9e 包行检测共用", "");
        check(!a.packHasSharedCars("无此包"), "A9f 未知包不误报", "");

        // A10 包列表页搜索：命中包名 / 无结果
        a.mode = 0; a.keptIds.clear(); a.packCounts.clear();
        a.q("上海");
        List<AddonLogic.Row> r10 = a.buildRows();
        check(r10.size() == 1 && ar(r10.get(0)).equals("PACK:上海地铁追加包"), "A10a 包名搜索", joinA(r10));
        a.q("zzz");
        List<AddonLogic.Row> r10b = a.buildRows();
        check(r10b.size() == 1 && r10b.get(0).message != null, "A10b 包搜索无结果", joinA(r10b));
        a.mode = 1; a.q("");

        // -------- 内置页用例 --------
        Family fa = new Family("fa", "A线列车");
        fa.vehicles.put("mtr:fa_1", "A线车厢1"); fa.vehicles.put("mtr:fa_2", "A线车厢2");
        Family fb = new Family("fb", "B线列车");
        fb.vehicles.put("mtr:fb_1", "B线车厢1");
        Family fc = new Family("fc", "C线列车");
        fc.vehicles.put("mtr:fc_1", "C线车厢1"); fc.vehicles.put("mtr:fc_2", "C线车厢2");
        FAMILIES.add(fa); FAMILIES.add(fb); FAMILIES.add(fc);

        BuiltinLogic b = new BuiltinLogic();
        List<BuiltinLogic.Row> b1 = b.buildRows();
        check(b1.size() == 3 && br(b1.get(0)).equals("FAM:A线列车"), "B1 内置页基线不展开", joinB(b1));

        b.expanded.add("fa");
        List<BuiltinLogic.Row> b2 = b.buildRows();
        check(b2.size() == 5 && br(b2.get(1)).equals("VEH:A线车厢1"), "B2 手动展开出车行", joinB(b2));
        b.expanded.clear();

        b.q("b线");
        List<BuiltinLogic.Row> b3 = b.buildRows();
        check(b3.size() == 2 && br(b3.get(0)).equals("FAM:B线列车")
                && br(b3.get(1)).equals("VEH:B线车厢1"),
            "B3 搜索车系名自动展开", joinB(b3));

        b.q("车厢2");
        List<BuiltinLogic.Row> b4 = b.buildRows();
        check(b4.size() == 4 && br(b4.get(1)).equals("VEH:A线车厢2")
                && br(b4.get(3)).equals("VEH:C线车厢2"),
            "B4 搜索单车名只出命中车", joinB(b4));

        b.q("zzz");
        List<BuiltinLogic.Row> b5 = b.buildRows();
        check(b5.size() == 1 && b5.get(0).isMessage(), "B5 搜索无结果空状态", joinB(b5));
        b.q("");

        b.keptFamilies.add("fa");
        b.filterMode = 1;
        List<BuiltinLogic.Row> b6 = b.buildRows();
        check(b6.size() == 1 && br(b6.get(0)).equals("FAM:A线列车"), "B6 只看已保留", joinB(b6));

        b.keptFamilies.clear();
        b.keptVehicleIds.add("mtr:fc_1");
        List<BuiltinLogic.Row> b6b = b.buildRows();
        check(b6b.size() == 1 && br(b6b.get(0)).equals("FAM:C线列车"), "B6b 部分保留也算已保留侧", joinB(b6b));

        b.filterMode = 2;
        List<BuiltinLogic.Row> b7 = b.buildRows();
        check(b7.size() == 3, "B7 只看未保留(部分保留两边都显)", joinB(b7));
        b.keptVehicleIds.clear(); b.filterMode = 0;

        System.out.println("----");
        System.out.println("PASS " + pass + " / FAIL " + fail);
        if (fail > 0) System.exit(1);
    }
}
