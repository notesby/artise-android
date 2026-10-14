/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.workspace.impl.R
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

/** "Your workspace": the name people type to reach their company's server ("acme"), with plain-words problems under it. */
@Composable
fun WorkspaceSignInView(
    state: WorkspaceSignInState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun submit() = state.eventSink(WorkspaceSignInEvent.Continue)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = {}, navigationIcon = { BackButton(onClick = onBackClick) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.Company()),
                title = stringResource(R.string.screen_workspace_title),
                subTitle = stringResource(R.string.screen_workspace_subtitle),
            )
            Spacer(Modifier.height(40.dp))
            TextField(
                value = state.input,
                onValueChange = { state.eventSink(WorkspaceSignInEvent.InputChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.screen_workspace_field_label),
                placeholder = stringResource(R.string.screen_workspace_field_placeholder),
                supportingText = state.problem?.let { problemText(it) },
                validity = if (state.problem != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                enabled = !state.isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                text = stringResource(if (state.problem.isRetryable()) CommonStrings.action_retry else CommonStrings.action_continue),
                onClick = ::submit,
                enabled = state.input.isNotBlank(),
                showProgress = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun WorkspaceProblem?.isRetryable() = when (this) {
    WorkspaceProblem.Unreachable, WorkspaceProblem.TooManyTries, is WorkspaceProblem.ServerUnreachable -> true
    else -> false
}

@Composable
private fun problemText(problem: WorkspaceProblem): String = when (problem) {
    WorkspaceProblem.Invalid -> stringResource(R.string.screen_workspace_error_invalid)
    is WorkspaceProblem.NotFound -> stringResource(R.string.screen_workspace_error_not_found, problem.typed)
    WorkspaceProblem.Paused -> stringResource(R.string.screen_workspace_error_paused)
    WorkspaceProblem.TooManyTries -> stringResource(R.string.screen_workspace_error_too_many_tries)
    WorkspaceProblem.Unreachable -> stringResource(R.string.screen_workspace_error_unreachable)
    is WorkspaceProblem.ServerUnreachable -> stringResource(R.string.screen_workspace_error_server_unreachable, problem.name)
    is WorkspaceProblem.NoPasswordSignIn -> stringResource(R.string.screen_workspace_error_no_password, problem.name)
    WorkspaceProblem.InviteUnavailable -> stringResource(R.string.screen_workspace_error_invite_unavailable)
}

@PreviewsDayNight
@Composable
internal fun WorkspaceSignInViewPreview(@PreviewParameter(WorkspaceSignInStatePreviewParam::class) state: WorkspaceSignInState) = ElementPreview {
    WorkspaceSignInView(state = state, onBackClick = {})
}
