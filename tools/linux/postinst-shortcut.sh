# /opt/dsh-desktop 安装完成后补桌面入口的部分。
# 由 tools/package.sh 拼进 deb 的 postinst 末尾（jpackage 生成的脚本在前，不要动它）。
#
# 为什么需要：jpackage 会生成一个 .desktop，但只放在 /opt/dsh-desktop/lib 下，
# **没有装进 /usr/share/applications**，于是菜单里查不到、桌面也没有图标。
# 真机 2026-10-21 报「安装无桌面快捷方式」就是这个原因。

install_dsh_shortcut() {
    # 1) 菜单项（/usr/share/applications 里的那份，安装时就在包里，这里只做存在性兜底）
    if [ -f /usr/share/applications/dsh-desktop.desktop ]; then
        update-desktop-database /usr/share/applications >/dev/null 2>&1 || true
        gtk-update-icon-cache -q -t /usr/share/icons/hicolor >/dev/null 2>&1 || true
    fi

    # 2) 桌面图标：UOS/Deepin 的桌面图标是独立的一份拷贝，必须按用户落到 ~/Desktop。
    #    安装时常常没有图形会话，目录不存在就跳过——应用首次启动会再补一次。
    for home in /home/*; do
        [ -d "$home" ] || continue
        desktop_dir=""
        for cand in "$home/Desktop" "$home/桌面"; do
            if [ -d "$cand" ]; then desktop_dir="$cand"; break; fi
        done
        [ -n "$desktop_dir" ] || continue
        target="$desktop_dir/dsh-desktop.desktop"
        # 不能写成 `[ -e "$t" ] && continue`：条件为假时整条返回 1，
        # 在 set -e 下会直接把 postinst 掐断（dpkg 报安装失败）。
        if [ -e "$target" ]; then continue; fi
        cp /usr/share/applications/dsh-desktop.desktop "$target" 2>/dev/null || continue
        chmod 755 "$target" 2>/dev/null || true
        owner=$(stat -c '%U:%G' "$home" 2>/dev/null || true)
        if [ -n "$owner" ]; then chown "$owner" "$target" 2>/dev/null || true; fi
        # 不标记 trusted 的话，UOS 上双击会弹「不信任的启动器」而不是直接运行
        user=$(basename "$home")
        su -s /bin/sh "$user" -c "gio set '$target' metadata::trusted true" >/dev/null 2>&1 || true
    done
}

install_dsh_shortcut
