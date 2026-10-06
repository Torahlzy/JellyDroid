package dev.jdtech.jellyfin.film.presentation.movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class MovieViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val videoMetadataParser: VideoMetadataParser,
) : ViewModel() {
    private val _state = MutableStateFlow(MovieState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<MovieEvent>()
    val events = eventsChannel.receiveAsFlow()

    lateinit var movieId: UUID

    fun loadMovie(movieId: UUID) {
        this.movieId = movieId
        viewModelScope.launch {
            try {
                val movie = repository.getMovie(movieId)
                val videoMetadata = videoMetadataParser.parse(movie.sources.first())
                val actors = getActors(movie)
                val director = getDirector(movie)
                val writers = getWriters(movie)
                val displayExtraInfo = appPreferences.getValue(appPreferences.displayExtraInfo)
                _state.emit(
                    _state.value.copy(
                        movie = movie,
                        videoMetadata = videoMetadata,
                        actors = actors,
                        director = director,
                        writers = writers,
                        displayExtraInfo = displayExtraInfo,
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun getActors(item: FindroidMovie): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    private suspend fun getDirector(item: FindroidMovie): FindroidItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: FindroidMovie): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.WRITER }
        }
    }

    fun onAction(action: MovieAction) {
        when (action) {
            is MovieAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.DeleteItemImages -> {
                deleteItemImages(action.images)
            }
            is MovieAction.ResetItemMetadata -> {
                resetItemMetadata()
            }
            is MovieAction.DeleteItemWithFiles -> {
                deleteItemWithFiles()
            }
            else -> Unit
        }
    }

    /** 加载服务器上的图片列表，供删除前逐张列出确认。 */
    fun loadItemImages() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoadingItemImages = true, itemImagesError = null))
            try {
                _state.emit(
                    _state.value.copy(
                        itemImages = repository.getItemImages(movieId),
                        isLoadingItemImages = false,
                    )
                )
            } catch (e: Exception) {
                AppLog.e(e, "加载服务器图片列表失败：%s", movieId)
                // 失败时清掉上一次的列表，避免弹窗里继续显示可能已被删掉的旧图片
                // 失败原因只落到 state：弹窗此时是开着的，直接在弹窗里显示即可，不再额外弹 Toast
                _state.emit(
                    _state.value.copy(
                        itemImages = emptyList(),
                        isLoadingItemImages = false,
                        itemImagesError = e,
                    )
                )
            }
        }
    }

    /** 删除服务器上选中的封面图片，成功后刷新页面让封面回退到下一张。 */
    private fun deleteItemImages(images: List<FindroidItemImage>) {
        viewModelScope.launch {
            try {
                repository.deleteItemImages(movieId, images)
                // 列表里的图片已被删掉，先清空；下次打开弹窗会重新拉取
                _state.emit(_state.value.copy(itemImages = emptyList()))
                eventsChannel.send(MovieEvent.ItemImagesDeleted)
                loadMovie(movieId)
            } catch (e: Exception) {
                AppLog.e(e, "删除服务器封面失败：%s", movieId)
                eventsChannel.send(MovieEvent.ItemImagesDeleteFailed(e))
            }
        }
    }

    /** 重置服务器上的 nfo，标题回退为服务器文件名。 */
    private fun resetItemMetadata() {
        viewModelScope.launch {
            try {
                repository.clearItemMetadata(movieId, _state.value.serverFileName)
                eventsChannel.send(MovieEvent.MetadataReset)
                loadMovie(movieId)
            } catch (e: Exception) {
                AppLog.e(e, "重置服务器 nfo 失败：%s", movieId)
                eventsChannel.send(MovieEvent.MetadataResetFailed(e))
            }
        }
    }

    /** 删除服务器上的条目本身，界面收到事件后返回上一页。 */
    private fun deleteItemWithFiles() {
        viewModelScope.launch {
            try {
                repository.deleteItem(movieId)
                eventsChannel.send(MovieEvent.ItemDeleted)
            } catch (e: Exception) {
                AppLog.e(e, "删除服务器条目失败：%s", movieId)
                eventsChannel.send(MovieEvent.ItemDeleteFailed(e))
            }
        }
    }
}
