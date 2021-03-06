package io.legado.app.ui.book.read.page.provider

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import io.legado.app.App
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.AppConfig
import io.legado.app.help.ReadBookConfig
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextChar
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.*
import java.util.regex.Pattern


@Suppress("DEPRECATION")
object ChapterProvider {
    var viewWidth = 0
    var viewHeight = 0
    var paddingLeft = 0
    var paddingTop = 0
    var visibleWidth = 0
    var visibleHeight = 0
    var visibleRight = 0
    var visibleBottom = 0
    private var lineSpacingExtra = 0
    private var paragraphSpacing = 0
    private var titleTopSpacing = 0
    private var titleBottomSpacing = 0
    var typeface: Typeface = Typeface.SANS_SERIF
    lateinit var titlePaint: TextPaint
    lateinit var contentPaint: TextPaint
    private val srcPattern =
        Pattern.compile("<img .*?src.*?=.*?\"(.*?)\".*?>", Pattern.CASE_INSENSITIVE)

    init {
        upStyle()
    }

    /**
     * 获取拆分完的章节数据
     */
    fun getTextChapter(
        book: Book,
        bookChapter: BookChapter,
        contents: List<String>,
        chapterSize: Int
    ): TextChapter {
        val textPages = arrayListOf<TextPage>()
        val pageLines = arrayListOf<Int>()
        val pageLengths = arrayListOf<Int>()
        val stringBuilder = StringBuilder()
        var durY = 0f
        textPages.add(TextPage())
        contents.forEachIndexed { index, text ->
            val matcher = srcPattern.matcher(text)
            if (matcher.find()) {
                var src = matcher.group(1)
                if (!book.isEpub()) {
                    src = NetworkUtils.getAbsoluteURL(bookChapter.url, src)
                }
                src?.let {
                    durY =
                        setTypeImage(
                            book, bookChapter, src, durY, textPages
                        )
                }
            } else {
                val isTitle = index == 0
                if (!(isTitle && ReadBookConfig.titleMode == 2)) {
                    durY =
                        setTypeText(
                            text, durY, textPages, pageLines,
                            pageLengths, stringBuilder, isTitle
                        )
                }
            }
        }
        textPages.last().height = durY + 20.dp
        textPages.last().text = stringBuilder.toString()
        if (pageLines.size < textPages.size) {
            pageLines.add(textPages.last().textLines.size)
        }
        if (pageLengths.size < textPages.size) {
            pageLengths.add(textPages.last().text.length)
        }
        textPages.forEachIndexed { index, item ->
            item.index = index
            item.pageSize = textPages.size
            item.chapterIndex = bookChapter.index
            item.chapterSize = chapterSize
            item.title = bookChapter.title
            item.upLinesPosition()
        }

        return TextChapter(
            bookChapter.index,
            bookChapter.title,
            bookChapter.url,
            textPages,
            pageLines,
            pageLengths,
            chapterSize
        )
    }

    private fun setTypeImage(
        book: Book,
        chapter: BookChapter,
        src: String,
        y: Float,
        textPages: ArrayList<TextPage>
    ): Float {
        var durY = y
        ImageProvider.getImage(book, chapter.index, src)?.let {
            var height = it.height
            var width = it.width
            if (it.width > visibleWidth) {
                height = it.height * visibleWidth / it.width
                width =
                    visibleWidth
            }
            if (height > visibleHeight) {
                width = width * visibleHeight / height
                height =
                    visibleHeight
            }
            if (durY + height > visibleHeight) {
                textPages.add(TextPage())
                durY = 0f
            }
            val textLine = TextLine(isImage = true)
            textLine.lineTop = durY
            durY += height
            textLine.lineBottom = durY
            val (start, end) = if (visibleWidth > width) {
                val adjustWidth = (visibleWidth - width) / 2f
                Pair(
                    paddingLeft.toFloat() + adjustWidth,
                    paddingLeft.toFloat() + adjustWidth + width
                )
            } else {
                Pair(paddingLeft.toFloat(), (paddingLeft + width).toFloat())
            }
            textLine.textChars.add(
                TextChar(
                    charData = src,
                    start = start,
                    end = end,
                    isImage = true
                )
            )
            textPages.last().textLines.add(textLine)
        }
        return durY + paragraphSpacing / 10f
    }

