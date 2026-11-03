package com.euphony.player.playback

/** One queue entry as saved for resuming; enough to show and play it before the library has loaded. */
data class QueueItem(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
)

/** Where playback was: [mediaId] double-checks [index] in case the saved queue and position disagree. */
data class ResumePosition(
    val index: Int,
    val mediaId: String,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/** Saves the queue as plain text, one tab-separated item per line. Pure, so it is unit tested. */
object ResumeCodec {

    private const val HEADER = "euphony-queue 1"

    fun encode(items: List<QueueItem>): String = buildString {
        append(HEADER).append('\n')
        items.forEach { item ->
            listOf(item.mediaId, item.uri, item.title, item.artist, item.album).joinTo(this, "\t") { escape(it) }
            append('\n')
        }
    }

    /** Lines that don't parse are skipped; anything without the header gives an empty queue. */
    fun decode(text: String): List<QueueItem> {
        val lines = text.split('\n')
        if (lines.firstOrNull()?.trim() != HEADER) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val fields = line.trimEnd('\r').split('\t').map(::unescape)
            if (fields.size < 5 || fields[0].isEmpty() || fields[1].isEmpty()) return@mapNotNull null
            QueueItem(fields[0], fields[1], fields[2], fields[3], fields[4])
        }
    }

    /** The queue index to resume at, or -1 when there's nothing to resume. */
    fun startIndex(items: List<QueueItem>, position: ResumePosition?): Int {
        if (items.isEmpty()) return -1
        if (position == null) return 0
        if (items.getOrNull(position.index)?.mediaId == position.mediaId) return position.index
        val byId = items.indexOfFirst { it.mediaId == position.mediaId }
        return if (byId >= 0) byId else position.index.coerceIn(items.indices)
    }

    /** Resume where the song was, unless the queue moved on to a different song. */
    fun startPosition(items: List<QueueItem>, position: ResumePosition?, index: Int): Long =
        if (position != null && items.getOrNull(index)?.mediaId == position.mediaId) position.positionMs.coerceAtLeast(0) else 0L

    private fun escape(s: String) = buildString {
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
