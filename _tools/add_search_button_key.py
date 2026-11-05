"""给 42 个语言文件补 `gui.mtr_no_original_trains.search`（收起状态下的搜索按钮文字）。

主要语言给本地化，其余回退英文 Search。幂等：已存在就覆盖。
Fabric 与 Forge 两份资源目录同时补。
"""
import io
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DIRS = [
    os.path.join(ROOT, "src", "main", "resources", "assets", "mtr_no_original_trains", "lang"),
    os.path.join(ROOT, "forge", "src", "main", "resources", "assets", "mtr_no_original_trains", "lang"),
]
KEY = "gui.mtr_no_original_trains.search"

T = {
    "zh_cn": "搜索", "zh_tw": "搜尋", "zh_hk": "搜尋",
    "ja_jp": "検索", "ko_kr": "검색",
    "de_de": "Suchen", "fr_fr": "Rechercher", "es_es": "Buscar",
    "pt_br": "Buscar", "pt_pt": "Procurar", "it_it": "Cerca",
    "ru_ru": "Поиск", "pl_pl": "Szukaj", "tr_tr": "Ara",
    "nl_nl": "Zoeken", "sv_se": "Sök", "da_dk": "Søg", "no_no": "Søk",
    "fi_fi": "Haku", "cs_cz": "Hledat", "sk_sk": "Hľadať", "hu_hu": "Keresés",
    "ro_ro": "Caută", "bg_bg": "Търсене", "uk_ua": "Пошук", "be_by": "Пошук",
    "el_gr": "Αναζήτηση", "he_il": "חיפוש", "ar_sa": "بحث", "hi_in": "खोजें",
    "th_th": "ค้นหา", "vi_vn": "Tìm kiếm", "id_id": "Cari", "ms_my": "Cari",
    "tl_ph": "Maghanap", "tr_tr2": "",
}

n = 0
for d in DIRS:
    if not os.path.isdir(d):
        continue
    for fn in sorted(os.listdir(d)):
        if not fn.endswith(".json"):
            continue
        loc = fn[:-5]
        path = os.path.join(d, fn)
        with io.open(path, encoding="utf-8") as f:
            data = json.load(f)
        data[KEY] = T.get(loc, "Search")
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        n += 1

print("updated", n, "files")
