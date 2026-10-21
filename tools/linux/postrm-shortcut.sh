# 卸载时清理桌面图标副本（/usr/share 下的由 dpkg 自己删，不用管）。
remove_dsh_shortcut() {
    for home in /home/*; do
        [ -d "$home" ] || continue
        for cand in "$home/Desktop/dsh-desktop.desktop" "$home/桌面/dsh-desktop.desktop"; do
            if [ -f "$cand" ]; then rm -f "$cand" 2>/dev/null || true; fi
        done
    done
}

remove_dsh_shortcut
