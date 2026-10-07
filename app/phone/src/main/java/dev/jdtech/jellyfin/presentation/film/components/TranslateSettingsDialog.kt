package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.domain.models.TranslateSettings

// 字段滚动区的高度上限：BaseDialog 卡片本身限高 540dp，扣掉标题、「翻译现有」与按钮行后留给字段的空间有限
private val FORM_MAX_HEIGHT = 260.dp

/**
 * 「翻译设置」弹窗：配置把抓到的日文标题 / 简介翻成简体中文用的大模型。
 *
 * 走 OpenAI 兼容的对话接口，所以只要「地址 / 密钥 / 模型」三项——地址要写完整（含 `chat/completions`），
 * 换服务时改这三项即可。设置只存在本机，不会上传到服务器。
 *
 * 「抓取后自动翻译」勾上后，抓取一结束就按所选字段翻译，某一字段翻译失败时保留该字段的原文；
 * 不想为了翻译再抓一次时用「翻译现有」，它翻的是调用方（编辑 nfo 弹窗）当前表单里的内容，
 * 因此「当前表单里哪个字段有内容」由调用方通过 [hasTranslatableTitle] / [hasTranslatableOverview] 告诉它，
 * 与这里当场勾选的值合起来判断能否点——刚勾上的字段才不会被已保存的设置挡住。
 *
 * 两个动作都会把弹窗里改过的设置一起交出去，避免「刚改完密钥就点翻译，却用了旧设置」。
 */
@Composable
fun TranslateSettingsDialog(
    settings: TranslateSettings,
    hasTranslatableTitle: Boolean,
    hasTranslatableOverview: Boolean,
    onConfirm: (TranslateSettings) -> Unit,
    onTranslateExisting: (TranslateSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(settings.url) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var translateTitle by remember { mutableStateOf(settings.translateTitle) }
    var translatePlot by remember { mutableStateOf(settings.translatePlot) }
    var autoTranslate by remember { mutableStateOf(settings.autoTranslate) }

    val collectSettings = {
        TranslateSettings(
            url = url.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            translateTitle = translateTitle,
            translatePlot = translatePlot,
            autoTranslate = autoTranslate,
        )
    }

    // 与 TranslateSettings.isConfigured 同一套判断：三项都填了才可能翻成功
    val isConfigured = url.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    // 用不带按钮槽位的 BaseDialog：按钮行要多摆一个「翻译现有内容」，negative / positive 两个槽位不够用
    BaseDialog(
        title = stringResource(CoreR.string.translate_settings),
        onDismiss = onDismiss,
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        // 必须限高：卡片本身限高 540dp，滚动区若不限高就会吃掉全部剩余高度，
                        // 把下面的「翻译现有内容 / 取消 / 确认」按钮行挤成 0 高度
                        .heightIn(max = FORM_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(CoreR.string.translate_settings_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(MaterialTheme.spacings.medium))
                TranslateTextField(
                    label = stringResource(CoreR.string.translate_url),
                    value = url,
                    onValueChange = { url = it },
                    keyboardType = KeyboardType.Uri,
                )
                TranslateTextField(
                    label = stringResource(CoreR.string.translate_api_key),
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    // 密钥属于隐私，按密码处理：明文显示容易被截图 / 录屏带走
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
                TranslateTextField(
                    label = stringResource(CoreR.string.translate_model),
                    value = model,
                    onValueChange = { model = it },
                )
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                Text(
                    text = stringResource(CoreR.string.translate_fields),
                    style = MaterialTheme.typography.titleSmall,
                )
                // 标题 / 简介的文案与「编辑 nfo」表单里的同名标签共用，避免两处说法不一致
                TranslateCheckbox(
                    label = stringResource(CoreR.string.edit_metadata_field_name),
                    checked = translateTitle,
                    onCheckedChange = { translateTitle = it },
                )
                TranslateCheckbox(
                    label = stringResource(CoreR.string.edit_metadata_field_overview),
                    checked = translatePlot,
                    onCheckedChange = { translatePlot = it },
                )
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                TranslateCheckbox(
                    label = stringResource(CoreR.string.translate_auto),
                    checked = autoTranslate,
                    onCheckedChange = { autoTranslate = it },
                )
                // 勾了自动翻译但三项没填全时，抓取后会静默跳过翻译；这里说清原因，免得用户以为功能坏了
                if (autoTranslate && !isConfigured) {
                    Text(
                        text = stringResource(CoreR.string.translate_auto_unconfigured),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // 放在滚动区之外，滑不滑都能点到
            Spacer(Modifier.height(MaterialTheme.spacings.default))
            // 三个按钮一起靠右：「翻译现有内容」紧贴「取消」左侧，左边缘与它对齐
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onTranslateExisting(collectSettings()) },
                    // 勾了哪个字段、那个字段在当前表单里就得有内容，否则这趟翻译无事可做
                    enabled =
                        (translateTitle && hasTranslatableTitle) ||
                            (translatePlot && hasTranslatableOverview),
                ) {
                    Text(text = stringResource(CoreR.string.translate_existing))
                }
                TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
                TextButton(onClick = { onConfirm(collectSettings()) }) {
                    Text(text = stringResource(CoreR.string.confirm))
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacings.default))
        }
    }
}

@Composable
private fun TranslateTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        singleLine = true,
    )
    Spacer(Modifier.height(MaterialTheme.spacings.small))
}

/** 整行可点：勾选框本身很小，光靠它不好点。 */
@Composable
private fun TranslateCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview
@Composable
private fun TranslateSettingsDialogPreview() {
    FindroidTheme {
        TranslateSettingsDialog(
            settings = TranslateSettings(url = "https://api.deepseek.com/chat/completions"),
            hasTranslatableTitle = true,
            hasTranslatableOverview = true,
            onConfirm = {},
            onTranslateExisting = {},
            onDismiss = {},
        )
    }
}
