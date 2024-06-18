package com.getcode.ui.tips.definitions

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.theme.CodeTheme
import dev.bmcreations.tipkit.Tip
import dev.bmcreations.tipkit.engines.EligibilityCriteria
import dev.bmcreations.tipkit.engines.EventEngine
import dev.bmcreations.tipkit.engines.TipsEngine
import dev.bmcreations.tipkit.engines.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days


@Singleton
class DownloadCodeTip @Inject constructor(
    eventEngine: EventEngine,
    tipsEngine: TipsEngine,
): Tip(eventEngine, tipsEngine), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    val homeOpen = Trigger(
        id = "home-open",
        engine = eventEngine
    ).also { await(it) }

    override fun showClose(): Boolean = false

    override fun message(): @Composable () -> Unit {
        return {
            Text(
                text = stringResource(R.string.tooltip_tapLogo),
                style = CodeTheme.typography.textMedium
            )
        }
    }

    override suspend fun criteria(): List<EligibilityCriteria> {
        val homeOpenCount = homeOpen.events.firstOrNull().orEmpty().count()


        val now = Clock.System.now()
        return listOf(
            { homeOpenCount >= 1 },
            {
                val accountCreatedAtMillis = SessionManager.getOrganizer()?.createdAtMillis
                val millis = accountCreatedAtMillis ?: return@listOf false
                val createdAt = Instant.fromEpochMilliseconds(millis)
                createdAt.plus(1.days) < now
            }
        )
    }
}
