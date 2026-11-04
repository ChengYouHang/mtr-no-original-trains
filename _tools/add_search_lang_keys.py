"""为 42 个语言文件注入 1.3.0 新增的「搜索 / 筛选 / 冲突提示」键。

新增键：
  search_hint          搜索框占位符
  filter_all           筛选：全部
  filter_kept          筛选：只看已保留（内置页）
  filter_unkept        筛选：只看未保留（内置页）
  filter_blocked       筛选：只看已拦截（追加包页）
  filter_unblocked     筛选：只看未拦截（追加包页）
  search_no_result     搜索无结果提示
  conflict_keep_tip    冲突提示：这辆车也在保留清单里
  conflict_multi_tip   冲突提示：这辆车被多个包声明
  conflict_count       冲突数量

主要语言逐个翻译，其余语言回退英文（Minecraft 缺键时自动回落 en_us）。
幂等：键已存在则覆盖。Fabric 与 Forge 两份资源目录必须同时补。
"""
import io
import json
import os

HERE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "mtr_no_original_trains", "lang")
HERE_FORGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                          "forge", "src", "main", "resources", "assets",
                          "mtr_no_original_trains", "lang")

P = "gui.mtr_no_original_trains."

KEYS = ["search_hint", "filter_all", "filter_kept", "filter_unkept", "filter_blocked",
        "filter_unblocked", "search_no_result", "conflict_keep_tip", "conflict_multi_tip",
        "conflict_count"]

EN = (
    "Search name or ID…",
    "All",
    "Kept",
    "Not kept",
    "Blocked",
    "Not blocked",
    "Nothing matches your search",
    "This car is also on the built-in keep list. Keeping wins, so blocking it has no effect.",
    "Another addon pack declares this same car. Blocking it removes the car from both packs.",
    "%s conflicts",
)

