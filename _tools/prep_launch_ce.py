import json
import os
import subprocess
import sys

VER = r"D:/PCL-CE/.minecraft/versions/1.20.1-Fabric"
MC = r"D:/PCL-CE/.minecraft"
LIBS = os.path.join(MC, "libraries")
JAVA = r"C:\Program Files\Java\jdk-17\bin\java.exe"

ver = json.load(open(os.path.join(VER, "1.20.1-Fabric.json"), encoding="utf-8"))

cp = [os.path.join(VER, "1.20.1-Fabric.jar")]
missing = []


def maven_path(name):
    """group:artifact:version[:classifier] -> libraries 下的相对路径。"""
    parts = name.split(":")
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier = parts[3] if len(parts) > 3 else None
    fn = artifact + "-" + version
    if classifier:
        fn += "-" + classifier
    fn += ".jar"
    return group.replace(".", "/") + "/" + artifact + "/" + version + "/" + fn


for lib in ver.get("libraries", []):
    dl = lib.get("downloads", {}).get("artifact")
    rel = None
    if dl and dl.get("path"):
        rel = dl["path"]
    elif lib.get("name"):
        rel = maven_path(lib["name"])
    if not rel:
        continue
    p = os.path.join(LIBS, rel.replace("/", os.sep))
    if os.path.exists(p):
        cp.append(p)
    else:
        missing.append(rel)

if missing:
    print("MISSING LIBS:", len(missing))
    for m in missing[:10]:
        print("  ", m)

natives = os.path.join(VER, "1.20.1-Fabric-natives")
print("classpath entries:", len(cp))
print("natives dir exists:", os.path.isdir(natives))

cmd = [
    JAVA,
    "-Xmx6G", "-Xms2G",
    "-Dfile.encoding=UTF-8",
    "-Djava.library.path=" + natives,
    "-Djna.tmpdir=" + natives,
    "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + natives,
    "-Dio.netty.native.workdir=" + natives,
    "-Dminecraft.launcher.brand=PCL2",
    "-Dminecraft.launcher.version=2.9.3",
    "-cp", os.pathsep.join(cp),
    ver["mainClass"],
    "--username", "TestUser",
    "--version", "1.20.1-Fabric",
    "--gameDir", VER,
    "--assetsDir", os.path.join(MC, "assets"),
    "--assetIndex", "5",
    "--uuid", "00000000000000000000000000000000",
    "--accessToken", "0",
    "--clientId", "0",
    "--xuid", "0",
    "--userType", "legacy",
    "--versionType", "release",
    "--width", "1082",
    "--height", "650",
]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

with open(os.path.join(ROOT, "_launch_cmd.json"), "w", encoding="utf-8") as f:
    json.dump(cmd, f, ensure_ascii=False, indent=1)

print("cmd written to", os.path.join(ROOT, "_launch_cmd.json"))
print("mainClass:", ver["mainClass"])
