public class GroupProbe {
    private static final String[] SERIES_SUFFIX_TOKENS = {
            "第?\\d+期", "\\d+期", "增购车", "增购", "神秘列车", "彩蛋车", "扩编",
            "xcan\\+?\\d*", "xcan", "lcd"
    };

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


    public static void main(String[] args) throws Exception {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(
                java.nio.file.Paths.get(args[0]), java.nio.charset.StandardCharsets.UTF_8);
        java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, Integer>> series = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, java.util.Set<String>> fams = new java.util.LinkedHashMap<>();
        for (String line : lines) {
            if (line.isEmpty()) continue;
            int tab = line.indexOf('\t');
            String name = tab >= 0 ? line.substring(0, tab) : line;
            String id = tab >= 0 ? line.substring(tab + 1) : "";
            String fam = familyKeyOf(id, name);
            fams.computeIfAbsent(fam, k -> new java.util.LinkedHashSet<>()).add(id);
            series.computeIfAbsent(seriesKeyOf(fam), k -> new java.util.LinkedHashMap<>()).putIfAbsent(fam, 0);
        }
        System.out.println("车 " + lines.size() + " / 车型 " + fams.size() + " / 系列 " + series.size());
        for (var se : series.entrySet()) {
            int total = 0;
            for (String fam : se.getValue().keySet()) total += fams.get(fam).size();
            System.out.printf("  [%3d] %-28s %d 车型%n", total, se.getKey(), se.getValue().size());
            for (String fam : se.getValue().keySet()) {
                System.out.printf("          %-40s %d%s%n", stripSeriesPrefix(fam), fams.get(fam).size(),
                        se.getValue().size() > 1 ? "" : "");
            }
        }
    }
}