    /**
     * 排版文字
     */
    private fun setTypeText(
        text: String,
        y: Float,
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        isTitle: Boolean
    ): Float {
        var durY = if (isTitle) y + titleTopSpacing else y
        val textPaint = if (isTitle) titlePaint else contentPaint
        val layout = StaticLayout(
            text, textPaint,
            visibleWidth,
            Layout.Alignment.ALIGN_NORMAL, 0f, 0f, true
        )
        for (lineIndex in 0 until layout.lineCount) {
            val textLine = TextLine(isTitle = isTitle)
            val words =
                text.substring(layout.getLineStart(lineIndex), layout.getLineEnd(lineIndex))
            val desiredWidth = layout.getLineWidth(lineIndex)
            var isLastLine = false
            if (lineIndex == 0 && layout.lineCount > 1 && !isTitle) {
                //第一行
                textLine.text = words
                addCharsToLineFirst(
                    textLine,
                    words.toStringArray(),
                    textPaint,
                    desiredWidth
                )
            } else if (lineIndex == layout.lineCount - 1) {
                //最后一行
                textLine.text = "$words\n"
                isLastLine = true
                val x = if (isTitle && ReadBookConfig.titleMode == 1)
                    (visibleWidth - layout.getLineWidth(lineIndex)) / 2
                else 0f
                addCharsToLineLast(
                    textLine,
                    words.toStringArray(),
                    textPaint,
                    x
                )
            } else {
                //中间行
                textLine.text = words
                addCharsToLineMiddle(
                    textLine,
                    words.toStringArray(),
                    textPaint,
                    desiredWidth,
                    0f
                )
            }
            if (durY + textPaint.textHeight > visibleHeight) {
                //当前页面结束,设置各种值
                textPages.last().text = stringBuilder.toString()
                pageLines.add(textPages.last().textLines.size)
                pageLengths.add(textPages.last().text.length)
                textPages.last().height = durY
                //新建页面
                textPages.add(TextPage())
                stringBuilder.clear()
                durY = 0f
            }
            stringBuilder.append(words)
            if (isLastLine) stringBuilder.append("\n")
            textPages.last().textLines.add(textLine)
            textLine.upTopBottom(durY, textPaint)
            durY += textPaint.textHeight * lineSpacingExtra / 10f
            textPages.last().height = durY
        }
        if (isTitle) durY += titleBottomSpacing
        durY += textPaint.textHeight * paragraphSpacing / 10f
        return durY
    }

    /**
     * 有缩进,两端对齐
     */
    private fun addCharsToLineFirst(
        textLine: TextLine,
        words: Array<String>,
        textPaint: TextPaint,
        desiredWidth: Float
    ) {
        var x = 0f
        if (!ReadBookConfig.textFullJustify) {
            addCharsToLineLast(
                textLine,
                words,
                textPaint,
                x
            )
            return
        }
        val bodyIndent = ReadBookConfig.bodyIndent
        val icw = StaticLayout.getDesiredWidth(bodyIndent, textPaint) / bodyIndent.length
        bodyIndent.toStringArray().forEach {
            val x1 = x + icw
            textLine.addTextChar(
                charData = it,
                start = paddingLeft + x,
                end = paddingLeft + x1
            )
            x = x1
        }
        val words1 = words.copyOfRange(bodyIndent.length, words.size)
        addCharsToLineMiddle(
            textLine,
            words1,
            textPaint,
            desiredWidth,
            x
        )
    }

    /**
     * 无缩进,两端对齐
     */
    private fun addCharsToLineMiddle(
        textLine: TextLine,
        words: Array<String>,
        textPaint: TextPaint,
        desiredWidth: Float,
        startX: Float
    ) {
        if (!ReadBookConfig.textFullJustify) {
            addCharsToLineLast(
                textLine,
                words,
                textPaint,
                startX
            )
            return
        }
        val gapCount: Int = words.lastIndex
        val d = (visibleWidth - desiredWidth) / gapCount
        var x = startX
        words.forEachIndexed { index, s ->
            val cw = StaticLayout.getDesiredWidth(s, textPaint)
            val x1 = if (index != words.lastIndex) (x + cw + d) else (x + cw)
            textLine.addTextChar(
                charData = s,
                start = paddingLeft + x,
                end = paddingLeft + x1
            )
            x = x1
        }
        exceed(
            textLine,
            words
        )
    }

