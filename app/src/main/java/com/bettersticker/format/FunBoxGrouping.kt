package com.bettersticker.format

/**
 * FunBox 目录分组规则：
 *   .fun/Sticker/Storage/<表情包分组>/...
 * 取 Sticker/Storage 后面的第一段作为分组，而不是文件所在的最深目录。
 *
 * 兼容：
 *   - 用户直接选择 Storage；
 *   - ZIP 根目录直接是 <分组>/...；
 *   - 没有 Storage 时退回第一段目录。
 */
object FunBoxGrouping {
    @JvmStatic
    fun groupFor(relativePath: String, rootName: String?): String {
        val parts = relativePath.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return "FunBox 迁移"

        var base = -1
        for (i in parts.indices) {
            if (parts[i].equals("Storage", ignoreCase = true)) {
                base = i + 1
                break
            }
        }
        if (base < 0) {
            for (i in parts.indices) {
                if (parts[i].equals("Sticker", ignoreCase = true)) {
                    base = i + 1
                    break
                }
            }
        }
        // 文件直接位于选择根目录：用根目录名（除非根就是 .fun）。
        if (base < 0 && parts.size == 1) {
            return packName(rootName)
        }
        if (base < 0) base = 0

        if (base < parts.size) {
            // base 指向文件本身时说明当前层没有分组目录。
            val candidate = parts[base]
            if (base == parts.size - 1 && candidate.substringAfterLast('.', "").isNotEmpty()) {
                return packName(rootName)
            }
            return candidate
        }
        return packName(rootName)
    }

    private fun packName(rootName: String?): String {
        val s = rootName?.trim().orEmpty()
        if (s.isEmpty() || s.equals(".fun", true)
            || s.equals("Storage", true) || s.equals("Sticker", true)) {
            return "FunBox 迁移"
        }
        return s
    }
}
