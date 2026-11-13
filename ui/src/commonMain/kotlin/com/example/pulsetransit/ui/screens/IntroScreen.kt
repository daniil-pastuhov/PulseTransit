package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.IntroPage
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.platform.SystemBackHandler
import com.example.pulsetransit.ui.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The short intro to the chosen mode, between the first-launch choice and the board. Every page
 * can be skipped, which ends the whole intro, as does "Get started" on the last page. Back, the
 * button or the system's, goes a page back, and from the first page to the choice ([onBack]).
 */
@Composable
fun IntroScreen(
    pages: List<IntroPage>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    // Empty for the moment between choosing a mode and the state that follows.
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex
    val back: () -> Unit = {
        if (pagerState.currentPage == 0) onBack() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }
    SystemBackHandler(onBack = back)
    PulseTransitTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.widthIn(max = ONBOARDING_COLUMN_WIDTH).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = back) {
                        Icon(PulseIcons.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                    Spacer(Modifier.weight(1f))
                    // Kept in place but hidden on the last page, so the pages do not shift up.
                    TextButton(
                        onClick = onFinish,
                        enabled = !last,
                        modifier = if (last) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                    ) {
                        Text(stringResource(Res.string.intro_skip))
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                    IntroPageContent(pages[index])
                }
                Row(
                    modifier = Modifier.widthIn(max = ONBOARDING_COLUMN_WIDTH).fillMaxWidth().padding(vertical = FjordDimens.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageDots(count = pages.size, current = pagerState.currentPage)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (last) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        contentPadding = PaddingValues(horizontal = FjordDimens.space6),
                        modifier = Modifier.heightIn(min = FjordDimens.touch),
                    ) {
                        Text(stringResource(if (last) Res.string.intro_done else Res.string.intro_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage) {
    val (title, body) = page.content()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FjordDimens.space4, Alignment.CenterVertically),
    ) {
        IntroIllustration(page)
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

@Composable
private fun PageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.intro_step, current + 1, count)
    Row(
        // "Step 2 of 4", announced as the page changes.
        modifier =
            modifier.semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            // The current step is a short bar, the others dots.
            val on = index == current
            val color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Spacer(modifier = Modifier.size(width = if (on) 24.dp else 8.dp, height = 8.dp).clip(CircleShape).background(color))
        }
    }
}

private fun IntroPage.content(): Pair<StringResource, StringResource> =
    when (this) {
        IntroPage.NEAREST_STOP -> Res.string.intro_nearest_stop_title to Res.string.intro_nearest_stop_body
        IntroPage.ROUTES_FROM_HERE -> Res.string.intro_routes_title to Res.string.intro_routes_body
        IntroPage.RESIZE -> Res.string.intro_resize_title to Res.string.intro_resize_body
        IntroPage.CHOOSE_STOPS -> Res.string.intro_choose_stops_title to Res.string.intro_choose_stops_body
        IntroPage.FASTEST_ROUTES -> Res.string.intro_fastest_routes_title to Res.string.intro_fastest_routes_body
        IntroPage.TAP_FOR_DETAILS -> Res.string.intro_details_title to Res.string.intro_details_body
        IntroPage.SCREEN_CARE -> Res.string.intro_screen_care_title to Res.string.intro_screen_care_body
    }
