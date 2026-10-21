import urllib.request, os

os.chdir("/media/wangke/OFFICE/workspace/2026-10-21-19-27-23/dsh-desktop")

DEPS = [
    ("androidx.annotation", "annotation-jvm", "1.8.0"),
    ("androidx.collection", "collection-jvm", "1.4.0"),
    ("androidx.lifecycle", "lifecycle-common-jvm", "2.8.5"),
    ("androidx.lifecycle", "lifecycle-runtime-desktop", "2.8.5"),
    ("androidx.lifecycle", "lifecycle-viewmodel-desktop", "2.8.5"),
    ("org.jetbrains.androidx.lifecycle", "lifecycle-common", "2.8.4"),
    ("org.jetbrains.androidx.lifecycle", "lifecycle-runtime", "2.8.4"),
    ("org.jetbrains.androidx.lifecycle", "lifecycle-runtime-compose", "2.8.4"),
    ("org.jetbrains.androidx.lifecycle", "lifecycle-runtime-compose-desktop", "2.8.4"),
    ("org.jetbrains.androidx.lifecycle", "lifecycle-viewmodel", "2.8.4"),
    ("org.jetbrains.kotlin", "kotlin-stdlib-common", "1.9.24"),
    ("org.jetbrains.kotlin", "kotlin-stdlib-jdk8", "1.9.24"),
    ("org.jetbrains.kotlinx", "atomicfu-jvm", "0.23.2"),
    ("org.jetbrains.kotlinx", "kotlinx-datetime-jvm", "0.6.0"),
    ("androidx.arch.core", "core-runtime", "2.2.0"),
    ("androidx.arch.core", "core-common", "2.2.0"),
]

BASES = [
    "https://repo1.maven.org/maven2",
    "https://maven.aliyun.com/repository/public",
    "https://maven.aliyun.com/repository/google",
]

for key in ("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY"):
    os.environ.pop(key, None)

ok = 0
for g, a, v in DEPS:
    dest = f"lib/{a}-{v}.jar"
    if os.path.exists(dest) and os.path.getsize(dest) > 5000:
        ok += 1
        continue
    path = f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.jar"
    for base in BASES:
        try:
            request = urllib.request.Request(base + "/" + path, headers={"User-Agent": "curl/8"})
            data = urllib.request.urlopen(request, timeout=30).read()
            if len(data) > 3000 and data[:2] == b"PK":
                open(dest, "wb").write(data)
                print("OK  ", a, v, len(data))
                ok += 1
                break
        except Exception:
            continue
    else:
        print("MISS", a, v)

print(f"就绪 {ok}/{len(DEPS)}")
