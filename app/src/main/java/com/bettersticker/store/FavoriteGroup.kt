package com.bettersticker.store

import org.json.JSONObject

/** 表情包分组。favorites=true 的是系统“收藏”分组，不直接存 groupId。 */
class FavoriteGroup {
    @JvmField var id: String = ""
    @JvmField var name: String = ""
    @JvmField var createdAt: Long = 0
    @JvmField var favorite: Boolean = false
    @JvmField var builtin: Boolean = false

    constructor()

    constructor(id: String, name: String, favorite: Boolean = false, builtin: Boolean = false) {
        this.id = id
        this.name = name
        this.createdAt = System.currentTimeMillis()
        this.favorite = favorite
        this.builtin = builtin
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("favorite", favorite)
        put("builtin", builtin)
    }

    companion object {
        const val DEFAULT_ID = "default"
        const val FAVORITES_ID = "favorites"

        @JvmStatic
        fun fromJson(o: JSONObject): FavoriteGroup = FavoriteGroup().apply {
            id = o.optString("id", "")
            name = o.optString("name", "未命名")
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
            favorite = o.optBoolean("favorite", false)
            builtin = o.optBoolean("builtin", false)
        }

        @JvmStatic
        fun defaults(): List<FavoriteGroup> = listOf(
            FavoriteGroup(DEFAULT_ID, "默认", false, true),
            FavoriteGroup(FAVORITES_ID, "收藏", true, true),
        )
    }
}
