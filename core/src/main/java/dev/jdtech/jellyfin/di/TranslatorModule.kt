package dev.jdtech.jellyfin.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.core.translator.MetadataTranslator
import dev.jdtech.jellyfin.core.translator.MetadataTranslatorImpl
import dev.jdtech.jellyfin.core.translator.TagTranslator
import dev.jdtech.jellyfin.core.translator.TagTranslatorImpl
import javax.inject.Singleton

/**
 * nfo 翻译（OpenAI 兼容接口）的依赖装配。
 *
 * 与抓取共用同一个 OkHttp 客户端（带浏览器请求头、跟随站点 Cookie 的那个），
 * 因此这里不再另外提供客户端，只绑定接口。注意翻译不读设置里的抓取代理，
 * 见 `MetadataTranslatorImpl`。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TranslatorModule {

    @Binds
    @Singleton
    abstract fun bindMetadataTranslator(impl: MetadataTranslatorImpl): MetadataTranslator

    /** 分类 / 标签的翻译器：词典优先、引擎兜底，译文缓存到本地偏好。 */
    @Binds
    @Singleton
    abstract fun bindTagTranslator(impl: TagTranslatorImpl): TagTranslator
}
