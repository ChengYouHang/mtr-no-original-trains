"""为 42 个语言文件注入「追加包列车拦截」新增键。

新增键：
  gui.mtr_no_original_trains.tab_builtin    内置列车页名
  gui.mtr_no_original_trains.tab_addon      追加包列车页名
  gui.mtr_no_original_trains.addon_summary  追加包页摘要
  gui.mtr_no_original_trains.addon_empty    无追加包提示

主要语言逐个翻译，其余语言回退英文。幂等：键已存在则覆盖。
"""
import io
import json
import os

HERE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "mtr_no_original_trains", "lang")
# Forge 的资源目录是独立拷贝，必须同步补键（否则 Forge 端新界面显示原始键名）
HERE_FORGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                          "forge", "src", "main", "resources", "assets",
                          "mtr_no_original_trains", "lang")

P = "gui.mtr_no_original_trains."

# lang -> (tab_builtin, tab_addon, addon_summary, addon_empty)
T = {
 "en_us": ("Built-in Trains", "Addon Trains",
           "Blocking %1$s / %2$s addon trains across %3$s packs",
           "No addon pack trains detected"),
 "zh_cn": ("内置列车", "追加包列车",
           "已拦截追加包列车 %1$s / %2$s 辆（%3$s 个包）",
           "未检测到追加包列车"),
 "zh_tw": ("內建列車", "附加包列車",
           "已攔截附加包列車 %1$s / %2$s 輛（%3$s 個包）",
           "未偵測到附加包列車"),
 "zh_hk": ("內置列車", "附加包列車",
           "已攔截附加包列車 %1$s / %2$s 輛（%3$s 個包）",
           "未偵測到附加包列車"),
 "ja_jp": ("内蔵列車", "追加パック列車",
           "追加パックの列車を %1$s / %2$s 両ブロック中（%3$s パック）",
           "追加パックの列車は検出されませんでした"),
 "ko_kr": ("내장 열차", "애드온 열차",
           "애드온 열차 %1$s / %2$s량 차단 중 (%3$s개 팩)",
           "감지된 애드온 열차가 없습니다"),
 "de_de": ("Eingebaute Züge", "Addon-Züge",
           "%1$s / %2$s Addon-Züge blockiert (%3$s Pakete)",
           "Keine Addon-Züge erkannt"),
 "fr_fr": ("Trains intégrés", "Trains d'addons",
           "%1$s / %2$s trains d'addons bloqués (%3$s packs)",
           "Aucun train d'addon détecté"),
 "es_es": ("Trenes integrados", "Trenes de addons",
           "Bloqueando %1$s / %2$s trenes de addons (%3$s paquetes)",
           "No se detectaron trenes de addons"),
 "pt_br": ("Trens integrados", "Trens de addons",
           "Bloqueando %1$s / %2$s trens de addons (%3$s pacotes)",
           "Nenhum trem de addon detectado"),
 "pt_pt": ("Comboios integrados", "Comboios de addons",
           "A bloquear %1$s / %2$s comboios de addons (%3$s pacotes)",
           "Nenhum comboio de addon detetado"),
 "ru_ru": ("Встроенные поезда", "Поезда аддонов",
           "Заблокировано %1$s / %2$s поездов аддонов (%3$s пакетов)",
           "Поезда аддонов не обнаружены"),
 "uk_ua": ("Вбудовані поїзди", "Поїзди аддонів",
           "Заблоковано %1$s / %2$s поїздів аддонів (%3$s пакетів)",
           "Поїздів аддонів не виявлено"),
 "it_it": ("Treni integrati", "Treni di addon",
           "Bloccati %1$s / %2$s treni di addon (%3$s pacchetti)",
           "Nessun treno di addon rilevato"),
 "nl_nl": ("Ingebouwde treinen", "Addon-treinen",
           "%1$s / %2$s addon-treinen geblokkeerd (%3$s pakketten)",
           "Geen addon-treinen gedetecteerd"),
 "pl_pl": ("Wbudowane pociągi", "Pociągi addonów",
           "Zablokowano %1$s / %2$s pociągów addonów (%3$s paczek)",
           "Nie wykryto pociągów addonów"),
 "cs_cz": ("Vestavěné vlaky", "Vlaky z addonů",
           "Blokováno %1$s / %2$s vlaků z addonů (%3$s balíčků)",
           "Nebyly zjištěny žádné vlaky z addonů"),
 "sk_sk": ("Vstavané vlaky", "Vlaky z addonov",
           "Blokovaných %1$s / %2$s vlakov z addonov (%3$s balíkov)",
           "Neboli zistené žiadne vlaky z addonov"),
 "hu_hu": ("Beépített vonatok", "Addon vonatok",
           "%1$s / %2$s addon vonat blokkolva (%3$s csomag)",
           "Nem észlelhető addon vonat"),
 "ro_ro": ("Trenuri integrate", "Trenuri din addonuri",
           "Se blochează %1$s / %2$s trenuri din addonuri (%3$s pachete)",
           "Niciun tren din addon detectat"),
 "sv_se": ("Inbyggda tåg", "Addon-tåg",
           "Blockerar %1$s / %2$s addon-tåg (%3$s paket)",
           "Inga addon-tåg upptäcktes"),
 "da_dk": ("Indbyggede tog", "Addon-tog",
           "Blokerer %1$s / %2$s addon-tog (%3$s pakker)",
           "Ingen addon-tog fundet"),
 "no_no": ("Innebygde tog", "Addon-tog",
           "Blokkerer %1$s / %2$s addon-tog (%3$s pakker)",
           "Ingen addon-tog oppdaget"),
 "fi_fi": ("Sisäänrakennetut junat", "Addon-junat",
           "Estetään %1$s / %2$s addon-junaa (%3$s pakettia)",
           "Addon-junia ei havaittu"),
 "el_gr": ("Ενσωματωμένα τρένα", "Τρένα addon",
           "Μπλοκάρονται %1$s / %2$s τρένα addon (%3$s πακέτα)",
           "Δεν εντοπίστηκαν τρένα addon"),
 "tr_tr": ("Yerleşik trenler", "Eklenti trenleri",
           "%1$s / %2$s eklenti treni engelleniyor (%3$s paket)",
           "Eklenti treni algılanmadı"),
 "ar_sa": ("القطارات المدمجة", "قطارات الإضافات",
           "يتم حظر %1$s / %2$s من قطارات الإضافات (%3$s حزم)",
           "لم يتم اكتشاف قطارات الإضافات"),
 "he_il": ("רכבות מובנות", "רכבות תוספים",
           "חוסמים %1$s / %2$s רכבות תוספים (%3$s חבילות)",
           "לא זוהו רכבות תוספים"),
 "hi_in": ("अंतर्निहित ट्रेनें", "ऐड-ऑन ट्रेनें",
           "%1$s / %2$s ऐड-ऑन ट्रेनें अवरुद्ध (%3$s पैक)",
           "कोई ऐड-ऑन ट्रेन नहीं मिली"),
 "id_id": ("Kereta bawaan", "Kereta addon",
           "Memblokir %1$s / %2$s kereta addon (%3$s paket)",
           "Tidak ada kereta addon terdeteksi"),
 "ms_my": ("Kereta terbina dalam", "Kereta addon",
           "Menyekat %1$s / %2$s kereta addon (%3$s pakej)",
           "Tiada kereta addon dikesan"),
 "th_th": ("ขบวนรถในเกม", "ขบวนรถจากแอดดอน",
           "บล็อกขบวนรถจากแอดดอน %1$s / %2$s คัน (%3$s แพ็ก)",
           "ไม่พบขบวนรถจากแอดดอน"),
 "vi_vn": ("Tàu tích hợp", "Tàu từ addon",
           "Đang chặn %1$s / %2$s tàu addon (%3$s gói)",
           "Không phát hiện tàu addon nào"),
}

