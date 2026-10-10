package com.tazzzo.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.appVersionLabel
import com.tazzzo.app.data.account.NameSave
import com.tazzzo.app.data.account.ProfileState
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.content.LegalSlug
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.checkout.Header
import com.tazzzo.app.ui.checkout.TextAction
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.TazzzoWordmark
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import kotlinx.coroutines.launch

/*
 * Profile and About in REMOTE mode (UI-07). Every row is a real destination; every state is truthful (see ProfileUiModel.kt for
 * the capability audit). Help & support and Legal live in ui/support. Logout goes through the existing `TazzzoAppState.logout()` (server revoke,
 * then unconditional local sign-out) behind an explicit confirmation sheet.
 */

// ---- Profile ------------------------------------------------------------------------------------------------------------

/** The PROFILE tab in REMOTE mode. */
@Composable
fun RemoteProfileContent() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val book by ServiceLocator.addressBook.state.collectAsState()
    val profileStore = ServiceLocator.profileStore
    val profile by profileStore.state.collectAsState()
    val save by profileStore.save.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    LaunchedEffect(app.isAuthenticated) { if (app.isAuthenticated) profileStore.ensureLoaded() }
    // A save that went through closes the editor.
    LaunchedEffect(save) { if (save == NameSave.Saved) { editingName = false; profileStore.acknowledgeSave() } }
    val loaded = (profile as? ProfileState.Loaded)?.profile
    ProfileLayout(
        signedIn = app.isAuthenticated,
        identity = remoteIdentity(loaded),
        addressCount = (book as? BookState.Loaded)?.addresses?.size,
        version = appVersionLabel(),
        confirmLogout = confirmLogout,
        canEditName = loaded != null,
        actions = ProfileActions(
            login = { app.navigate(Screen.Login) },
            open = { e ->
                when (e) {
                    ProfileEntry.ADDRESSES -> app.navigate(Screen.Addresses)
                    ProfileEntry.ORDERS -> app.homeTab = HomeTab.ORDERS
                    ProfileEntry.HELP -> app.navigate(Screen.Help)
                    ProfileEntry.TERMS -> app.navigate(Screen.Legal(LegalSlug.TERMS.path))
                    ProfileEntry.PRIVACY -> app.navigate(Screen.Legal(LegalSlug.PRIVACY.path))
                    ProfileEntry.ABOUT -> app.navigate(Screen.About)
                }
            },
            askLogout = { confirmLogout = true },
            cancelLogout = { confirmLogout = false },
            confirmLogoutNow = { confirmLogout = false; scope.launch { app.logout() } },
            editName = { profileStore.acknowledgeSave(); editingName = true }
        )
    )
    if (editingName && loaded != null) {
        NameEditor(
            initial = loaded.displayName.orEmpty(), saving = save == NameSave.Saving, serverMessage = save.message(),
            onSave = { profileStore.saveDisplayName(it) }, onDismiss = { editingName = false; profileStore.acknowledgeSave() }
        )
    }
}

class ProfileActions(
    val login: () -> Unit, val open: (ProfileEntry) -> Unit,
    val askLogout: () -> Unit, val cancelLogout: () -> Unit, val confirmLogoutNow: () -> Unit,
    val editName: () -> Unit = {}
)

@Composable
fun ProfileLayout(
    signedIn: Boolean, identity: ProfileIdentity, addressCount: Int?, version: String, confirmLogout: Boolean, actions: ProfileActions,
    canEditName: Boolean = false
) {
    Box(Modifier.fillMaxSize().background(TazColors.Cream).testTag("profile")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.lg)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = TazSpace.lg, bottom = TazSpace.lg)) {
                EditorialText(listOf(plain(ProfileCopy.TITLE)), size = TazType.editorialHeadlineSize, lineHeight = TazType.editorialHeadlineLine, color = TazColors.BrandEditorial, textAlign = TextAlign.Start)
            }
            if (signedIn) {
                IdentityCard(identity, onEdit = if (canEditName) actions.editName else null)
                Spacer(Modifier.height(TazSpace.xl))
                SectionLabel(ProfileCopy.SECTION_ACCOUNT)
                RowsCard(ACCOUNT_ENTRIES, addressCount, actions.open)
                Spacer(Modifier.height(TazSpace.xl))
                SectionLabel(ProfileCopy.SECTION_MORE)
                RowsCard(MORE_ENTRIES, addressCount, actions.open)
                Spacer(Modifier.height(TazSpace.xl))
                LogoutRow(onClick = actions.askLogout)
            } else {
                SignedOutCard(onLogin = actions.login)
                Spacer(Modifier.height(TazSpace.xl))
                SectionLabel(ProfileCopy.SECTION_MORE)
                RowsCard(profileEntries(false), null, actions.open)
            }
            Spacer(Modifier.height(TazSpace.xl))
            Text("${ProfileCopy.VERSION_PREFIX} $version", fontSize = TazType.microSize, color = TazColors.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("appVersion"))
            Spacer(Modifier.height(TazSize.floatingNavClearance))
        }
        if (confirmLogout) LogoutSheet(onCancel = actions.cancelLogout, onConfirm = actions.confirmLogoutNow)
    }
}

