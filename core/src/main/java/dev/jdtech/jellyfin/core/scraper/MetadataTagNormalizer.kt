package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.logging.AppLog

/**
 * 分类（genre）与标签（tag）的归一：拆分被塞进单个元素的多个分类、繁→简、同义词合并、
 * 特征值迁移到 tag、删除旧模板产物 `制作:X`。
 *
 * 规则与词表参照 JavSP 的 `javsp/tag.py`。之所以要把 genre 与 tag 分开：Jellyfin 上只有分类
 * 可以点击跳转，标签不行，所以「剧情 / 题材」留在分类，分辨率、字幕、码型这类文件特征应进标签。
 *
 * 繁→简只用本文件的字表，不引第三方词库：它只覆盖分类 / 标签里会出现的字，不会把片名、人名
 * 里的字改坏；表里没有的写法原样保留（宁可留着繁体，也不猜）。
 */
object MetadataTagNormalizer {
    /** 特征值：属于标签而非分类（分辨率 / 画质、字幕、码型、作品形态、发行形态）。 */
    private val FEATURE_TAGS =
        setOf(
            // 分辨率 / 画质
            "4K",
            "8K",
            "1080p",
            "60fps",
            "高画质",
            "高清",
            "HD",
            "ハイビジョン",
            "フルハイビジョン(FHD)",
            // 字幕
            "内嵌字幕",
            "中文字幕",
            // 码型（有无码 / 马赛克）
            "有码",
            "无码",
            "无修正",
            "无码流出",
            "无码破解",
            "无码流出/破解",
            "打码情况未知",
            "数位马赛克",
            "薄马赛克",
            // 作品形态
            "素人作品",
            "单体作品",
            // 发行形态
            "DMM独家",
            "独占配信",
            "配信専用",
            "VR専用",
            "MGSだけのおまけ映像付き",
        )

    /**
     * 词级别名：值 → 统一形态。
     *
     * 只放字表处理不了的差异——日文新字体、两种译名、顿号连接的整串；繁→简交给 [SIMPLIFIED_CHARS]。
     */
    private val MERGE_ALIASES =
        mapOf(
            // メイド 的两种译名统一成 JavSP 用的那一个
            "女仆" to "女佣",
            "模特儿" to "模特",
            // 日文写法与中文译名同义
            "中出し" to "中出",
            "潮吹き" to "潮吹",
            "4时间以上作品" to "4小时以上作品",
            // 顿号是词内连接而非分隔，整串命中时优先于拆分（键要写成繁→简之后的形态）
            "精选、综合" to "精选综合",
            "姐・妹" to "姐妹",
        )

    /** 多个分类被塞进单个元素时的分隔符。 */
    private val SPLIT_PATTERN = Regex("[,，、]")

    /** 旧版 nfo 模板的产物：`制作:Fitch`（繁→简之后只剩简体形态），与 studio 完全重复。 */
    private val MAKER_PATTERN = Regex("^制作:")

    /** 平假名、片假名与半角片假名：出现即说明是日文原文。 */
    private val KANA_PATTERN = Regex("[\\u3040-\\u309f\\u30a0-\\u30ff\\uff66-\\uff9f]")

    /** 中日韩字符的下界，与 JavSP 判断「是否含中日韩字符」用的是同一个界。 */
    private const val ASCII_LIMIT = 0x2E7F

