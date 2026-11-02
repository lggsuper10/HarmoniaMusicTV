package com.harmoniamusic.tv.data.source.userapi

/**
 * 内置预设用户 API 清单（对应 RN 版 `config/tvPresetUserApi.ts` 的 TV_PRESET_USER_API_CANDIDATES）。
 * 脚本随 APK 打包进 assets/script/，不可被用户修改；UI 仅可「选用 / 移除 / 测网络 / 开关更新提醒」。
 */
data class PresetUserApi(
    val id: String,
    val name: String,
    val author: String,
    val assetPath: String,
)

object PresetUserApis {
    val list: List<PresetUserApi> = listOf(
        PresetUserApi(
            id = "tv_preset_juhe",
            name = "聚合API接口 (CF)",
            author = "lerd",
            assetPath = "script/tv-default-user-api-juhe.js",
        ),
        PresetUserApi(
            id = "tv_preset_grass",
            name = "野草",
            author = "pdone",
            assetPath = "script/tv-default-user-api.js",
        ),
        PresetUserApi(
            id = "tv_preset_xinghai",
            name = "星海音乐源",
            author = "万去了了",
            assetPath = "script/tv-default-xinghai.js",
        ),
    )

    /** 音源中文显示名（设置页 / 源选择用）。 */
    fun displayName(id: String): String = when (id) {
        "tv_preset_grass" -> "野草"
        "tv_preset_juhe" -> "聚合音源"
        "tv_preset_xinghai" -> "星海音乐源"
        else -> id
    }
}