/**
 * Who is signed in: the profile's display name and email when set (`GET /v1/customer/profile`), otherwise "Signed in". The
 * contract has no phone number, so none is shown; nothing is invented. [onEdit] (when the profile loaded) adds "Edit name".
 */
@Composable
private fun IdentityCard(identity: ProfileIdentity, onEdit: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.lg), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(TazColors.GreenSoft), contentAlignment = Alignment.Center) {
            TazIcon(TazIcons.Profile, null, size = TazSize.iconLg, tint = TazColors.BrandEditorial)
        }
        Spacer(Modifier.width(TazSpace.lg))
        Column(Modifier.weight(1f)) {
            EditorialText(listOf(plain(identity.name ?: ProfileCopy.SIGNED_IN)), size = TazType.editorialSectionSize, lineHeight = TazType.editorialSectionLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start)
            val line = listOfNotNull(identity.phone, identity.email).joinToString(" · ").ifBlank { ProfileCopy.SIGNED_IN_BODY }
            Text(line, fontSize = TazType.captionSize, lineHeight = TazType.captionLine, color = TazColors.TextSecondary)
            if (onEdit != null) {
                Spacer(Modifier.height(TazSpace.xs))
                TextAction(if (identity.name == null) ProfileCopy.ADD_NAME else ProfileCopy.EDIT_NAME, onClick = onEdit)
            }
        }
    }
}

/** Set or clear the display name. Checked locally with the backend's rule before PATCH; server outcomes are app-written copy. */
@Composable
private fun NameEditor(initial: String, saving: Boolean, serverMessage: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val problem = nameProblem(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TazColors.Surface,
        title = { Text(ProfileCopy.NAME_TITLE, fontSize = TazType.titleSize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, isError = problem != null,
                    modifier = Modifier.fillMaxWidth().testTag("displayNameField")
                )
                Spacer(Modifier.height(TazSpace.xs))
                Text(problem ?: serverMessage ?: ProfileCopy.NAME_HINT, fontSize = TazType.captionSize,
                    color = if (problem != null || serverMessage != null) TazColors.Danger else TazColors.TextSecondary)
            }
        },
        confirmButton = { TextAction(ProfileCopy.SAVE, enabled = problem == null && !saving, onClick = { onSave(text) }) },
        dismissButton = { TextAction(ProfileCopy.CANCEL, TazColors.TextSecondary, onClick = onDismiss) }
    )
}

@Composable
private fun SignedOutCard(onLogin: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(TazColors.GreenSoft), contentAlignment = Alignment.Center) { TazIcon(TazIcons.Profile, null, size = 32.dp, tint = TazColors.BrandEditorial) }
        Spacer(Modifier.height(TazSpace.lg))
        EditorialText(listOf(plain(ProfileCopy.SIGNED_OUT_TITLE)), size = TazType.editorialStateTitleSize, lineHeight = TazType.editorialStateTitleLine, color = TazColors.TextPrimary)
        Spacer(Modifier.height(TazSpace.sm))
        Text(ProfileCopy.SIGNED_OUT_BODY, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(TazSpace.xl))
        TazzzoPrimaryButton(ProfileCopy.LOG_IN, onClick = onLogin, modifier = Modifier.fillMaxWidth().testTag("profileLogin"))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.TextSecondary, modifier = Modifier.padding(start = TazSpace.xs, bottom = TazSpace.sm))
}

@Composable
private fun RowsCard(entries: List<ProfileEntry>, addressCount: Int?, onOpen: (ProfileEntry) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface)) {
        entries.forEachIndexed { i, e ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(TazColors.CardBorder))
            val subtitle = when (e) {
                ProfileEntry.ADDRESSES -> addressCount?.let { if (it == 1) "1 saved" else "$it saved" }
                else -> e.subtitle
            }
            ProfileRow(iconFor(e), e.title, subtitle, onClick = { onOpen(e) })
        }
    }
}

