package dev.jdtech.jellyfin.presentation.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.scraper.ProxyAddressInvalidException
import dev.jdtech.jellyfin.core.scraper.ProxyConnectionTester
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 代理弹窗里的连通性测试，设置页与详情页共用一份状态。 */
@HiltViewModel
class ProxyTestViewModel
@Inject
constructor(
    private val tester: ProxyConnectionTester,
) : ViewModel() {
    private val _state = MutableStateFlow(ProxyTestState())
    val state = _state.asStateFlow()

    /** 最近一次测试的地址：用户改了输入后，迟到返回的旧结果不能再落到界面上。 */
    private var latestAddress: String? = null

    fun test(address: String) {
        latestAddress = address
        viewModelScope.launch {
            _state.emit(ProxyTestState(isTesting = true))
            val result = tester.test(address)
            if (latestAddress != address) return@launch
            _state.emit(
                result.fold(
                    onSuccess = { ProxyTestState(durationMillis = it) },
                    onFailure = {
                        val isInvalidAddress = it is ProxyAddressInvalidException
                        ProxyTestState(
                            isInvalidAddress = isInvalidAddress,
                            error = if (isInvalidAddress) null else it.message,
                        )
                    },
                )
            )
        }
    }

    /** 输入内容变化 / 弹窗重新打开时清掉上一次结果。 */
    fun clearResult() {
        latestAddress = null
        viewModelScope.launch { _state.emit(ProxyTestState()) }
    }
}
