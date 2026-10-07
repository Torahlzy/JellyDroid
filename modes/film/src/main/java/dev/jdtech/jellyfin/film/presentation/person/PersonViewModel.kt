package dev.jdtech.jellyfin.film.presentation.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel
class PersonViewModel @Inject internal constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(PersonState())
    val state = _state.asStateFlow()

    /**
     * 加载人物资料与其参演的作品。
     *
     * 两次请求分开处理：任一侧失败都只影响自己，并且都把原因记进日志与 [PersonState.error]，
     * 避免一处失败就让整页停在加载态、且查不出原因。
     */
    fun loadPerson(personId: UUID) {
        viewModelScope.launch {
            val person =
                try {
                    repository.getPerson(personId)
                } catch (e: Exception) {
                    AppLog.e(e, "加载人物资料失败：%s", personId)
                    null
                }

            val items =
                try {
                    repository.getPersonItems(
                        personIds = listOf(personId),
                        includeTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                    )
                } catch (e: Exception) {
                    AppLog.e(e, "加载人物作品失败：%s", personId)
                    _state.emit(_state.value.copy(person = person, error = e))
                    return@launch
                }

            _state.emit(
                _state.value.copy(
                    person = person,
                    starredInMovies = items.filterIsInstance<FindroidMovie>(),
                    starredInShows = items.filterIsInstance<FindroidShow>(),
                    error = null,
                )
            )
        }
    }
}