private fun iconFor(e: ProfileEntry): ImageVector = when (e) {
    ProfileEntry.ADDRESSES -> TazIcons.Location
    ProfileEntry.ORDERS -> TazIcons.Receipt
    ProfileEntry.HELP -> TazIcons.Help
    ProfileEntry.TERMS, ProfileEntry.PRIVACY -> TazIcons.Info
    ProfileEntry.ABOUT -> TazIcons.Info
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tazPressable(onClick = onClick, pressScale = TazPress.compact, role = Role.Button)
            .semantics { contentDescription = title }.padding(horizontal = TazSpace.lg, vertical = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(TazColors.GreenSoft), contentAlignment = Alignment.Center) { TazIcon(icon, null, size = TazSize.iconSm, tint = TazColors.BrandEditorial) }
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = TazType.titleSize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary)
            subtitle?.let { Text(it, fontSize = TazType.captionSize, color = TazColors.TextSecondary) }
        }
        TazIcon(TazIcons.Forward, null, size = TazSize.iconSm, tint = TazColors.TextTertiary)
    }
}

@Composable
private fun LogoutRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface)
            .tazPressable(onClick = onClick, pressScale = TazPress.compact, role = Role.Button).semantics { contentDescription = ProfileCopy.LOG_OUT }
            .padding(horizontal = TazSpace.lg, vertical = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(TazColors.DangerSoft), contentAlignment = Alignment.Center) { TazIcon(TazIcons.Logout, null, size = TazSize.iconSm, tint = TazColors.Danger) }
        Spacer(Modifier.width(TazSpace.md))
        Text(ProfileCopy.LOG_OUT, fontSize = TazType.titleSize, fontWeight = FontWeight.Medium, color = TazColors.Danger)
    }
}

/** Explicit confirmation: no one-tap sign-out. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LogoutSheet(onCancel: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCancel, containerColor = TazColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = TazSpace.xl).padding(bottom = TazSpace.xxl).navigationBarsPadding().testTag("logoutSheet"), horizontalAlignment = Alignment.CenterHorizontally) {
            EditorialText(listOf(plain(ProfileCopy.LOG_OUT_TITLE)), size = TazType.editorialStateTitleSize, lineHeight = TazType.editorialStateTitleLine, color = TazColors.TextPrimary)
            Spacer(Modifier.height(TazSpace.sm))
            Text(ProfileCopy.LOG_OUT_BODY, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(TazSpace.xl))
            TazzzoPrimaryButton(ProfileCopy.LOG_OUT, onClick = onConfirm, trailingArrow = false, modifier = Modifier.fillMaxWidth().testTag("confirmLogout"))
            Spacer(Modifier.height(TazSpace.sm))
            TextAction(ProfileCopy.CANCEL, TazColors.TextSecondary, onClick = onCancel)
        }
    }
}

// ---- About --------------------------------------------------------------------------------------------------------------

@Composable
fun RemoteAboutScreen() {
    val app = LocalAppState.current
    AboutLayout(version = appVersionLabel(), onBack = { app.back() }, openLegal = { app.navigate(Screen.Legal(it.path)) })
}

/** Truthful About: the wordmark, the canonical tagline, the version, and the two legal documents (in-app, plain text). */
@Composable
fun AboutLayout(version: String, onBack: () -> Unit, openLegal: (LegalSlug) -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(TazColors.Cream).verticalScroll(rememberScrollState()).testTag("about")) {
        Header(ProfileCopy.ABOUT_TITLE, onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(TazSpace.xxl))
            TazzzoWordmark(width = 180.dp)
            Spacer(Modifier.height(TazSpace.md))
            Text(aboutTagline(), fontSize = TazType.taglineSize, letterSpacing = TazType.taglineTracking, color = TazColors.BrandEditorial, textAlign = TextAlign.Center, modifier = Modifier.testTag("tagline"))
            Spacer(Modifier.height(TazSpace.xxl))
            Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.xl)) {
                Text(ProfileCopy.ABOUT_DESCRIPTION, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(TazSpace.lg))
            Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface)) {
                AboutRow(ProfileCopy.VERSION_PREFIX, version, tag = "aboutVersion")
            }
            Spacer(Modifier.height(TazSpace.lg))
            Text(ProfileCopy.LEGAL_SECTION, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.TextSecondary, modifier = Modifier.fillMaxWidth().padding(start = TazSpace.xs, bottom = TazSpace.sm))
            Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface)) {
                ProfileRow(TazIcons.Info, ProfileCopy.TERMS, null, onClick = { openLegal(LegalSlug.TERMS) })
                Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(TazColors.CardBorder))
                ProfileRow(TazIcons.Info, ProfileCopy.PRIVACY, null, onClick = { openLegal(LegalSlug.PRIVACY) })
            }
            Spacer(Modifier.navigationBarsPadding().height(TazSpace.xxl))
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, tag: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg, vertical = TazSpace.md), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = TazType.bodySize, color = TazColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = TazType.bodySize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}
