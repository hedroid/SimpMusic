package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * 网易云评论表情文本码([xx])→ unicode emoji 映射。官方客户端把 [微笑] 渲染成自家表情图,
 * 三方端拿不到稳定图源,这里用语义最接近的 unicode 渲染;不在表里的码保持原文(官方新表情
 * 不至于乱码,只是不转义)。
 */
internal val NeteaseEmojiMap: Map<String, String> =
    mapOf(
        "微笑" to "😊", "开心" to "😄", "色" to "😍", "发呆" to "😐", "得意" to "😌",
        "流泪" to "😢", "害羞" to "😳", "闭嘴" to "🤐", "睡" to "😴", "大哭" to "😭",
        "尴尬" to "😅", "发怒" to "😠", "调皮" to "😜", "呲牙" to "😁", "惊讶" to "😮",
        "难过" to "😔", "囧" to "😖", "抓狂" to "😫", "吐" to "🤮", "偷笑" to "🤭",
        "可爱" to "🥰", "白眼" to "🙄", "傲慢" to "😤", "饥饿" to "🤤", "困" to "😪",
        "惊恐" to "😱", "流汗" to "😅", "憨笑" to "🤣", "大兵" to "🫡", "奋斗" to "😤",
        "咒骂" to "🤬", "疑问" to "❓", "嘘" to "🤫", "晕" to "😵", "折磨" to "😖",
        "衰" to "💀", "骷髅" to "💀", "敲打" to "👊", "再见" to "👋", "擦汗" to "😳",
        "抠鼻" to "👃", "鼓掌" to "👏", "坏笑" to "😏", "左哼哼" to "😤", "右哼哼" to "😤",
        "哈欠" to "🥱", "鄙视" to "🙄", "委屈" to "😣", "快哭了" to "😥", "阴险" to "😏",
        "亲亲" to "😘", "吓" to "😱", "可怜" to "🥺", "菜刀" to "🔪", "西瓜" to "🍉",
        "啤酒" to "🍺", "篮球" to "🏀", "乒乓" to "🏓", "咖啡" to "☕", "饭" to "🍚",
        "猪头" to "🐷", "玫瑰" to "🌹", "凋谢" to "🥀", "嘴唇" to "👄", "爱心" to "❤️",
        "心碎" to "💔", "蛋糕" to "🎂", "闪电" to "⚡", "炸弹" to "💣", "刀" to "🔪",
        "足球" to "⚽", "瓢虫" to "🐞", "便便" to "💩", "月亮" to "🌙", "太阳" to "☀️",
        "礼物" to "🎁", "拥抱" to "🤗", "强" to "👍", "弱" to "👎", "握手" to "🤝",
        "胜利" to "✌️", "抱拳" to "🙏", "勾引" to "👉", "拳头" to "✊", "差劲" to "👎",
        "爱你" to "🤟", "NO" to "🙅", "OK" to "👌", "转圈" to "💫", "磕头" to "🙏",
        "回头" to "↩️", "跳绳" to "🏃", "挥手" to "👋", "激动" to "🎉", "街舞" to "💃",
        "献吻" to "💋", "左太极" to "☯️", "右太极" to "☯️", "闲逛" to "🚶",
        // 2018+ 新增
        "耍酷" to "😎", "捂脸" to "🤦", "多多捂脸" to "🤦", "黑线" to "😶", "皱眉" to "😟",
        "嘿哈" to "🙂", "无语" to "😶", "奸笑" to "😏", "吃瓜" to "🍉", "加油" to "💪",
        "油" to "⛽", "汗" to "💧", "天啊" to "😱", "Emm" to "🤔", "社会社会" to "🕶️",
        "旺柴" to "🐶", "好的" to "👌", "打脸" to "👋", "哇" to "😮", "翻白眼" to "🙄",
        "666" to "💯", "让我看看" to "👀", "叹气" to "😔", "苦涩" to "😖", "裂开" to "💥",
        "拜托" to "🙏", "泪目" to "🥲", "赞" to "👍", "踩" to "👎", "比心" to "💗",
        "顶" to "👍", "哈哈" to "😆", "吐舌" to "😛", "祈愿" to "🙏", "钻头" to "🔨",
        "很棒" to "👍", "乞求" to "🥺", "求求了" to "🥺", "惊呆" to "🤯", "崇拜" to "🤩",
    )

private sealed interface NeteaseEmojiSegment {
    data class Text(val text: String) : NeteaseEmojiSegment

    data class Emoji(val code: String, val emoji: String) : NeteaseEmojiSegment
}

/** 把评论文本按 [表情码] 切段;不在映射表里的码归回文本段 */
private fun splitNeteaseEmoji(text: String): List<NeteaseEmojiSegment> {
    if (!text.contains('[')) return listOf(NeteaseEmojiSegment.Text(text))
    val segments = mutableListOf<NeteaseEmojiSegment>()
    var plainStart = 0
    var i = 0
    while (i < text.length) {
        if (text[i] == '[') {
            val close = text.indexOf(']', i + 1)
            if (close > i + 1) {
                val code = text.substring(i + 1, close)
                val emoji = NeteaseEmojiMap[code]
                if (emoji != null) {
                    if (i > plainStart) segments.add(NeteaseEmojiSegment.Text(text.substring(plainStart, i)))
                    segments.add(NeteaseEmojiSegment.Emoji("[$code]", emoji))
                    i = close + 1
                    plainStart = i
                    continue
                }
            }
        }
        i++
    }
    if (plainStart < text.length) segments.add(NeteaseEmojiSegment.Text(text.substring(plainStart)))
    return segments
}

/**
 * 评论正文渲染:[微笑] 类表情文本码替换为 unicode emoji 内联内容;无码时零开销直出。
 * fallback 传原始码文本,长码截断场景语义仍可读。
 */
@Composable
fun NeteaseEmojiText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    val segments = remember(text) { splitNeteaseEmoji(text) }
    if (segments.size == 1 && segments[0] is NeteaseEmojiSegment.Text) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    val fontSize: TextUnit = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val size = fontSize * 1.2f
    val annotated: AnnotatedString =
        buildAnnotatedString {
            segments.forEachIndexed { index, segment ->
                when (segment) {
                    is NeteaseEmojiSegment.Text -> append(segment.text)
                    is NeteaseEmojiSegment.Emoji -> appendInlineContent("emoji$index", segment.code)
                }
            }
        }
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    segments.forEachIndexed { index, segment ->
        if (segment is NeteaseEmojiSegment.Emoji) {
            inlineContent["emoji$index"] =
                InlineTextContent(
                    Placeholder(size, size, PlaceholderVerticalAlign.TextCenter),
                ) {
                    Text(text = segment.emoji, fontSize = fontSize)
                }
        }
    }
    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inlineContent,
        modifier = modifier,
    )
}