    /**
     * 最后一行,自然排列
     */
    private fun addCharsToLineLast(
        textLine: TextLine,
        words: Array<String>,
        textPaint: TextPaint,
        startX: Float
    ) {
        var x = startX
        words.forEach {
            val cw = StaticLayout.getDesiredWidth(it, textPaint)
            val x1 = x + cw
            textLine.addTextChar(
                charData = it,
                start = paddingLeft + x,
                end = paddingLeft + x1
            )
            x = x1
        }
        exceed(
            textLine,
            words
        )
    }

    /**
     * 超出边界处理
     */
    private fun exceed(textLine: TextLine, words: Array<String>) {
        val endX = textLine.textChars.last().end
        if (endX > visibleRight) {
            val cc = (endX - visibleRight) / words.size
            for (i in 0..words.lastIndex) {
                textLine.getTextCharReverseAt(i).let {
                    val py = cc * (words.size - i)
                    it.start = it.start - py
                    it.end = it.end - py
                }
            }
        }
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        typeface = try {
            val fontPath = App.INSTANCE.getPrefString(PreferKey.readBookFont)
            if (!TextUtils.isEmpty(fontPath)) {
                Typeface.createFromFile(fontPath)
            } else {
                when (AppConfig.systemTypefaces) {
                    1 -> Typeface.SERIF
                    2 -> Typeface.MONOSPACE
                    else -> Typeface.SANS_SERIF
                }
            }
        } catch (e: Exception) {
            App.INSTANCE.removePref(PreferKey.readBookFont)
            Typeface.SANS_SERIF
        }
        // 字体统一处理
        val bold = Typeface.create(typeface, Typeface.BOLD)
        val normal = Typeface.create(typeface, Typeface.NORMAL)
        val (titleFont, textFont) = when (ReadBookConfig.textBold) {
            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    Pair(Typeface.create(typeface, 900, false), bold)
                else
                    Pair(bold, bold)
            }
            2 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    Pair(normal, Typeface.create(typeface, 300, false))
                else
                    Pair(normal, normal)
            }
            else -> Pair(bold, normal)
        }

        //标题
        titlePaint = TextPaint()
        titlePaint.color = ReadBookConfig.textColor
        titlePaint.letterSpacing = ReadBookConfig.letterSpacing
        titlePaint.typeface = titleFont
        titlePaint.textSize = with(ReadBookConfig) { textSize + titleSize }.sp.toFloat()
        titlePaint.isAntiAlias = true
        //正文
        contentPaint = TextPaint()
        contentPaint.color = ReadBookConfig.textColor
        contentPaint.letterSpacing = ReadBookConfig.letterSpacing
        contentPaint.typeface = textFont
        contentPaint.textSize = ReadBookConfig.textSize.sp.toFloat()
        contentPaint.isAntiAlias = true
        //间距
        lineSpacingExtra = ReadBookConfig.lineSpacingExtra
        paragraphSpacing = ReadBookConfig.paragraphSpacing
        titleTopSpacing = ReadBookConfig.titleTopSpacing.dp
        titleBottomSpacing = ReadBookConfig.titleBottomSpacing.dp
        upViewSize()
    }

    /**
     * 更新View尺寸
     */
    fun upViewSize() {
        paddingLeft = ReadBookConfig.paddingLeft.dp
        paddingTop = ReadBookConfig.paddingTop.dp
        visibleWidth = viewWidth - paddingLeft - ReadBookConfig.paddingRight.dp
        visibleHeight = viewHeight - paddingTop - ReadBookConfig.paddingBottom.dp
        visibleRight = paddingLeft + visibleWidth
        visibleBottom = paddingTop + visibleHeight
    }

    val TextPaint.textHeight: Float
        get() = fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading
}