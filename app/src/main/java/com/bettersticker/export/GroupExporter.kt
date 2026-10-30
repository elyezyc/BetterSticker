package com.bettersticker.export

import android.content.Context
import com.bettersticker.store.FavoriteGroup
import com.bettersticker.store.FavoriteItem
import com.bettersticker.store.FavoriteStore
import com.bettersticker.shared.LogX
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 按分组导出成 ZIP（图片原格式，语音保留 SILK）。 */
object GroupExporter {
    data class Result(
        val ok: Boolean,
        val message: String,
    )

    @JvmStatic
    fun export(context: Context, groupId: String?): Result {
        return try {
            val store = FavoriteStore.get()
            val group: FavoriteGroup? = groupId?.let { store.groupById(it) }
            val items: List<FavoriteItem> = store.itemsInGroup(groupId)
            if (items.isEmpty()) {
                return Result(false, "这个分组还没有可导出的内容")
            }
            val groupName = group?.name ?: "全部"
            val bytes = buildZip(store, groupName, items)
            val safe = MediaStoreExporter.sanitize(groupName)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "BetterSticker_${safe}_$stamp.zip"
            val uri = MediaStoreExporter(context).exportBytes(name, "application/zip", bytes)
            if (uri == null) Result(false, "导出失败：MediaStore 返回 null")
            else Result(true, "已导出 $groupName：${items.size} 项\nDownload/BetterSticker/$name")
        } catch (t: Throwable) {
            LogX.e("分组导出失败", t)
            Result(false, "分组导出失败: ${t.message}")
        }
    }

    private fun buildZip(store: FavoriteStore, groupName: String, items: List<FavoriteItem>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject().apply {
                put("app", "BetterSticker")
                put("schemaVersion", 2)
                put("group", groupName)
                put("name", groupName)
                put("exportedAt", FavoriteItem.iso(System.currentTimeMillis()))
                put("items", JSONArray().apply {
                    for (it in items) {
                        put(JSONObject().apply {
                            put("name", it.originalName ?: "")
                            put("type", it.type)
                            put("mime", it.mime ?: "")
                            put("sha256", it.sha256 ?: "")
                        })
                    }
                })
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val used = HashSet<String>()
            val folder = MediaStoreExporter.sanitize(groupName) + "/"
            for ((index, it) in items.withIndex()) {
                val data = store.read(it)
                val fallbackExt = when {
                    it.isVoice -> "silk"
                    else -> it.storedFile.substringAfterLast('.', "bin")
                }
                var name = MediaStoreExporter.sanitize(it.originalName.ifEmpty { "item_$index" })
                if (!name.contains('.')) name = "$name.$fallbackExt"
                var entryName = folder + name
                var n = 1
                while (!used.add(entryName)) {
                    entryName = folder + name.substringBeforeLast('.') + "_" + (n++) +
                        "." + name.substringAfterLast('.')
                }
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
