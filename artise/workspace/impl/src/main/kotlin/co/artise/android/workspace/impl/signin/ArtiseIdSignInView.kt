/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.workspace.impl.R
import co.artise.android.workspace.impl.artiseid.ArtiseIdProvider
import co.artise.android.workspace.impl.artiseid.ArtiseIdWorkspace
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import kotlinx.collections.immutable.ImmutableList
import co.artise.android.appicon.R as AppIconR

/**
 * The first sign-in screen when Artise ID is offered: "Artise" with a button per way in, then the person's workspaces (or how to get an
 * invite when there are none). [openingProblem] is a problem opening the chosen workspace, from the workspace step.
 */
@Composable
fun ArtiseIdSignInView(
    state: ArtiseIdSignInState,
    isOpening: Boolean,
    openingProblem: String?,
    onTypeWorkspaceClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = {}, navigationIcon = { BackButton(onClick = onBackClick) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            when (val screen = state.screen) {
                ArtiseIdScreen.Checking, ArtiseIdScreen.Unavailable -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ArtiseIdScreen.Providers -> Providers(
                    screen = screen,
                    onSignIn = { state.eventSink(ArtiseIdSignInEvent.SignIn(it)) },
                )
                is ArtiseIdScreen.Workspaces -> Workspaces(
                    workspaces = screen.workspaces,
                    isOpening = isOpening,
                    problem = openingProblem,
                    onChoose = { state.eventSink(ArtiseIdSignInEvent.Choose(it)) },
                )
                ArtiseIdScreen.NoWorkspaces -> IconTitleSubtitleMolecule(
                    modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                    iconStyle = BigIcon.Style.Default(CompoundIcons.Company()),
                    title = stringResource(R.string.screen_artise_id_no_workspaces_title),
                    subTitle = stringResource(R.string.screen_artise_id_no_workspaces),
                )
            }
            Spacer(Modifier.weight(1f).height(24.dp))
            TextButton(
                text = stringResource(R.string.screen_artise_id_type_workspace),
                onClick = onTypeWorkspaceClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColumnScope.Providers(
    screen: ArtiseIdScreen.Providers,
    onSignIn: (ArtiseIdProvider?) -> Unit,
) {
    // The tapped button shows the progress; the others wait.
    var tapped by remember { mutableStateOf<ArtiseIdProvider?>(null) }
    var emailTapped by remember { mutableStateOf(false) }
    fun signIn(provider: ArtiseIdProvider?) {
        tapped = provider
        emailTapped = provider == null
        onSignIn(provider)
    }
    Image(
        painter = painterResource(AppIconR.drawable.onboarding_logo),
        contentDescription = null,
        modifier = Modifier
            .padding(top = 32.dp)
            .size(96.dp)
            .align(Alignment.CenterHorizontally),
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.screen_artise_id_title),
        style = ElementTheme.typography.fontHeadingLgBold,
        color = ElementTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.screen_artise_id_subtitle),
        style = ElementTheme.typography.fontBodyLgRegular,
        color = ElementTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(40.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        screen.providers.forEach { provider ->
            OutlinedButton(
                text = stringResource(provider.label()),
                onClick = { signIn(provider) },
                enabled = !screen.isBusy,
                showProgress = screen.isBusy && !emailTapped && tapped == provider,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            text = stringResource(R.string.screen_artise_id_continue_email),
            onClick = { signIn(null) },
            enabled = !screen.isBusy,
            showProgress = screen.isBusy && emailTapped,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    screen.problem?.let { problem ->
        Spacer(Modifier.height(16.dp))
        ProblemText(
            stringResource(
                when (problem) {
                    ArtiseIdProblem.SignInFailed -> R.string.screen_artise_id_error_sign_in
                    ArtiseIdProblem.Unreachable -> R.string.screen_workspace_error_unreachable
                }
            )
        )
    }
}

@Composable
private fun ColumnScope.Workspaces(
    workspaces: ImmutableList<ArtiseIdWorkspace>,
    isOpening: Boolean,
    problem: String?,
    onChoose: (ArtiseIdWorkspace) -> Unit,
) {
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
        iconStyle = BigIcon.Style.Default(CompoundIcons.Company()),
        title = stringResource(R.string.screen_artise_id_workspaces_title),
        subTitle = null,
    )
    workspaces.forEach { workspace ->
        ListItem(
            supportingContent = { Text(workspace.serverName, style = ElementTheme.typography.fontBodySmRegular) },
            enabled = !isOpening,
            onClick = { onChoose(workspace) },
        ) {
            Text(workspace.name)
        }
    }
    if (isOpening) {
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), Alignment.Center) { CircularProgressIndicator() }
    }
    problem?.let {
        Spacer(Modifier.height(16.dp))
        ProblemText(it)
    }
}

@Composable
private fun ProblemText(text: String) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textCriticalPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun ArtiseIdProvider.label(): Int = when (this) {
    ArtiseIdProvider.GOOGLE -> R.string.screen_artise_id_continue_google
    ArtiseIdProvider.MICROSOFT -> R.string.screen_artise_id_continue_microsoft
    ArtiseIdProvider.APPLE -> R.string.screen_artise_id_continue_apple
}

@PreviewsDayNight
@Composable
internal fun ArtiseIdSignInViewPreview(@PreviewParameter(ArtiseIdSignInStatePreviewParam::class) state: ArtiseIdSignInState) = ElementPreview {
    ArtiseIdSignInView(state = state, isOpening = false, openingProblem = null, onTypeWorkspaceClick = {}, onBackClick = {})
}
