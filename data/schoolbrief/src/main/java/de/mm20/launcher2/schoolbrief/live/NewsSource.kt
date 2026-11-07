package de.mm20.launcher2.schoolbrief.live

import de.mm20.launcher2.schoolbrief.NewsItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.w3c.dom.Element
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

internal data class NewsCandidate(val topic: String, val title: String, val url: String, val published: OffsetDateTime)

/** Headlines from fixed feeds, read on the phone. Titles and links can only come from here. */
internal object NewsSource {

    val Feeds = mapOf(
        "aviation" to listOf("https://simpleflying.com/feed/"),
        "tech" to listOf("https://www.theverge.com/rss/index.xml", "https://feeds.arstechnica.com/arstechnica/index"),
        "eu" to listOf("https://www.euractiv.com/feed/", "https://www.politico.eu/feed/"),
    )

    /** Up to 15 newest items per topic from the last [hours]; one dead feed never fails the rest. */
    suspend fun fetch(now: OffsetDateTime, hours: Long = 36): Map<String, List<NewsCandidate>> = coroutineScope {
        val cutoff = now.minusHours(hours)
        Feeds.mapValues { (topic, urls) ->
            urls.map { url -> async { runCatching { parse(topic, Http.request(url).body) }.getOrDefault(emptyList()) } }
                .flatMap { it.await() }
                .filter { it.published.isAfter(cutoff) }
                .sortedByDescending { it.published }
                .distinctBy { it.url }
                .take(15)
        }
    }

    /** Without AI: the newest item of each topic. */
    fun newestPerTopic(c: Map<String, List<NewsCandidate>>): List<NewsItem> =
        listOf("aviation", "tech", "eu").mapNotNull { t -> c[t]?.firstOrNull()?.let { NewsItem(t, it.title, it.url) } }

    fun parse(topic: String, xml: String): List<NewsCandidate> {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // No external entities from untrusted feeds.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = f.newDocumentBuilder().parse(xml.byteInputStream())
        val out = mutableListOf<NewsCandidate>()
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val e = items.item(i) as Element
            val title = text(e, "title") ?: continue
            val link = text(e, "link") ?: continue
            val date = date(text(e, "pubDate")) ?: continue
            if (link.startsWith("https://")) out += NewsCandidate(topic, clean(title), link, date)
        }
        val entries = doc.getElementsByTagNameNS("*", "entry")
        for (i in 0 until entries.length) {
            val e = entries.item(i) as Element
            val title = text(e, "title") ?: continue
            val links = e.getElementsByTagNameNS("*", "link")
            var href: String? = null
            for (j in 0 until links.length) {
                val l = links.item(j) as Element
                if (href == null || l.getAttribute("rel") == "alternate") href = l.getAttribute("href")
            }
            val date = date(text(e, "published") ?: text(e, "updated")) ?: continue
            if (href != null && href.startsWith("https://")) out += NewsCandidate(topic, clean(title), href, date)
        }
        return out
    }

    private fun text(e: Element, tag: String): String? {
        val n = e.getElementsByTagNameNS("*", tag).item(0) ?: e.getElementsByTagName(tag).item(0) ?: return null
        return n.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim()

    private fun date(s: String?): OffsetDateTime? {
        if (s.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(s.trim()) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toOffsetDateTime() }.getOrNull()
    }
}
