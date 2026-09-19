package com.gratia.music.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.gratia.music.provider.ytmusic.innertube.PlayerClient

/**
 * The data source chain every player read goes through, in one place so the
 * player and read-ahead cannot drift apart.
 *
 * Three things are stacked here, innermost first:
 *
 * 1. **[ChunkedDataSource]** — fetches googlevideo as a run of bounded ranges.
 *    An open-ended read of one is paced to roughly playback speed, which is why
 *    a stream can resolve perfectly and still never produce sound.
 * 2. **[ResolvingDataSource]** — dresses each request as the client that minted
 *    the URL. googlevideo bakes the client into the URL as `c=`/`cver=` and
 *    compares it against the headers of the request that comes back for the
 *    bytes; a mismatch is treated as reason enough to throttle the response to
 *    a crawl or refuse it outright with a 403. The right User-Agent depends on
 *    which client produced the URL, so it is set per request rather than once
 *    on the factory.
 * 3. **[DefaultDataSource]** — so `file://`, `content://` and everything else
 *    local still plays through the same chain as a stream.
 */
@UnstableApi
object PlaybackDataSources {

    /**
     * How much is asked for in one range.
     *
     * Two megabytes, and it wants to stay a round multiple of the probe's own
     * request: a URL that grudges listening-sized reads while still answering
     * token ones would otherwise sail through the pre-flight check and fail on
     * the playback path instead — which is the one place a failure costs
     * anything.
     */
    const val STREAM_CHUNK_BYTES = 2L * 1024 * 1024

    /** Generous enough for a slow mobile handover, short of hanging on nothing. */
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    fun create(context: Context): DataSource.Factory {
        // Deliberately no user agent on this factory: DefaultHttpDataSource
        // *appends* the factory's value rather than replacing a request's, so
        // setting one here as well would send two contradictory User-Agent
        // headers and get the bytes refused. The per-request header below is
        // the only one.
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            // googlevideo hands back a redirect before it serves a range, and
            // the hop from an https host to another protocol is normal here.
            .setAllowCrossProtocolRedirects(true)

        val chunked = ChunkedDataSource.Factory(http, STREAM_CHUNK_BYTES)

        // Innermost, so it chunks the real googlevideo URL and not the media
        // item's own — by the time a request reaches here, everything above has
        // already substituted the stream in.
        val resolving = ResolvingDataSource.Factory(chunked) { dataSpec ->
            val url = dataSpec.uri.toString()
            if (!url.startsWith("http")) {
                dataSpec
            } else {
                dataSpec.buildUpon()
                    .setHttpRequestHeaders(PlayerClient.forStreamUrl(url).mediaHeaders())
                    .build()
            }
        }

        return DefaultDataSource.Factory(context, resolving)
    }
}