# 第二批键：返回 / 包明细摘要 / 包列表摘要（lang -> (back, addon_pack_summary, addon_list_summary)）
T2 = {
 "en_us": ("Back",
           "Blocking %1$s / %2$s trains in this pack",
           "%1$s addon packs, %2$s trains in total"),
 "zh_cn": ("返回",
           "已拦截该包列车 %1$s / %2$s 辆",
           "共 %1$s 个追加包，%2$s 辆列车"),
 "zh_tw": ("返回",
           "已攔截該包列車 %1$s / %2$s 輛",
           "共 %1$s 個附加包，%2$s 輛列車"),
 "zh_hk": ("返回",
           "已攔截該包列車 %1$s / %2$s 輛",
           "共 %1$s 個附加包，%2$s 輛列車"),
 "ja_jp": ("戻る",
           "このパックの列車を %1$s / %2$s 両ブロック中",
           "追加パック %1$s 個、合計 %2$s 両"),
 "ko_kr": ("뒤로",
           "이 팩의 열차 %1$s / %2$s량 차단 중",
           "애드온 팩 %1$s개, 총 %2$s량"),
 "de_de": ("Zurück",
           "%1$s / %2$s Züge dieses Pakets blockiert",
           "%1$s Addon-Pakete, insgesamt %2$s Züge"),
 "fr_fr": ("Retour",
           "%1$s / %2$s trains de ce pack bloqués",
           "%1$s packs d'addons, %2$s trains au total"),
 "es_es": ("Atrás",
           "Bloqueando %1$s / %2$s trenes de este paquete",
           "%1$s paquetes de addons, %2$s trenes en total"),
 "ru_ru": ("Назад",
           "Заблокировано поездов в этом пакете: %1$s / %2$s",
           "Пакетов аддонов: %1$s, всего поездов: %2$s"),
}

def patch_dir(here):
    n = 0
    for fn in sorted(os.listdir(here)):
        if not fn.endswith(".json"):
            continue
        lang = fn[:-5]
        path = os.path.join(here, fn)
        with io.open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        t = T.get(lang, T["en_us"])
        data[P + "tab_builtin"] = t[0]
        data[P + "tab_addon"] = t[1]
        data[P + "addon_summary"] = t[2]
        data[P + "addon_empty"] = t[3]
        t2 = T2.get(lang, T2["en_us"])
        data[P + "back"] = t2[0]
        data[P + "addon_pack_summary"] = t2[1]
        data[P + "addon_list_summary"] = t2[2]
        with io.open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=True)
            f.write("\n")
        n += 1
    return n

def main():
    print("patched", patch_dir(HERE), "fabric lang files")
    print("patched", patch_dir(HERE_FORGE), "forge lang files")

if __name__ == "__main__":
    main()