T = {
    "zh_cn": (
        "搜索名称或 ID…", "全部", "已保留", "未保留", "已拦截", "未拦截",
        "没有匹配的列车",
        "这辆车同时在内置保留清单里，保留优先，勾选拦截不会生效。",
        "这辆车也被另一个追加包声明，拦截后两个包里的这辆车都会消失。",
        "%s 处冲突",
    ),
    "zh_tw": (
        "搜尋名稱或 ID…", "全部", "已保留", "未保留", "已攔截", "未攔截",
        "沒有符合的列車",
        "這輛車同時在內建保留清單裡，保留優先，勾選攔截不會生效。",
        "這輛車也被另一個附加包宣告，攔截後兩個包裡的這輛車都會消失。",
        "%s 處衝突",
    ),
    "zh_hk": (
        "搜尋名稱或 ID…", "全部", "已保留", "未保留", "已攔截", "未攔截",
        "沒有符合的列車",
        "這輛車同時在內置保留清單裡，保留優先，勾選攔截不會生效。",
        "這輛車也被另一個附加包宣告，攔截後兩個包裡的這輛車都會消失。",
        "%s 處衝突",
    ),
    "ja_jp": (
        "名前または ID で検索…", "すべて", "保持中", "未保持", "ブロック中", "未ブロック",
        "一致する列車がありません",
        "この車両は内蔵の保持リストにも含まれています。保持が優先されるため、ブロックしても効果はありません。",
        "同じ車両が別のアドオンパックにも含まれています。ブロックすると両方から消えます。",
        "競合 %s 件",
    ),
    "ko_kr": (
        "이름 또는 ID 검색…", "전체", "유지됨", "유지 안 됨", "차단됨", "차단 안 됨",
        "일치하는 열차가 없습니다",
        "이 차량은 내장 유지 목록에도 있습니다. 유지가 우선이라 차단해도 효과가 없습니다.",
        "같은 차량이 다른 애드온 팩에도 있습니다. 차단하면 두 팩 모두에서 사라집니다.",
        "충돌 %s건",
    ),
    "de_de": (
        "Name oder ID suchen…", "Alle", "Behalten", "Nicht behalten", "Blockiert",
        "Nicht blockiert",
        "Keine Treffer",
        "Dieser Zug steht auch auf der Behalten-Liste. Behalten hat Vorrang, Blockieren bringt nichts.",
        "Ein anderes Addon-Paket enthält denselben Zug. Blockieren entfernt ihn aus beiden.",
        "%s Konflikte",
    ),
    "fr_fr": (
        "Rechercher un nom ou un ID…", "Tous", "Conservés", "Non conservés", "Bloqués",
        "Non bloqués",
        "Aucun résultat",
        "Ce train figure aussi dans la liste des trains conservés. La conservation l'emporte : le bloquer n'a aucun effet.",
        "Un autre pack d'addon déclare ce même train. Le bloquer le retire des deux packs.",
        "%s conflits",
    ),
    "es_es": (
        "Buscar nombre o ID…", "Todos", "Conservados", "No conservados", "Bloqueados",
        "No bloqueados",
        "Sin resultados",
        "Este tren también está en la lista de conservados. Conservar tiene prioridad, así que bloquearlo no hace nada.",
        "Otro paquete de addon declara este mismo tren. Bloquearlo lo quita de ambos.",
        "%s conflictos",
    ),
    "pt_br": (
        "Buscar nome ou ID…", "Todos", "Mantidos", "Não mantidos", "Bloqueados",
        "Não bloqueados",
        "Nenhum resultado",
        "Este trem também está na lista de mantidos. Manter tem prioridade, então bloquear não faz efeito.",
        "Outro pacote de addon declara este mesmo trem. Bloquear remove o trem dos dois.",
        "%s conflitos",
    ),
    "pt_pt": (
        "Procurar nome ou ID…", "Todos", "Mantidos", "Não mantidos", "Bloqueados",
        "Não bloqueados",
        "Sem resultados",
        "Este comboio também está na lista de mantidos. Manter tem prioridade, bloquear não tem efeito.",
        "Outro pacote de addon declara este mesmo comboio. Bloquear remove-o dos dois.",
        "%s conflitos",
    ),
    "ru_ru": (
        "Поиск по названию или ID…", "Все", "Сохранённые", "Несохранённые", "Заблокированные",
        "Незаблокированные",
        "Ничего не найдено",
        "Этот поезд также в списке сохранённых. Сохранение важнее — блокировка не подействует.",
        "Этот же поезд объявлен в другом аддон-паке. Блокировка уберёт его из обоих.",
        "Конфликтов: %s",
    ),
    "it_it": (
        "Cerca nome o ID…", "Tutti", "Conservati", "Non conservati", "Bloccati",
        "Non bloccati",
        "Nessun risultato",
        "Anche questo treno è nell'elenco dei conservati. Conservare ha la precedenza, bloccarlo non ha effetto.",
        "Un altro pacchetto addon dichiara lo stesso treno. Bloccarlo lo rimuove da entrambi.",
        "%s conflitti",
    ),
    "pl_pl": (
        "Szukaj nazwy lub ID…", "Wszystkie", "Zachowane", "Niezachowane", "Zablokowane",
        "Niezablokowane",
        "Brak wyników",
        "Ten pociąg jest też na liście zachowanych. Zachowanie ma priorytet, blokowanie nic nie da.",
        "Inny pakiet dodatku deklaruje ten sam pociąg. Zablokowanie usunie go z obu.",
        "Konflikty: %s",
    ),
}


def patch_dir(base):
    changed = 0
    for name in sorted(os.listdir(base)):
        if not name.endswith(".json"):
            continue
        path = os.path.join(base, name)
        lang = name[:-5].lower()
        values = T.get(lang, EN)
        with io.open(path, encoding="utf-8") as f:
            data = json.load(f)
        for key, value in zip(KEYS, values):
            data[P + key] = value
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        changed += 1
    return changed


if __name__ == "__main__":
    print("fabric:", patch_dir(HERE))
    print("forge :", patch_dir(HERE_FORGE))
