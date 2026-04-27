package com.flipcash.app.updates

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseStageResolverTest {

    private fun manifest(
        internal: Int? = null,
        alpha: Int? = null,
        beta: Int? = null,
        production: Int? = null,
    ) = ReleaseManifest(
        tracks = ReleaseManifest.Tracks(
            internal = internal?.let { TrackInfo(versionCode = it) },
            alpha = alpha?.let { TrackInfo(versionCode = it) },
            beta = beta?.let { TrackInfo(versionCode = it) },
            production = production?.let { TrackInfo(versionCode = it) },
        )
    )

    @Test
    fun `matches internal track`() {
        val result = resolveStage(manifest(internal = 100, production = 90), versionCode = 100)
        assertEquals(ReleaseStage.Internal, result)
    }

    @Test
    fun `matches alpha track`() {
        val result = resolveStage(manifest(alpha = 100, production = 90), versionCode = 100)
        assertEquals(ReleaseStage.Alpha, result)
    }

    @Test
    fun `matches beta track`() {
        val result = resolveStage(manifest(beta = 100, production = 90), versionCode = 100)
        assertEquals(ReleaseStage.Beta, result)
    }

    @Test
    fun `matches production track`() {
        val result = resolveStage(manifest(production = 100), versionCode = 100)
        assertEquals(ReleaseStage.Production, result)
    }

    @Test
    fun `internal takes priority over alpha when same versionCode`() {
        val result = resolveStage(manifest(internal = 100, alpha = 100, production = 90), versionCode = 100)
        assertEquals(ReleaseStage.Internal, result)
    }

    @Test
    fun `alpha takes priority over beta when same versionCode`() {
        val result = resolveStage(manifest(alpha = 100, beta = 100, production = 90), versionCode = 100)
        assertEquals(ReleaseStage.Alpha, result)
    }

    @Test
    fun `versionCode above production falls back to internal`() {
        val result = resolveStage(manifest(production = 90), versionCode = 100)
        assertEquals(ReleaseStage.Internal, result)
    }

    @Test
    fun `versionCode below production falls back to production`() {
        val result = resolveStage(manifest(production = 100), versionCode = 90)
        assertEquals(ReleaseStage.Production, result)
    }

    @Test
    fun `no production track falls back to production`() {
        val result = resolveStage(manifest(), versionCode = 100)
        assertEquals(ReleaseStage.Production, result)
    }

    @Test
    fun `empty manifest falls back to production`() {
        val result = resolveStage(ReleaseManifest(), versionCode = 100)
        assertEquals(ReleaseStage.Production, result)
    }
}
