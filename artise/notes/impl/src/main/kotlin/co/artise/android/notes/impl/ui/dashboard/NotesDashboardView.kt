/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import co.artise.android.notes.api.ExportFormat
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.share.ShareAction
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf
import timber.log.Timber

private val dashboardFormats = persistentListOf(ExportFormat.PDF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesDashboardView(
    state: NotesDashboardState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onOpenAsNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.problem == DashboardProblem.NOT_A_DASHBOARD) {
        val open by rememberUpdatedState(onOpenAsNote)
        LaunchedEffect(Unit) { open() }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBackClick) },
                actions = {
                    if (state.canEdit) {
                        IconButton(onClick = onEditClick) {
                            Icon(imageVector = CompoundIcons.Edit(), contentDescription = stringResource(R.string.screen_notes_chart_edit))
                        }
                    }
                    ShareAction(state = state.share, formats = dashboardFormats, title = state.title)
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val html = state.html
            when {
                html != null -> DashboardWebView(html, modifier = Modifier.fillMaxSize())
                state.isLoading -> CircularProgressIndicator()
                state.problem == DashboardProblem.LOAD_FAILED -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.screen_notes_dashboard_load_failed),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        text = stringResource(CommonStrings.action_retry),
                        onClick = { state.eventSink(NotesDashboardEvent.Retry) },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * The server's page in a web view that runs no scripts and opens nothing inside the app: links (rare: wiki links are
 * plain text there) go to the browser. The page's own content policy is put back in, since a page loaded from text
 * loses the server's header: nothing but its inline styles and pictures loads.
 */
@Composable
private fun DashboardWebView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.setGeolocationEnabled(false)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (url.scheme == "https" || url.scheme == "http") {
                            try {
                                view.context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: ActivityNotFoundException) {
                                Timber.w(e, "No browser for a dashboard link")
                            }
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(null, DashboardHtml.withPolicy(html), "text/html", "utf-8", null)
            }
        },
    )
}

/** The server's content policy as a meta tag at the top of the page (see [DashboardWebView]). */
internal object DashboardHtml {
    const val POLICY = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src https:"

    fun withPolicy(html: String): String {
        val meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"$POLICY\">"
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) {
            html.substring(0, head.range.last + 1) + meta + html.substring(head.range.last + 1)
        } else {
            meta + html
        }
    }
}

@PreviewsDayNight
@Composable
internal fun NotesDashboardViewPreview(@PreviewParameter(NotesDashboardStatePreviewParam::class) state: NotesDashboardState) = ElementPreview {
    NotesDashboardView(state = state, onBackClick = {}, onEditClick = {}, onOpenAsNote = {})
}
