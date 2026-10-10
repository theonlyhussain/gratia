package com.gratia.music.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download ledger's contract: states are keyed by the stable recording
 * identity, a failure is a named state — never a silent nothing, never a fake
 * "downloaded" — and completion is terminal rather than absent, so a surface
 * holding an older `SongEntity` snapshot still reports the file that is
 * actually on disk.
 */
class DownloadStateHolderTest {

    @Test
    fun `unknown key reads as not downloaded`() {
        val holder = DownloadStateHolder()
        assertEquals(DownloadState.NotDownloaded, holder.stateFor("nope"))
    }

    @Test
    fun `progress updates are visible per key`() {
        val holder = DownloadStateHolder()
        holder.setResolving("abc")
        assertTrue(holder.stateFor("abc") is DownloadState.Resolving)

        holder.setDownloading("abc", progress = null)
        assertTrue(holder.stateFor("abc") is DownloadState.Downloading)

        holder.setDownloading("abc", progress = 0.42f)
        val state = holder.stateFor("abc")
        assertTrue(state is DownloadState.Downloading)
        assertEquals(0.42f, (state as DownloadState.Downloading).progress!!, 0.001f)
    }

    @Test
    fun `resolution and transfer are distinct states`() {
        // "Preparing" and "Downloading" fail differently and a UI that
        // conflates them claims bytes are moving when none are.
        val holder = DownloadStateHolder()
        holder.setResolving("abc")
        assertTrue(holder.stateFor("abc") is DownloadState.Resolving)
        assertTrue(holder.stateFor("abc") !is DownloadState.Downloading)
        holder.setDownloading("abc", progress = 0.5f)
        assertTrue(holder.stateFor("abc") is DownloadState.Downloading)
        assertTrue(holder.stateFor("abc") !is DownloadState.Resolving)
    }

    @Test
    fun `other tracks are unaffected by one track's progress`() {
        val holder = DownloadStateHolder()
        holder.setDownloading("a", progress = 0.5f)
        assertEquals(DownloadState.NotDownloaded, holder.stateFor("b"))
    }

    @Test
    fun `completion is terminal and survives a stale snapshot`() {
        val holder = DownloadStateHolder()
        holder.setDownloading("abc", progress = 0.99f)
        holder.setDownloaded("abc")
        // Room holds the durable copy, but a song menu opened from a list read
        // before the bytes landed is still holding its own snapshot of the
        // track — with no flag set on it. If completion emptied the ledger,
        // that menu would offer Download again seconds after the file was on
        // disk. The ledger keeps the answer instead.
        assertEquals(DownloadState.Downloaded, holder.stateFor("abc"))
    }

    @Test
    fun `removing the file releases the completed entry`() {
        val holder = DownloadStateHolder()
        holder.setDownloaded("abc")
        holder.clear("abc")
        assertEquals(DownloadState.NotDownloaded, holder.stateFor("abc"))
    }

    @Test
    fun `a fresh download restarts from a completed entry`() {
        val holder = DownloadStateHolder()
        holder.setDownloaded("abc")
        holder.setDownloading("abc", progress = 0.1f)
        assertTrue(holder.stateFor("abc") is DownloadState.Downloading)
    }

    @Test
    fun `failure is a named state carrying the reason`() {
        val holder = DownloadStateHolder()
        holder.setDownloading("abc", progress = 0.3f)
        holder.setFailed("abc", "Server returned 503")
        val state = holder.stateFor("abc")
        assertTrue(state is DownloadState.Failed)
        assertEquals("Server returned 503", (state as DownloadState.Failed).message)
    }

    @Test
    fun `failure can be retried by starting again`() {
        val holder = DownloadStateHolder()
        holder.setFailed("abc", "no network")
        holder.setDownloading("abc", progress = null)
        assertTrue(holder.stateFor("abc") is DownloadState.Downloading)
    }
}