    /**
     * 繁→简字表，按「两个字符一组、繁体在前」书写。
     *
     * 分组只为便于增补，运行时拼成一张表；漏写一个字会让该组从漏点起整体错位，
     * 因此长度不是偶数时告警并丢弃落单的末位。
     */
    private val SIMPLIFIED_CHAR_GROUPS =
        listOf(
            // 作品形态 / 码型 / 画质 / 字幕：單體作品、無碼、無修正、高畫質、標清…
            "單单體体無无碼码內内製制總总選选綜综優优質质畫画標标視视頻频",
            // 人物 / 职业：女教師、女大學生、護士、秘書、導演、老闆娘、爺爺…
            "師师學学護护書书親亲婦妇兒儿漢汉僕仆醫医練练員员隊队導导監监闆板爺爷媽妈傭佣傳传記记",
            // 外形：顏射、髮型、貧乳、豐滿、苗條、蘿莉塔、嬌小…
            "顏颜顔颜髮发髪发臉脸腳脚貧贫豐丰滿满條条蘿萝麗丽豔艳艷艳髒脏嬌娇鬆松",
            // 行为 / 题材：亂倫、強姦、輪姦、羞恥、捆綁、調教、潤滑液、連褲襪、絲襪、後宮、
            // 溫泉、戶外、電車、主觀視角、曬黑、偷窺、盜撮、淫語、戲劇、惡作劇、約會、寫真…
            "亂乱強强姦奸輪轮恥耻綁绑調调潤润連连褲裤襪袜絲丝後后宮宫裝装溫温戶户電电觀观" +
                "曬晒窺窥盜盗竊窃語语網网紗纱喪丧訊讯話话騎骑側侧屬属復复複复戲戏劇剧惡恶訕讪" +
                "約约會会寫写動动攝摄紀纪錄录藝艺門门專专癡痴園园誘诱樂乐",
            // 服装 / 场所 / 物件：眼鏡、襯衫、項鍊、繩、牆、館、藥、療、濕、潔、湯、煙、燈、熱…
            "鏡镜襯衬帶带飾饰項项鏈链環环繩绳籠笼籃篮牆墙廳厅廠厂廣广館馆場场藥药療疗濕湿" +
                "潔洁濃浓湯汤煙烟燈灯熱热飯饭賓宾診诊",
            // 常用字：戀、愛、懷、態、應、願、驚、興、歡、難、靜、雙、對、從、來、個、時、間、
            // 開、關、長、見、實、樣、種、級、結、絕、統、綠、維、緊、線、織、續、聲、職、聽、
            // 脫、變、讓、贊、賞…
            "戀恋愛爱憂忧懷怀慮虑慣惯態态應应慶庆願愿驚惊興兴舉举歡欢難难煩烦靜静靈灵雙双" +
                "對对從从來来個个們们時时間间開开關关長长見见現现實实樣样種种級级給给結结" +
                "絕绝統统綠绿維维緊紧線线織织續续聲声職职聽听脫脱變变讓让讚赞贊赞賞赏",
            // 日文新字体：単、実、変、発、気、薬、楽（与汉字混排的站点分类里常见）
            "単单実实変变発发楽乐気气薬药",
            // 补字（作品 / 场所 / 职业）：獨占、發行商、數位、專業、運動、電車、圖書館、情況、
            // 藥劑師、獸醫、廚師、導遊、自衛隊、喪屍、療癒、項鍊、養女、辦公室、翻譯、檢察官、
            // 偵探、軍人、奴隸、寵物、機器人、異世界、懸疑、紋身、飲精、服務生、馬尾、中華、
            // 交換伴侶、補習班、高爾夫、潛水、衝浪、體驗、業餘、處女、預覽、縮圖、瀏覽、檔案、
            // 年齡、牙齒…
            "獨独發发數数業业運运車车圖图況况劑剂獸兽廚厨遊游衛卫屍尸癒愈鍊链養养辦办譯译" +
                "檢检偵侦軍军隸隶寵宠機机異异懸悬紋纹飲饮務务馬马華华換换侶侣習习補补爾尔潛潜" +
                "衝冲驗验餘余處处覽览預预縮缩瀏浏檔档齡龄齒齿姊姐寢寝",
            // 补字（高频）：說、這、進、還、為、與、將、兩、點、於、裡、週、隻、臺、幾、麼
            "說说這这進进還还為为與与將将兩两點点於于裡里裏里週周隻只臺台幾几麼么",
            // 补字（零散）：亂倫、賽車、出軌、淫蕩、縱慾、心臟、鬍子、嚇人、團體、聖女、處罰、
            // 罵人、陰毛、雜交、離脱、頸部、照顧、黃色、悠閒
            "倫伦賽赛軌轨縱纵慾欲蕩荡臟脏鬍胡嚇吓團团聖圣罰罚罵骂陰阴雜杂離离頸颈顧顾黃黄閒闲",
        )

    private val SIMPLIFIED_CHARS: Map<Char, Char> =
        buildMap {
            for (group in SIMPLIFIED_CHAR_GROUPS) {
                // 不写成 require：字表笔误不该变成「点抓取就崩」，告警后按已对齐的部分继续
                if (group.length % 2 != 0) {
                    AppLog.wtf("繁简字表分组长度不是偶数，末位的字已忽略：%s", group)
                }
                group.chunked(2).forEach { pair -> if (pair.length == 2) put(pair[0], pair[1]) }
            }
        }

    /**
     * 归一 [genres] / [tags]，返回归一后的两列。
     *
     * 入参可以是站点原始值（含繁体、日文、逗号合并的多值），也可以是已翻译过的值——
     * 归一与翻译要交替做两遍，翻译后出现的特征值（如 `高画质`）才能正确迁到标签。
     */
    fun normalize(
        genres: Iterable<String>,
        tags: Iterable<String> = emptyList(),
    ): Pair<List<String>, List<String>> {
        val newGenres = mutableListOf<String>()
        val newTags = mutableListOf<String>()

        fun feed(value: String, toTagsOnly: Boolean) {
            for (segment in splitValues(value)) {
                val normalized = normalizeValue(segment)
                if (normalized.isEmpty() || MAKER_PATTERN.containsMatchIn(normalized)) continue
                val target = if (toTagsOnly || normalized in FEATURE_TAGS) newTags else newGenres
                if (normalized !in target) target += normalized
            }
        }

        genres.forEach { feed(it, toTagsOnly = false) }
        tags.forEach { feed(it, toTagsOnly = true) }
        return newGenres to newTags
    }

    /** 文本中是否含假名，即是否为日文原文（判定是否需要翻译的依据之一）。 */
    fun containsKana(text: String): Boolean = KANA_PATTERN.containsMatchIn(text)

    /** 类别值归一：trim + 繁→简 + 语义别名统一。 */
    private fun normalizeValue(value: String): String {
        val simplified = toSimplified(value.trim())
        return MERGE_ALIASES[simplified] ?: simplified
    }

    /** 繁→简（字表未收录的写法原样保留）；翻译词典查表前也要用同一套转换。 */
    internal fun toSimplified(text: String): String =
        if (text.isEmpty()) {
            text
        } else {
            buildString(text.length) { text.forEach { append(SIMPLIFIED_CHARS[it] ?: it) } }
        }

    /**
     * 拆分一个元素里的多个分类。
     *
     * 整串命中别名时优先（`精选、综合` 的顿号是词内连接）；纯 ASCII 值不拆，避免误伤 TMDB 关键词
     * （如 `manhattan, new york city`）。
     */
    private fun splitValues(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptyList()
        MERGE_ALIASES[toSimplified(trimmed)]?.let { return listOf(it) }
        return if (trimmed.any { it.code > ASCII_LIMIT }) {
            trimmed.split(SPLIT_PATTERN)
        } else {
            listOf(trimmed)
        }
    }
}
