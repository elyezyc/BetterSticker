package com.bettersticker.imports

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.bettersticker.export.FunBoxZipImporter
import com.bettersticker.shared.LogX
import com.bettersticker.store.FavoriteStore
import com.bettersticker.ui.PanelController
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** 通过系统文件选择器导入 FunBox ZIP / 本地图片。 */
object LocalImport {
    const val REQ_ZIP = 0x4A01
    const val REQ_IMAGES = 0x4A02
    const val REQ_TREE = 0x4A03

    @JvmStatic
    fun pickZip(activity: Activity) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip", "application/x-zip-compressed", "application/octet-stream"
            ))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivityForResult(i, REQ_ZIP)
    }

    @JvmStatic
    fun pickImages(activity: Activity) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivityForResult(i, REQ_IMAGES)
    }

    @JvmStatic
    fun pickMigrationDir(activity: Activity) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        activity.startActivityForResult(i, REQ_TREE)
    }

    @JvmStatic
    fun onActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
        if (activity == null || resultCode != Activity.RESULT_OK || data == null) return
        if (requestCode == REQ_TREE) {
            val uri = data.data ?: return
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {
            }
            importTree(activity, uri)
            return
        }
        if (requestCode != REQ_ZIP && requestCode != REQ_IMAGES) return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { uris.add(it) }
            }
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) {
            Toast.makeText(activity, "没有选择文件", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, "正在导入…", Toast.LENGTH_SHORT).show()
        Thread({
            var msg: String
            try {
                val store = FavoriteStore.get()
                if (requestCode == REQ_ZIP) {
                    val uri = uris[0]
                    val name = displayName(activity, uri)
                    val input = activity.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("无法打开 ZIP")
                    val r = input.use { FunBoxZipImporter.import(store, it, name) }
                    msg = r.summary()
                } else {
                    val group = store.ensureGroup("本地导入")
                    var ok = 0
                    var skip = 0
                    for (uri in uris) {
                        try {
                            val bytes = read(activity, uri)
                            val name = displayName(activity, uri)
                            store.addSticker(bytes, name, "local-import", 0, 0, group.id)
                            ok++
                        } catch (t: Throwable) {
                            skip++
                            LogX.w("本地图片导入失败 $uri: $t")
                        }
                    }
                    msg = "本地导入：新增 $ok，跳过 $skip（分组：${group.name}）"
                }
            } catch (t: Throwable) {
                msg = "导入失败: ${t.message}"
                LogX.e("本地导入失败", t)
            }
            activity.runOnUiThread {
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                PanelController.refreshIfOpen()
            }
        }, "BetterSticker-local-import").start()
    }

    /** 通过 SAF 目录树导入 FunBox 素材（不依赖绝对路径权限）。 */
    private fun importTree(activity: Activity, treeUri: Uri) {
        Toast.makeText(activity, "正在扫描选择的目录…", Toast.LENGTH_SHORT).show()
        Thread({
            var err: String? = null
            val counts = intArrayOf(0, 0)
            val groupNames = HashSet<String>()
            try {
                val store = FavoriteStore.get()
                val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                walkTree(activity, treeUri, rootId, store, "", "", 0, counts, groupNames)
            } catch (t: Throwable) {
                err = t.message
                LogX.e("SAF 目录导入失败", t)
            }
            val msg = if (err != null) "目录导入失败: $err"
            else "FunBox 迁移：新增 ${counts[0]}，跳过 ${counts[1]}，按原目录创建 ${groupNames.size} 个分组"
            activity.runOnUiThread {
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                PanelController.refreshIfOpen()
            }
        }, "BetterSticker-tree-import").start()
    }

    private fun walkTree(
        activity: Activity,
        treeUri: Uri,
        docId: String,
        store: FavoriteStore,
        groupName: String,
        relPath: String,
        depth: Int,
        counts: IntArray,
        groupNames: MutableSet<String>,
    ) {
        if (depth > 6) return
        try {
            val children = android.provider.DocumentsContract
                .buildChildDocumentsUriUsingTree(treeUri, docId)
            activity.contentResolver.query(
                children,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: id
                    val mime = c.getString(2) ?: ""
                    if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childRel = if (relPath.isEmpty()) name else "$relPath/$name"
                        walkTree(activity, treeUri, id, store, groupName, childRel,
                            depth + 1, counts, groupNames)
                        continue
                    }
                    try {
                        val docUri = android.provider.DocumentsContract
                            .buildDocumentUriUsingTree(treeUri, id)
                        val bytes = activity.contentResolver.openInputStream(docUri)?.use {
                            it.readBytes(64 * 1024 * 1024)
                        } ?: throw java.io.IOException("无法读取 $name")
                        val fmt = com.bettersticker.format.ImageSniffer.detect(bytes)
                        if (!fmt.image && fmt != com.bettersticker.format.ImageSniffer.Format.SILK) {
                            counts[1]++
                            continue
                        }
                        val fileRel = if (relPath.isEmpty()) name else "$relPath/$name"
                        val effectiveGroup = com.bettersticker.format.FunBoxGrouping.groupFor(
                            fileRel, "FunBox")
                        val group = store.ensureGroup(effectiveGroup)
                        groupNames.add(group.name)
                        if (fmt == com.bettersticker.format.ImageSniffer.Format.SILK) {
                            store.addVoice(bytes, name, "FunBox(SAF)", 0)
                        } else {
                            store.addSticker(bytes, name, "FunBox(SAF)", 0, 0, group.id)
                        }
                        counts[0]++
                    } catch (t: Throwable) {
                        counts[1]++
                        LogX.w("SAF 文件导入失败 $name: $t")
                    }
                }
            }
        } catch (t: Throwable) {
            LogX.w("SAF 目录遍历失败: $t")
        }
    }

    private fun InputStream.readBytes(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n <= 0) break
            total += n
            if (total > max) throw java.io.IOException("文件超过 64MB")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun displayName(activity: Activity, uri: Uri): String {
        try {
            activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val s = c.getString(0)
                    if (!s.isNullOrEmpty()) return s
                }
            }
        } catch (_: Throwable) {
        }
        return uri.lastPathSegment ?: "import.bin"
    }

    private fun read(activity: Activity, uri: Uri): ByteArray {
        val input: InputStream = activity.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("无法打开文件")
        return input.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                total += n
                if (total > 64 * 1024 * 1024) throw java.io.IOException("文件超过 64MB")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }
}
