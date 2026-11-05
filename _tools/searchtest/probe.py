# -*- coding: utf-8 -*-
"""
1.3.0 搜索/筛选/冲突 逻辑探针生成器。
从 AddonSelectScreen.java / TrainSelectScreen.java 里按签名切出真实方法体，
套上桩环境生成 SearchProbe.java，再由 run.sh 编译运行。
"""
import re, os, io

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "src", "main", "java", "com", "mtrdisableoriginaltrains")
OUT = os.path.dirname(os.path.abspath(__file__))

def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()

addon_src = read(os.path.join(SRC, "AddonSelectScreen.java"))
builtin_src = read(os.path.join(SRC, "TrainSelectScreen.java"))

def cut_method(src, anchor):
    """按签名锚点 + 花括号配平切出方法/类/字段。"""
    i = src.find(anchor)
    assert i >= 0, "anchor not found: " + anchor
    j = src.find("{", i)
    depth = 0
    k = j
    while k < len(src):
        c = src[k]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[i:k + 1]
        k += 1
    raise AssertionError("unbalanced braces at " + anchor)

def cut_field(src, anchor):
    """切到 ; 结尾的字段声明（如数组常量）。"""
    i = src.find(anchor)
    assert i >= 0, "field anchor not found: " + anchor
    j = src.find(";", i)
    return src[i:j + 1]

# ---------------- 追加包页（AddonSelectScreen）----------------
addon_methods = []
for a in [
    "static String familyKeyOf(String id, String name) {",
    "static String seriesKeyOf(String familyKey) {",
    "static String stripSeriesPrefix(String familyKey) {",
    "static String labelOf(String id, Map<String, String> captured) {",
    "static String tailOf(String id) {",
    "private boolean matches(String text) {",
    "private int conflictOf(String id) {",
    "private boolean packHasSharedCars(String raw) {",
    "private int conflictCountInPack() {",
    "void refreshRows() {",
    "private boolean familyVisible(String familyKey, List<String> ids, Map<String, String> names) {",
    "private int bestScore(List<String> families, Map<String, Integer> familyScore) {",
    "private List<String> matchingIds(List<String> ids, Map<String, String> names) {",
    "private boolean blockedFilterOk(List<String> ids) {",
    "private void addFamilyRows(List<Row> rows, Map<String, List<String>> byFamily,",
]:
    addon_methods.append(cut_method(addon_src, a))
addon_methods.append(cut_method(addon_src, "private static final String[] SERIES_SUFFIX_TOKENS") + ";")
addon_row = cut_method(addon_src, "private static final class Row {")

def addon_fix(text):
    text = re.sub(r'tr\(("(?:[^"\\]|\\.)*")\)\.getString\(\)', r'tr(\1)', text)
    text = text.replace("AddonPackScanner.addonPacks()", "addonPacks()")
    text = text.replace("AddonPackScanner.displayPackName(", "displayPackName(")
    text = text.replace("AddonPackScanner.displayTrainName(", "displayTrainName(")
    text = text.replace("BuiltinTrainFilter.capturedAddonNames()", "capturedAddonNames()")
    # refreshRows 改成返回 List<Row>，尾部的控件装载换成 return
    text = text.replace("void refreshRows() {", "List<Row> buildRows() {", 1)
    text = re.sub(r'this\.clearEntries\(\);\s*for \(Row r : rows\) \{\s*this\.addEntry\(new AddonEntry\(r\)\);\s*\}',
                  'return rows;', text)
    return text

addon_methods = [addon_fix(m) for m in addon_methods]
addon_row = addon_row.replace("private static final class Row", "static final class Row", 1)

# ---------------- 内置页（TrainSelectScreen）----------------
builtin_methods = []
for a in [
    "private boolean matches(String text) {",
    "private int familyScore(TrainFamilyCatalog.Family f) {",
    "void refreshRows() {",
    "boolean isFamilyFullyKept(TrainFamilyCatalog.Family f) {",
    "boolean isFamilyPartiallyKept(TrainFamilyCatalog.Family f) {",
]:
    builtin_methods.append(cut_method(builtin_src, a))
builtin_row = cut_method(builtin_src, "private static final class Row {")

def builtin_fix(text):
    text = re.sub(r'tr\(("(?:[^"\\]|\\.)*")\)\.getString\(\)', r'tr(\1)', text)
    text = text.replace("TrainFamilyCatalog.families()", "families()")
    text = text.replace("TrainFamilyCatalog.Family", "Family")
    text = text.replace("void refreshRows() {", "List<Row> buildRows() {", 1)
    text = re.sub(r'this\.clearEntries\(\);\s*for \(Row r : rows\) \{\s*this\.addEntry\(new TrainEntry\(r\)\);\s*\}',
                  'return rows;', text)
    return text

builtin_methods = [builtin_fix(m) for m in builtin_methods]
builtin_row = builtin_fix(builtin_row).replace("private static final class Row", "static final class Row", 1)

# ---------------- 组装 SearchProbe.java ----------------
probe = r'''import java.util.*;

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

%%ADDON_METHODS%%

%%ADDON_ROW%%
    }

    // ================= 内置页逻辑（切自 TrainSelectScreen） =================
    public static class BuiltinLogic {
        String query = ""; int filterMode = 0;
        Set<String> expanded = new HashSet<>();
        Set<String> keptFamilies = new HashSet<>();
        Set<String> keptVehicleIds = new HashSet<>();
        SearchQuery needle = SearchQuery.EMPTY;
        void q(String s) { query = s; needle = new SearchQuery(s); }

%%BUILTIN_METHODS%%

%%BUILTIN_ROW%%
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
'''

probe = probe.replace("%%ADDON_METHODS%%", "\n".join(addon_methods))
probe = probe.replace("%%ADDON_ROW%%", addon_row)
probe = probe.replace("%%BUILTIN_METHODS%%", "\n".join(builtin_methods))
probe = probe.replace("%%BUILTIN_ROW%%", builtin_row)

out_java = os.path.join(OUT, "SearchProbe.java")
with io.open(out_java, "w", encoding="utf-8") as f:
    f.write(probe)

# 把线上 SearchQuery.java 原文（只去掉 package 行）复制成默认包版本，确保测的是真实实现
sq_src = io.open(os.path.join(SRC, "SearchQuery.java"), encoding="utf-8").read()
sq_src = re.sub(r'^\s*package\s+[\w.]+;\s*', '', sq_src, count=1)
with io.open(os.path.join(OUT, "SearchQuery.java"), "w", encoding="utf-8") as f:
    f.write(sq_src)
print("written", out_java)
