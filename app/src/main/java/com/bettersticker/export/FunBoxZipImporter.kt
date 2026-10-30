package com.bettersticker.export

import com.bettersticker.format.ImageSniffer
import com.bettersticker.shared.LogX
import com.bettersticker.store.FavoriteStore
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 导入 FunBox 导出的表情包 ZIP。
 *
 * FunBox 导出的具体 schema 在不同版本有差异，这里做兼容读取：
 * - 顶层目录名作为分组名；
 * - 根目录图片归到 ZIP 文件名/ manifest 的 name；
 * - 识别 PNG/GIF/WebP/JPEG 和 SILK；
 * - 忽略 __MACOSX、.DS_Store、目录项。
 */
object FunBoxZipImporter {
    data class Result(
        val imported: Int,
        val skipped: Int,
        val groups: Int,
        val error: String? = null,
    ) {
        fun summary(): String =
            if (error != null) "FunBox ZIP 导入失败: $error"
            else "FunBox ZIP 导入：新增 $imported，跳过 $skipped，分组 $groups"
    }

    @JvmStatic
    fun import(store: FavoriteStore, input: InputStream, zipName: String): Result {
        var imported = 0
        var skipped = 0
        val groupIds = HashSet<String>()
        var packTitle: String? = null
        val baseName = zipName.substringBeforeLast('.').ifEmpty { "FunBox 导入" }
        try {
            ZipInputStream(input.buffered()).use { zip ->
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > 2000) break
                    val raw = entry.name.replace('\\', '/')
                    val name = raw.trimStart('/')
                    if (entry.isDirectory || name.isEmpty()) {
                        zip.closeEntry()
                        continue
                    }
                    if (name.contains("__MACOSX/") || name.endsWith(".DS_Store")) {
                        zip.closeEntry()
                        continue
                    }
                    val bytes = readLimited(zip, 64 * 1024 * 1024)
                    zip.closeEntry()
                    val file = name.substringAfterLast('/')
                    // 按 Sticker/Storage/<分组>/ 取分组；兼容根目录直接是分组目录。
                    val folder = com.bettersticker.format.FunBoxGrouping.groupFor(name, baseName)
                    if (file.endsWith(".json", true)) {
                        if (bytes.size < 512 * 1024) {
                            try {
                                val o = JSONObject(String(bytes, Charsets.UTF_8))
                                val title = o.optString("name", "")
                                    .ifEmpty { o.optString("title", "") }
                                    .ifEmpty { o.optString("packName", "") }
                                if (title.isNotEmpty()) packTitle = title
                            } catch (_: Throwable) {
                            }
                        }
                        continue
                    }
                    val fmt = ImageSniffer.detect(bytes)
                    if (!fmt.image && fmt != ImageSniffer.Format.SILK) {
                        skipped++
                        continue
                    }
                    val groupName = if (folder == "FunBox 迁移" && packTitle != null) {
                        packTitle!!
                    } else {
                        folder
                    }
                    val group = store.ensureGroup(groupName)
                    groupIds.add(group.id)
                    try {
                        if (fmt == ImageSniffer.Format.SILK) {
                            store.addVoice(bytes, file, "funbox-zip", 0)
                        } else {
                            store.addSticker(bytes, file, "funbox-zip", 0, 0, group.id)
                        }
                        imported++
                    } catch (t: Throwable) {
                        skipped++
                        LogX.w("FunBox ZIP 条目失败 $name: $t")
                    }
                }
            }
            return Result(imported, skipped, groupIds.size)
        } catch (t: Throwable) {
            LogX.e("FunBox ZIP 读取失败", t)
            return Result(imported, skipped, groupIds.size, t.message)
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > max) throw java.io.IOException("ZIP 条目超过 64MB")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
