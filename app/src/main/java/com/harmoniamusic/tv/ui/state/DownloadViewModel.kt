package com.harmoniamusic.tv.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmoniamusic.tv.data.download.DownloadManager
import com.harmoniamusic.tv.data.download.DownloadTask
import com.harmoniamusic.tv.data.source.LOCAL_SOURCE
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicSources
import com.harmoniamusic.tv.data.source.userapi.UserApiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 下载 ViewModel：解析音源取流地址后交由 [DownloadManager] 落盘。
 */
class DownloadViewModel(app: Application) : AndroidViewModel(app) {
    private val dm = DownloadManager(app)
    private val userApi = UserApiManager.shared(app)
    val tasks: StateFlow<List<DownloadTask>> = dm.tasks

    /** 下载操作反馈（成功/失败一句话），由界面弹提示后清空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun download(item: MusicItem, quality: String = "128k") {
        viewModelScope.launch {
            if (item.source == LOCAL_SOURCE) {
                _message.value = "本地歌曲无需下载"
                return@launch
            }
            runCatching { userApi.init() }
            val builtinUrl = runCatching {
                MusicSources.builtin[item.source]?.getMusicUrl(item.songmid, quality)
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val url = builtinUrl
                ?: runCatching { userApi.getMusicUrl(item, quality) }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
            if (url == null) {
                _message.value = "取流失败，无法下载：${item.name}"
                return@launch
            }
            dm.start(item, url)
            _message.value = "已加入下载：${item.name}"
        }
    }
}
