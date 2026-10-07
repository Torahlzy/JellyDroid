package dev.jdtech.jellyfin.core.translator

/**
 * 翻译失败的原因。
 *
 * [message] 会原样展示给用户（例如「翻译接口返回 401：Invalid API key」），
 * 所以要写成人话，不要把异常类名之类的诊断信息塞进去——诊断信息用 AppLog 打。
 */
class TranslateException(message: String, cause: Throwable? = null) : Exception(message, cause)
