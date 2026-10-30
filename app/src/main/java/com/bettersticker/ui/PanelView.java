package com.bettersticker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.bettersticker.export.ExportService;
import com.bettersticker.export.GroupExporter;
import com.bettersticker.format.ImageSniffer;
import com.bettersticker.format.SilkDecoders;
import com.bettersticker.shared.LogX;
import com.bettersticker.store.FavoriteItem;
import com.bettersticker.store.FavoriteGroup;
import com.bettersticker.store.FavoriteStore;

/**
 * 自绘面板：不复制 FunBox 的任何图片/布局资源。
 * 深色半透明、双 Tab、网格、空态、导出按钮。
 */
public final class PanelView extends LinearLayout {

    public interface Callback {
        void onClose();
    }

    private final Activity activity;
    private final Callback callback;
    private final List<FavoriteItem> shown = new ArrayList<>();

    private String tab = FavoriteItem.TYPE_STICKER;
    private String selectedGroup = null;
    private GridView grid;
    private TextView emptyTitle;
    private TextView emptyBody;
    private TextView status;
    private TextView tabSticker;
    private TextView tabVoice;
    private TextView exportBtn;
    private HorizontalScrollView groupScroll;
    private LinearLayout groupBar;
    private ItemAdapter adapter;
    private boolean exporting;

    public PanelView(Activity activity, String initialTab, Callback callback) {
        super(activity);
        this.activity = activity;
        this.callback = callback;
        if (FavoriteItem.TYPE_VOICE.equals(initialTab)) {
            tab = FavoriteItem.TYPE_VOICE;
        }
        setOrientation(VERTICAL);
        setPadding(Ui.dp(activity, 20), Ui.dp(activity, 18), Ui.dp(activity, 20), Ui.dp(activity, 16));
        setBackground(Ui.rounded(Ui.BG_PANEL, 28, activity, 0x33FFFFFF, 1));
        setElevation(Ui.dp(activity, 12));
        build();
        refresh();
    }

    private void build() {
        // 标题
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(VERTICAL);
        TextView title = Ui.text(activity, "BetterSticker", 22, Ui.TEXT_PRIMARY, true);
        TextView subtitle = Ui.text(activity, "QQ 表情 / 语音收藏  ·  不联网", 11, Ui.TEXT_SECONDARY, false);
        subtitle.setPadding(0, Ui.dp(activity, 3), 0, 0);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = Ui.text(activity, "关闭", 13, Ui.TEXT_SECONDARY, false);
        close.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 9), Ui.dp(activity, 6), Ui.dp(activity, 9));
        close.setBackground(Ui.rounded(0x1AFFFFFF, 20, activity, 0, 0));
        close.setOnClickListener(v -> callback.onClose());
        header.addView(close);
        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Tab
        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(HORIZONTAL);
        tabs.setPadding(0, Ui.dp(activity, 12), 0, 0);
        tabSticker = Ui.text(activity, "表情", 14, Ui.TEXT_PRIMARY, true);
        tabVoice = Ui.text(activity, "语音", 14, Ui.TEXT_SECONDARY, false);
        tabSticker.setOnClickListener(v -> switchTab(FavoriteItem.TYPE_STICKER));
        tabVoice.setOnClickListener(v -> switchTab(FavoriteItem.TYPE_VOICE));
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tabLp.setMargins(0, 0, Ui.dp(activity, 8), 0);
        tabs.addView(tabSticker, tabLp);
        tabs.addView(tabVoice, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 表情包分组
        groupScroll = new HorizontalScrollView(activity);
        groupScroll.setHorizontalScrollBarEnabled(false);
        groupBar = new LinearLayout(activity);
        groupBar.setOrientation(LinearLayout.HORIZONTAL);
        groupBar.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 2));
        groupScroll.addView(groupBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(groupScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 状态 + 导入入口
        LinearLayout statusRow = new LinearLayout(activity);
        statusRow.setOrientation(HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 6));
        status = Ui.text(activity, "", 11, Ui.TEXT_SECONDARY, false);
        statusRow.addView(status, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView importFunbox = Ui.text(activity, "导入", 11, Ui.ACCENT, true);
        importFunbox.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 4),
                Ui.dp(activity, 2), Ui.dp(activity, 4));
        importFunbox.setOnClickListener(v -> showImportMenu());
        statusRow.addView(importFunbox);
        addView(statusRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 网格
        FrameLayout content = new FrameLayout(activity);
        grid = new GridView(activity);
        grid.setNumColumns(4);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Ui.dp(activity, 7));
        grid.setVerticalSpacing(Ui.dp(activity, 7));
        grid.setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 4));
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        adapter = new ItemAdapter();
        grid.setAdapter(adapter);

        LinearLayout empty = buildEmpty();
        content.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        grid.setEmptyView(empty);

        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        int gridH = Math.min(Ui.dp(activity, 360), Math.max(Ui.dp(activity, 220),
                screenH - Ui.dp(activity, 300)));
        addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gridH));

        // 底部按钮
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, Ui.dp(activity, 12), 0, 0);

        exportBtn = Ui.button(activity, "导出全部", true);
        exportBtn.setOnClickListener(v -> doExport());
        footer.addView(exportBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));

        TextView groupExport = Ui.button(activity, "导出本组", false);
        groupExport.setOnClickListener(v -> {
            if (selectedGroup == null) {
                Toast.makeText(activity, "先在分组条选择一个分组", Toast.LENGTH_SHORT).show();
            } else {
                exportGroup(selectedGroup);
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        glp.setMargins(Ui.dp(activity, 8), 0, 0, 0);
        footer.addView(groupExport, glp);

        TextView refresh = Ui.button(activity, "刷新", false);
        refresh.setOnClickListener(v -> refresh());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rlp.setMargins(Ui.dp(activity, 8), 0, 0, 0);
        footer.addView(refresh, rlp);

        TextView close2 = Ui.button(activity, "关闭", false);
        close2.setOnClickListener(v -> callback.onClose());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMargins(Ui.dp(activity, 8), 0, 0, 0);
        footer.addView(close2, clp);
        addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout buildEmpty() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 20),
                Ui.dp(activity, 20), Ui.dp(activity, 20));
        emptyTitle = Ui.text(activity, "还没有收藏", 18, Ui.TEXT_PRIMARY, true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyBody = Ui.text(activity, "", 12, Ui.TEXT_SECONDARY, false);
        emptyBody.setGravity(Gravity.CENTER);
        emptyBody.setLineSpacing(Ui.dp(activity, 3), 1f);
        emptyBody.setPadding(0, Ui.dp(activity, 10), 0, 0);
        box.addView(emptyTitle);
        box.addView(emptyBody);
        return box;
    }

    public void switchTab(String newTab) {
        if (newTab.equals(tab)) {
            return;
        }
        tab = newTab;
        refresh();
    }

    public void refresh() {
        try {
            FavoriteStore store = FavoriteStore.get();
            rebuildGroupBar(store);
            List<FavoriteItem> all = store.items();
            shown.clear();
            for (FavoriteItem it : all) {
                if (!tab.equals(it.type)) {
                    continue;
                }
                if (FavoriteItem.TYPE_STICKER.equals(tab) && selectedGroup != null) {
                    if (FavoriteGroup.FAVORITES_ID.equals(selectedGroup)) {
                        if (!it.favorite) {
                            continue;
                        }
                    } else if (!selectedGroup.equals(it.groupId)) {
                        continue;
                    }
                }
                shown.add(it);
            }
            adapter.notifyDataSetChanged();
            int s = store.count(FavoriteItem.TYPE_STICKER);
            int v = store.count(FavoriteItem.TYPE_VOICE);
            int fav = store.itemsInGroup(FavoriteGroup.FAVORITES_ID).size();
            status.setText("已收藏 " + s + " 表情 · " + v + " 语音 · " + fav + " 收藏项"
                    + "   |   SILK：" + (SilkDecoders.isAvailable() ? "可用" : "未配置"));
            updateTabStyle();
            updateEmpty();
        } catch (Throwable t) {
            status.setText("读取收藏失败: " + t.getMessage());
            LogX.e("面板刷新失败", t);
        }
    }

    private void rebuildGroupBar(FavoriteStore store) {
        if (groupBar == null) {
            return;
        }
        groupScroll.setVisibility(FavoriteItem.TYPE_STICKER.equals(tab)
                ? View.VISIBLE : View.GONE);
        groupBar.removeAllViews();
        addGroupChip("全部", null, false, store);
        for (FavoriteGroup g : store.groups()) {
            addGroupChip(g.name, g.id, g.favorite, store);
        }
        TextView add = Ui.text(activity, "＋", 13, Ui.ACCENT, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8),
                Ui.dp(activity, 12), Ui.dp(activity, 8));
        add.setBackground(Ui.rounded(Ui.BG_CARD, 20, activity, 0, 0));
        add.setOnClickListener(v -> newGroupDialog());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(activity, 6), 0, 0, 0);
        groupBar.addView(add, lp);
    }

    private void addGroupChip(String name, String id, boolean favorite, FavoriteStore store) {
        boolean selected = id == null ? selectedGroup == null : id.equals(selectedGroup);
        TextView chip = Ui.text(activity, name, 12,
                selected ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8),
                Ui.dp(activity, 14), Ui.dp(activity, 8));
        chip.setBackground(Ui.rounded(selected ? Ui.BG_CARD_SELECTED : Ui.BG_CARD,
                20, activity, 0, 0));
        chip.setOnClickListener(v -> {
            selectedGroup = id;
            refresh();
        });
        chip.setOnLongClickListener(v -> {
            groupMenu(id, favorite, store);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, Ui.dp(activity, 6), 0);
        groupBar.addView(chip, lp);
    }

    private void groupMenu(String id, boolean favorite, FavoriteStore store) {
        if (id == null) {
            Toast.makeText(activity, "长按具体分组可以收藏/导出该分组", Toast.LENGTH_SHORT).show();
            return;
        }
        FavoriteGroup g = store.groupById(id);
        List<String> options = new ArrayList<>();
        if (!favorite) {
            options.add("收藏整组到“收藏”");
        }
        options.add("导出该组 ZIP");
        if (!favorite && (g == null || !g.builtin)) {
            options.add("重命名");
            options.add("删除分组");
        }
        Md3Dialog.showList(activity, g == null ? "分组" : g.name,
                options.toArray(new String[0]), which -> {
                    String opt = options.get(which);
                    if (opt.startsWith("收藏整组")) {
                        int n = store.markGroupFavorite(id);
                        Toast.makeText(activity, "已收藏 " + n + " 项到“收藏”分组",
                                Toast.LENGTH_LONG).show();
                        refresh();
                    } else if (opt.startsWith("导出该组")) {
                        exportGroup(id);
                    } else if (opt.equals("重命名")) {
                        renameGroupDialog(g);
                    } else if (opt.equals("删除分组")) {
                        confirmDeleteGroup(g);
                    }
                });
    }

    private void exportGroup(String id) {
        Toast.makeText(activity, "正在打包分组…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            GroupExporter.Result r = GroupExporter.export(activity, id);
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, r.getMessage(), Toast.LENGTH_LONG).show();
                status.setText(r.getMessage());
            });
        }, "BetterSticker-group-export").start();
    }

    private void newGroupDialog() {
        Md3Dialog.showInput(activity, "新建表情包分组", null, "", "创建", name -> {
            if (name.isEmpty()) {
                Toast.makeText(activity, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            FavoriteGroup g = FavoriteStore.get().ensureGroup(name);
            selectedGroup = g.id;
            refresh();
        }, null);
    }

    private void renameGroupDialog(FavoriteGroup g) {
        if (g == null) {
            return;
        }
        Md3Dialog.showInput(activity, "重命名分组", null, g.name, "保存", name -> {
            FavoriteStore.get().renameGroup(g.id, name);
            refresh();
        }, null);
    }

    private void confirmDeleteGroup(FavoriteGroup g) {
        if (g == null) {
            return;
        }
        Md3Dialog.confirm(activity, "删除分组",
                "删除“" + g.name + "”？组内表情会移到“默认”分组，不会删除文件。",
                "删除", () -> {
                    FavoriteStore.get().deleteGroup(g.id);
                    if (g.id.equals(selectedGroup)) {
                        selectedGroup = null;
                    }
                    refresh();
                });
    }

    private void itemMenu(FavoriteItem item) {
        List<String> options = new ArrayList<>();
        options.add("预览");
        options.add(item.favorite ? "取消收藏" : "收藏到“收藏”分组");
        if (item.isSticker()) {
            options.add("移动到分组…");
        }
        options.add("删除");
        Md3Dialog.showList(activity, item.originalName,
                options.toArray(new String[0]), which -> {
                    String opt = options.get(which);
                    if (opt.equals("预览")) {
                        preview(item);
                    } else if (opt.startsWith("收藏")) {
                        FavoriteStore.get().setFavorite(item.id, true);
                        Toast.makeText(activity, "已收藏到“收藏”分组",
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    } else if (opt.startsWith("取消收藏")) {
                        FavoriteStore.get().setFavorite(item.id, false);
                        refresh();
                    } else if (opt.startsWith("移动到分组")) {
                        moveGroupDialog(item);
                    } else if (opt.equals("删除")) {
                        confirmDelete(item);
                    }
                });
    }

    private void moveGroupDialog(FavoriteItem item) {
        List<FavoriteGroup> groups = new ArrayList<>();
        for (FavoriteGroup g : FavoriteStore.get().groups()) {
            if (!g.favorite) {
                groups.add(g);
            }
        }
        String[] names = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            names[i] = groups.get(i).name;
        }
        Md3Dialog.showList(activity, "移动到分组", names, which -> {
            FavoriteStore.get().moveToGroup(item.id, groups.get(which).id);
            selectedGroup = groups.get(which).id;
            refresh();
        });
    }

    private void updateTabStyle() {
        boolean sticker = FavoriteItem.TYPE_STICKER.equals(tab);
        tabSticker.setTextColor(sticker ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY);
        tabVoice.setTextColor(!sticker ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY);
        tabSticker.setBackground(Ui.rounded(sticker ? Ui.BG_CARD_SELECTED : Color.TRANSPARENT,
                20, activity, 0, 0));
        tabVoice.setBackground(Ui.rounded(!sticker ? Ui.BG_CARD_SELECTED : Color.TRANSPARENT,
                20, activity, 0, 0));
        tabSticker.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 9),
                Ui.dp(activity, 18), Ui.dp(activity, 9));
        tabVoice.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 9),
                Ui.dp(activity, 18), Ui.dp(activity, 9));
    }

    private void updateEmpty() {
        emptyTitle.setText("还没有收藏");
        if (FavoriteItem.TYPE_VOICE.equals(tab)) {
            emptyBody.setText("长按聊天输入区的语音图标打开面板；\n"
                    + "长按语音消息，点“收藏到 BetterSticker”。\n"
                    + "SILK→WAV 解码器尚未配置，当前只保存原始 SILK。");
        } else {
            emptyBody.setText("长按聊天输入区的表情图标打开面板；\n"
                    + "“导入”支持 FunBox ZIP / 本地图片；\n"
                    + "长按表情可收藏到“收藏”分组。");
        }
    }

    private void showImportMenu() {
        String[] items = {
                "从 FunBox ZIP 导入",
                "从 FunBox 迁移（原版素材目录）",
                "从本地图片导入",
                "新建表情包分组"
        };
        Md3Dialog.showList(activity, "导入 / 分组", items, which -> {
            if (which == 0) {
                com.bettersticker.imports.LocalImport.pickZip(activity);
            } else if (which == 1) {
                migrateFunBoxDir();
            } else if (which == 2) {
                com.bettersticker.imports.LocalImport.pickImages(activity);
            } else {
                newGroupDialog();
            }
        });
    }

    /**
     * 从 FunBox 迁移：默认先看 QQ 的 Android/media/.../.fun，
     * 也可以手动输入绝对路径或通过系统目录选择器选择。
     */
    private void migrateFunBoxDir() {
        final String defaultPath =
                "/sdcard/Android/media/com.tencent.mobileqq/.fun";
        File def = new File(defaultPath);
        String tip = def.isDirectory()
                ? "已检测到默认目录：\n" + defaultPath
                : "默认目录当前不存在：\n" + defaultPath;
        Md3Dialog.showInput(activity, "从 FunBox 迁移",
                tip + "\n\n可以直接输入绝对路径，或点“选择目录”。",
                defaultPath, "开始迁移", path -> {
                    migrateFunBoxPath(path.isEmpty() ? defaultPath : path);
                }, () -> com.bettersticker.imports.LocalImport.pickMigrationDir(activity));
    }

    private void migrateFunBoxPath(String path) {
        File root = new File(path);
        if (!root.isDirectory()) {
            Toast.makeText(activity, "目录不存在或不可读:\n" + path,
                    Toast.LENGTH_LONG).show();
            return;
        }
        status.setText("正在从 FunBox 迁移 " + root.getAbsolutePath() + " …");
        new Thread(() -> {
            int ok = 0;
            int skipped = 0;
            String error = null;
            java.util.Set<String> groupNames = new java.util.HashSet<>();
            try {
                com.bettersticker.format.FunBoxMaterialReader reader =
                        new com.bettersticker.format.FunBoxMaterialReader(root);
                List<com.bettersticker.format.StoredAsset> assets = reader.list();
                FavoriteStore store = FavoriteStore.get();
                for (com.bettersticker.format.StoredAsset a : assets) {
                    try {
                        // FunBox 原始目录的第一级目录名就是分组名；根目录文件归“FunBox 迁移”。
                        String groupName = "FunBox 迁移";
                        String rel = a.relativePath == null ? "" : a.relativePath;
                        groupName = com.bettersticker.format.FunBoxGrouping.groupFor(
                                rel, root.getName());
                        FavoriteGroup group = store.ensureGroup(groupName);
                        groupNames.add(group.name);
                        byte[] data = reader.read(a);
                        if (a.format == ImageSniffer.Format.SILK) {
                            store.addVoice(data, a.name, a.source, 0);
                        } else {
                            store.addSticker(data, a.name, a.source, 0, 0, group.id);
                        }
                        ok++;
                    } catch (Throwable t) {
                        skipped++;
                        LogX.e("FunBox 迁移失败: " + a.name, t);
                    }
                }
            } catch (Throwable t) {
                error = t.getMessage();
                LogX.e("扫描 FunBox 素材失败", t);
            }
            final int okF = ok;
            final int skipF = skipped;
            final int groupCount = groupNames.size();
            final String errF = error;
            activity.runOnUiThread(() -> {
                String msg = errF != null ? "FunBox 迁移失败: " + errF
                        : "FunBox 迁移：成功 " + okF + "，跳过 " + skipF
                        + "，按原目录创建 " + groupCount + " 个分组";
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                status.setText(msg);
                refresh();
            });
        }, "BetterSticker-funbox-migrate").start();
    }

    private void doExport() {
        if (exporting) {
            return;
        }
        if (shown.isEmpty() && FavoriteStore.get().count(null) == 0) {
            Toast.makeText(activity, "还没有可导出的收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        exporting = true;
        exportBtn.setAlpha(0.6f);
        exportBtn.setText("导出中…");
        status.setText("正在导出到 Download/BetterSticker…");
        new Thread(() -> {
            ExportService.Result result = new ExportService(activity).exportAll(
                    (done, total, current) -> activity.runOnUiThread(() ->
                            status.setText("导出中 " + done + "/" + total + "  " + safe(current))));
            activity.runOnUiThread(() -> {
                exporting = false;
                exportBtn.setAlpha(1f);
                exportBtn.setText("导出全部");
                String msg = result.summary();
                if (!result.errors.isEmpty()) {
                    msg += "\n" + result.errors.get(0);
                }
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                refresh();
                status.setText(msg);
            });
        }, "BetterSticker-export").start();
    }

    private void sendSticker(FavoriteItem item) {
        File f = FavoriteStore.get().fileOf(item);
        boolean ok = com.bettersticker.qq.StickerSender.send(activity, f);
        if (ok) {
            callback.onClose();
        }
    }

    private void preview(FavoriteItem item) {
        try {
            Dialog d = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout box = new LinearLayout(activity);
            box.setOrientation(VERTICAL);
            box.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 16),
                    Ui.dp(activity, 18), Ui.dp(activity, 16));
            box.setBackground(Ui.rounded(Ui.BG_PANEL, 18, activity, 0x33FFFFFF, 1));

            TextView title = Ui.text(activity, item.originalName, 16, Ui.TEXT_PRIMARY, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            box.addView(title);

            if (item.isSticker()) {
                ImageView iv = new ImageView(activity);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setAdjustViewBounds(true);
                File f = FavoriteStore.get().fileOf(item);
                Drawable drawable = f == null ? null : ThumbnailLoader.decodePreview(f, 900);
                iv.setImageDrawable(drawable);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 280));
                ilp.setMargins(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 6));
                box.addView(iv, ilp);
            } else {
                TextView voice = Ui.text(activity, "♪  SILK v3 语音", 26, Ui.ACCENT, true);
                voice.setGravity(Gravity.CENTER);
                voice.setPadding(0, Ui.dp(activity, 30), 0, Ui.dp(activity, 30));
                box.addView(voice);
                TextView note = Ui.text(activity,
                        "SILK 解码器尚未配置：当前可保存原始 SILK，\n暂不能播放或导出 WAV。",
                        12, Ui.TEXT_SECONDARY, false);
                note.setGravity(Gravity.CENTER);
                box.addView(note);
            }

            TextView meta = Ui.text(activity, metadata(item), 11, Ui.TEXT_SECONDARY, false);
            meta.setLineSpacing(Ui.dp(activity, 2), 1f);
            meta.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 8));
            box.addView(meta);

            TextView close = Ui.button(activity, "关闭", true);
            close.setOnClickListener(v -> d.dismiss());
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, Ui.dp(activity, 10), 0, 0);
            box.addView(close, clp);

            d.setContentView(box);
            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.CENTER);
                w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setDimAmount(0.45f);
            }
            d.show();
        } catch (Throwable t) {
            LogX.e("预览失败", t);
            Toast.makeText(activity, "预览失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(FavoriteItem item) {
        Md3Dialog.confirm(activity, "删除收藏",
                "确定删除 " + item.originalName + " ？\n文件会从模块数据目录移除，已导出的文件不受影响。",
                "删除", () -> {
                    FavoriteStore.get().delete(item.id);
                    refresh();
                    Toast.makeText(activity, "已删除", Toast.LENGTH_SHORT).show();
                });
    }

    private static String metadata(FavoriteItem item) {
        String time = item.savedAtIso;
        if (time == null || time.isEmpty()) {
            time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(item.savedAt));
        }
        return "类型: " + item.type
                + "    大小: " + human(item.size)
                + "\n来源: " + (item.source == null || item.source.isEmpty() ? "未记录（待真机适配）" : item.source)
                + "\n保存: " + time
                + "\nSHA256: " + shortHash(item.sha256);
    }

    private static String shortHash(String s) {
        if (s == null) {
            return "-";
        }
        return s.length() > 16 ? s.substring(0, 16) + "…" : s;
    }

    private static String human(long n) {
        if (n < 1024) {
            return n + " B";
        }
        if (n < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", n / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", n / (1024.0 * 1024.0));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private String badge(FavoriteItem item) {
        if (item.isVoice()) {
            return "SILK";
        }
        ImageSniffer.Format f = ImageSniffer.Format.UNKNOWN;
        String mime = item.mime == null ? "" : item.mime.toLowerCase(Locale.US);
        if (mime.contains("gif")) {
            f = ImageSniffer.Format.GIF;
        } else if (mime.contains("webp")) {
            f = ImageSniffer.Format.WEBP;
        } else if (mime.contains("png")) {
            f = ImageSniffer.Format.PNG;
        } else if (mime.contains("jpeg") || mime.contains("jpg")) {
            f = ImageSniffer.Format.JPEG;
        }
        return f.extension.toUpperCase(Locale.US);
    }

    private final class ItemAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            FavoriteItem it = shown.get(position);
            return it.id == null ? position : it.id.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FavoriteItem item = shown.get(position);
            SquareLayout box = new SquareLayout(activity);
            box.setBackground(Ui.rounded(Ui.BG_CARD, 16, activity, 0, 0));
            box.setPadding(Ui.dp(activity, 5), Ui.dp(activity, 5),
                    Ui.dp(activity, 5), Ui.dp(activity, 5));

            if (item.isSticker()) {
                ImageView iv = new ImageView(activity);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setTag("bs_own");
                box.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                File f = FavoriteStore.get().fileOf(item);
                ThumbnailLoader.load(f, 220, iv);
            } else {
                LinearLayout voice = new LinearLayout(activity);
                voice.setOrientation(VERTICAL);
                voice.setGravity(Gravity.CENTER);
                TextView icon = Ui.text(activity, "♪", 26, Ui.ACCENT, true);
                TextView label = Ui.text(activity, "语音", 11, Ui.TEXT_SECONDARY, false);
                voice.addView(icon);
                voice.addView(label);
                box.addView(voice, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            TextView badge = Ui.text(activity, badge(item), 9, Ui.TEXT_SECONDARY, true);
            badge.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 2),
                    Ui.dp(activity, 4), Ui.dp(activity, 2));
            badge.setBackground(Ui.rounded(0xB3000000, 6, activity, 0, 0));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.gravity = Gravity.TOP | Gravity.START;
            box.addView(badge, blp);

            box.setOnClickListener(v -> {
                if (item.isSticker()) {
                    sendSticker(item);
                } else {
                    preview(item);
                }
            });
            box.setOnLongClickListener(v -> {
                itemMenu(item);
                return true;
            });
            return box;
        }
    }

    /** 正方形网格单元。 */
    private static final class SquareLayout extends FrameLayout {
        SquareLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY));
        }
    }
}
