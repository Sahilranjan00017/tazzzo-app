package com.tazzzo.app.ui.support

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.content.AppInfo
import com.tazzzo.app.data.content.ContactLinks
import com.tazzzo.app.data.content.FaqGroup
import com.tazzzo.app.data.content.LegalSlug
import com.tazzzo.app.data.order.RemoteOrderDataSource
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.data.support.RemoteSupportDataSource
import com.tazzzo.app.data.support.SupportCase
import com.tazzzo.app.data.support.SupportCategory
import com.tazzzo.app.data.support.SupportRules
import com.tazzzo.app.data.support.SupportWrite
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
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.profile.ProfileCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Help & support, support requests and legal documents (REMOTE; the MOCK Help screen is unchanged). Every server string — FAQ
 * answers, messages, legal paragraphs — is plain Text. The only links opened are tel:/mailto: built by ContactLinks from values
 * that passed a strict check, through Compose's platform UriHandler (Android: an ACTION_VIEW intent; iOS: UIApplication.openURL).
 */

private suspend fun <T> loadOrNull(block: suspend () -> T): T? =
    try { block() } catch (e: CancellationException) { throw e } catch (_: Throwable) { null }

// ---- Help & support ---------------------------------------------------------------------------------------------------------

@Composable
fun RemoteHelpScreen() {
    val app = LocalAppState.current
    val store = ServiceLocator.supportStore
    val cases by store.cases.collectAsState()
    var attempt by remember { mutableStateOf(0) }
    val faqs by produceState<HelpLoad<List<FaqGroup>>>(HelpLoad.Loading, attempt) {
        value = HelpLoad.Loading
        value = loadOrNull { ServiceLocator.helpContent.faqs() }?.let { HelpLoad.Loaded(it) } ?: HelpLoad.Failed
    }
    val info by produceState<HelpLoad<AppInfo>>(HelpLoad.Loading, attempt) {
        value = HelpLoad.Loading
        value = loadOrNull { ServiceLocator.helpContent.appConfig() }?.let { HelpLoad.Loaded(it) } ?: HelpLoad.Failed
    }
    // Idle = never loaded in this session, or a new request was sent: (re)load page 1.
    LaunchedEffect(app.isAuthenticated, cases is com.tazzzo.app.data.catalog.PagedState.Idle) {
        if (app.isAuthenticated && cases is com.tazzzo.app.data.catalog.PagedState.Idle) store.openList()
    }
    HelpLayout(
        faqs = faqs, info = info, requests = requestsSection(app.isAuthenticated, cases),
        actions = HelpActions(
            back = { app.back() },
            retry = { attempt++ },
            retryRequests = { store.refreshList() },
            loadMoreRequests = { store.loadMore() },
            newRequest = { if (app.isAuthenticated) app.navigate(Screen.SupportNew()) else app.navigate(Screen.Login) },
            openRequest = { app.navigate(Screen.SupportCase(it)) },
            signIn = { app.navigate(Screen.Login) }
        )
    )
}

class HelpActions(
    val back: () -> Unit, val retry: () -> Unit, val retryRequests: () -> Unit, val loadMoreRequests: () -> Unit,
    val newRequest: () -> Unit, val openRequest: (String) -> Unit, val signIn: () -> Unit
)

@Composable
fun HelpLayout(faqs: HelpLoad<List<FaqGroup>>, info: HelpLoad<AppInfo>, requests: RequestsSection, actions: HelpActions) {
    Column(Modifier.fillMaxSize().background(TazColors.Cream).testTag("help")) {
        Header(ProfileCopy.HELP_TITLE, onBack = actions.back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
            SectionTitle(ProfileCopy.CONTACT_TITLE)
            ContactCard(info)
            TazzzoPrimaryButton(ProfileCopy.NEW_REQUEST, onClick = actions.newRequest, trailingArrow = false, modifier = Modifier.fillMaxWidth().testTag("newSupportRequest"))

            SectionTitle(ProfileCopy.REQUESTS_TITLE)
            RequestsCard(requests, actions)

            SectionTitle(ProfileCopy.FAQ_TITLE)
            when (faqs) {
                HelpLoad.Loading -> SkeletonBlock(height = 120.dp, corner = TazRadius.tileDp)
                HelpLoad.Failed -> Card { Text(ProfileCopy.FAQ_FAILED, fontSize = TazType.bodySize, color = TazColors.TextSecondary); TextAction(ProfileCopy.TRY_AGAIN, onClick = actions.retry) }
                is HelpLoad.Loaded -> if (faqs.value.isEmpty()) Card { Text(ProfileCopy.FAQ_EMPTY, fontSize = TazType.bodySize, color = TazColors.TextSecondary) }
                    else faqs.value.forEach { FaqGroupCard(it) }
            }
            Spacer(Modifier.navigationBarsPadding().height(TazSpace.xxl))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = TazType.titleSize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary, modifier = Modifier.padding(top = TazSpace.sm))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.xs)) { content() }
}

/** Phone and email from app-config as selectable text; "Call"/"Email" only for a value that passed its strict check. */
@Composable
private fun ContactCard(info: HelpLoad<AppInfo>) {
    val uri = LocalUriHandler.current
    when (info) {
        HelpLoad.Loading -> SkeletonBlock(height = 72.dp, corner = TazRadius.tileDp)
        HelpLoad.Failed -> Card { Text(ProfileCopy.CONTACT_NONE, fontSize = TazType.bodySize, color = TazColors.TextSecondary) }
        is HelpLoad.Loaded -> Card {
            val i = info.value
            if (!i.hasContact) Text(ProfileCopy.CONTACT_NONE, fontSize = TazType.bodySize, color = TazColors.TextSecondary)
            ContactRow(i.supportPhone, ContactLinks.telUri(i.supportPhone), ProfileCopy.CALL) { runCatching { uri.openUri(it) } }
            ContactRow(i.supportEmail, ContactLinks.mailtoUri(i.supportEmail), ProfileCopy.EMAIL) { runCatching { uri.openUri(it) } }
        }
    }
}

@Composable
private fun ContactRow(value: String?, link: String?, actionLabel: String, open: (String) -> Unit) {
    if (value == null) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(Modifier.weight(1f)) { Text(value, fontSize = TazType.bodySize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary) }
        if (link != null) TextAction(actionLabel, onClick = { open(link) })
    }
}

@Composable
private fun RequestsCard(requests: RequestsSection, actions: HelpActions) {
    when (requests) {
        RequestsSection.SignedOut -> Card { Text(ProfileCopy.REQUESTS_SIGNED_OUT, fontSize = TazType.bodySize, color = TazColors.TextSecondary); TextAction(SupportCopy.LOG_IN, onClick = actions.signIn) }
        RequestsSection.Loading -> SkeletonBlock(height = 72.dp, corner = TazRadius.tileDp)
        RequestsSection.Failed -> Card { Text(ProfileCopy.REQUESTS_FAILED, fontSize = TazType.bodySize, color = TazColors.TextSecondary); TextAction(ProfileCopy.TRY_AGAIN, onClick = actions.retryRequests) }
        RequestsSection.Empty -> Card { Text(ProfileCopy.REQUESTS_EMPTY, fontSize = TazType.bodySize, color = TazColors.TextSecondary) }
        is RequestsSection.Content -> Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface)) {
            requests.rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
                Column(
                    Modifier.fillMaxWidth().tazPressable(onClick = { actions.openRequest(r.caseId) }, pressScale = TazPress.compact, role = Role.Button)
                        .semantics { contentDescription = "${r.subject}, ${r.status}" }.padding(TazSpace.lg)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.subject, fontSize = TazType.bodySize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(TazSpace.sm))
                        Text(r.status, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.BrandEditorial)
                    }
                    Text(r.detail, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                }
            }
            if (requests.hasMore || requests.appendFailed) Row(Modifier.fillMaxWidth().padding(TazSpace.sm), horizontalArrangement = Arrangement.Center) {
                if (requests.appending) Text(SupportCopy.LOADING, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                else TextAction(if (requests.appendFailed) ProfileCopy.TRY_AGAIN else ProfileCopy.LOAD_MORE, onClick = actions.loadMoreRequests)
            }
        }
    }
}

/** One FAQ category; each question expands to its plain-text answer. */
@Composable
private fun FaqGroupCard(group: FaqGroup) {
    var open by remember(group.category) { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxWidth()) {
        Text(group.category.label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.TextSecondary, modifier = Modifier.padding(start = TazSpace.xs, bottom = TazSpace.xs))
        Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface)) {
            group.faqs.forEachIndexed { i, f ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
                val expanded = f.id in open
                Column(
                    Modifier.fillMaxWidth().tazPressable(onClick = { open = if (expanded) open - f.id else open + f.id }, pressScale = TazPress.compact, role = Role.Button)
                        .semantics { contentDescription = f.question + if (expanded) ", expanded" else ", collapsed" }.padding(TazSpace.lg)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.question, fontSize = TazType.bodySize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, modifier = Modifier.weight(1f))
                        TazIcon(if (expanded) TazIcons.Minus else TazIcons.Plus, null, size = TazSize.iconSm, tint = TazColors.TextTertiary)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(TazSpace.sm))
                        SelectionContainer { Text(f.answer, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary) }
                    }
                }
            }
        }
    }
}

// ---- one request ----------------------------------------------------------------------------------------------------------

@Composable
fun SupportCaseScreen(caseId: String) {
    val app = LocalAppState.current
    val store = ServiceLocator.supportStore
    val scope = rememberCoroutineScope()
    var attempt by remember(caseId) { mutableStateOf(0) }
    var case by remember(caseId) { mutableStateOf<SupportCase?>(null) }
    var failed by remember(caseId) { mutableStateOf(false) }
    var reply by remember(caseId) { mutableStateOf("") }
    var sending by remember(caseId) { mutableStateOf(false) }
    var error by remember(caseId) { mutableStateOf<String?>(null) }
    LaunchedEffect(caseId, attempt) {
        failed = false
        if (!RemoteSupportDataSource.isValidCaseId(caseId)) { failed = true; return@LaunchedEffect }
        val c = store.load(caseId)
        if (c == null) failed = true else case = c
    }
    Column(Modifier.fillMaxSize().background(TazColors.Cream).imePadding().testTag("supportCase")) {
        Header(SupportCopy.CASE_TITLE, onBack = { app.back() })
        val c = case
        when {
            c != null -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                    EditorialText(listOf(plain(c.subject)), size = TazType.editorialSectionSize, lineHeight = TazType.editorialSectionLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start)
                    Text(listOfNotNull(c.status.label, c.category.label, c.orderId?.let { "${SupportCopy.ABOUT_ORDER} $it" }).joinToString(" · "), fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                    Spacer(Modifier.height(TazSpace.sm))
                    c.messageViews().forEach { m -> MessageBubble(m) }
                    Spacer(Modifier.height(TazSpace.md))
                }
                Column(Modifier.fillMaxWidth().background(TazColors.Surface).padding(TazSpace.lg).navigationBarsPadding()) {
                    if (!c.status.acceptsReplies) Text(SupportCopy.CLOSED_NOTE, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                    // The session ended (e.g. rejected) while this thread was open: offer Login, never a dead reply box.
                    else if (!app.isAuthenticated) {
                        Text(SupportCopy.SIGNED_OUT_BODY, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                        TextAction(SupportCopy.LOG_IN, onClick = { app.navigate(Screen.Login) })
                    }
                    else {
                        val problem = SupportRules.checkMessage(reply)?.takeIf { reply.isNotEmpty() }
                        OutlinedTextField(
                            value = reply, onValueChange = { reply = it; error = null }, placeholder = { Text(SupportCopy.REPLY) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 160.dp).testTag("supportReply"), isError = problem != null
                        )
                        Text(error ?: problem?.copy() ?: counter(reply, SupportRules.MAX_MESSAGE), fontSize = TazType.captionSize,
                            color = if (error != null || problem != null) TazColors.Danger else TazColors.TextTertiary)
                        Spacer(Modifier.height(TazSpace.sm))
                        TazzzoPrimaryButton(
                            if (sending) SupportCopy.SENDING else SupportCopy.SEND, trailingArrow = false,
                            enabled = !sending && SupportRules.checkMessage(reply) == null, modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (sending) return@TazzzoPrimaryButton                 // one send at a time; the draft is kept on failure
                                sending = true
                                scope.launch {
                                    when (val r = store.reply(c.caseId, reply)) {
                                        is SupportWrite.Done -> { case = r.case; reply = "" }
                                        is SupportWrite.Failed -> { error = r.failure.message; r.refreshed?.let { case = it } }
                                    }
                                    sending = false
                                }
                            }
                        )
                    }
                }
            }
            failed -> EditorialEmptyState(TazIcons.Help, SupportCopy.CASE_FAILED, null, ProfileCopy.TRY_AGAIN, onAction = { attempt++ })
            else -> Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                SkeletonBlock(height = 28.dp, corner = 6.dp); SkeletonBlock(height = 96.dp, corner = TazRadius.tileDp); SkeletonBlock(height = 96.dp, corner = TazRadius.tileDp)
            }
        }
    }
}

@Composable
private fun MessageBubble(m: MessageView) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromCustomer) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(TazRadius.tile)
                .background(if (m.fromCustomer) TazColors.GreenSoft else TazColors.Surface)
                .padding(TazSpace.md)
        ) {
            Text(listOfNotNull(m.author, m.time).joinToString(" · "), fontSize = TazType.microSize, color = TazColors.TextSecondary)
            Spacer(Modifier.height(TazSpace.xxs))
            SelectionContainer { Text(m.text, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextPrimary) }
        }
    }
}

// ---- new request ------------------------------------------------------------------------------------------------------------

/** "Contact us" (optionally about one order): category, subject, message — checked with the backend's limits before sending. */
@Composable
fun NewSupportRequestScreen(orderId: String?) {
    val app = LocalAppState.current
    val store = ServiceLocator.supportStore
    val scope = rememberCoroutineScope()
    val order = orderId?.takeIf { RemoteOrderDataSource.isValidOrderId(it) }
    var category by remember { mutableStateOf(if (order != null) SupportCategory.ORDER_ISSUE else null) }
    var subject by remember { mutableStateOf(order?.let { orderSubject(it) } ?: "") }
    var message by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(TazColors.Cream).imePadding().testTag("supportNew")) {
        Header(SupportCopy.NEW_TITLE, onBack = { app.back() })
        if (!app.isAuthenticated) {
            EditorialEmptyState(TazIcons.Profile, SupportCopy.SIGNED_OUT_TITLE, SupportCopy.SIGNED_OUT_BODY, SupportCopy.LOG_IN, onAction = { app.navigate(Screen.Login) })
            return@Column
        }
        val subjectProblem = SupportRules.checkSubject(subject)
        val messageProblem = SupportRules.checkMessage(message)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
            if (order != null) Text("${SupportCopy.ABOUT_ORDER} $order", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.BrandEditorial)
            Text(SupportCopy.CATEGORY, fontSize = TazType.bodySize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary)
            SupportCategory.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                    pair.forEach { c -> CategoryChip(c.label, selected = category == c, modifier = Modifier.weight(1f)) { category = c } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            OutlinedTextField(
                value = subject, onValueChange = { subject = it; error = null }, label = { Text(SupportCopy.SUBJECT) }, singleLine = true,
                isError = touched && subjectProblem != null, modifier = Modifier.fillMaxWidth().testTag("supportSubject")
            )
            Text((if (touched) subjectProblem?.copy() else null) ?: "${SupportRules.cleanSubject(subject).length} / ${SupportRules.MAX_SUBJECT}",
                fontSize = TazType.captionSize, color = if (touched && subjectProblem != null) TazColors.Danger else TazColors.TextTertiary)
            OutlinedTextField(
                value = message, onValueChange = { message = it; error = null }, label = { Text(SupportCopy.MESSAGE) },
                isError = touched && messageProblem != null, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("supportMessage")
            )
            Text((if (touched) messageProblem?.copy() else null) ?: counter(message, SupportRules.MAX_MESSAGE),
                fontSize = TazType.captionSize, color = if (touched && messageProblem != null) TazColors.Danger else TazColors.TextTertiary)
            error?.let { Text(it, fontSize = TazType.captionSize, color = TazColors.Danger, modifier = Modifier.testTag("supportError")) }
            Spacer(Modifier.height(TazSpace.lg))
        }
        Column(Modifier.fillMaxWidth().padding(TazSpace.lg).navigationBarsPadding()) {
            TazzzoPrimaryButton(
                if (sending) SupportCopy.SENDING else SupportCopy.SEND, trailingArrow = false, enabled = !sending && category != null,
                modifier = Modifier.fillMaxWidth().testTag("supportSend"),
                onClick = {
                    if (sending) return@TazzzoPrimaryButton                             // one send at a time; the draft is kept on failure
                    touched = true
                    val c = category ?: return@TazzzoPrimaryButton
                    if (subjectProblem != null || messageProblem != null) return@TazzzoPrimaryButton
                    sending = true
                    scope.launch {
                        when (val r = store.submit(c, subject, message, order)) {
                            // The new request replaces this form on the stack, so Back from it returns to where the customer came from.
                            is SupportWrite.Done -> { app.back(); app.navigate(Screen.SupportCase(r.case.caseId)) }
                            is SupportWrite.Failed -> error = r.failure.message
                        }
                        sending = false
                    }
                }
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(TazSize.chipHeight).clip(TazRadius.pill)
            .background(if (selected) TazColors.BrandEditorial else TazColors.Surface)
            .border(BorderStroke(1.dp, if (selected) TazColors.BrandEditorial else TazColors.CardBorder), TazRadius.pill)
            .tazPressable(onClick = onClick, pressScale = TazPress.compact, selected = selected, role = Role.RadioButton)
            .padding(horizontal = TazSpace.md),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = if (selected) TazColors.EditorialOnDark else TazColors.TextPrimary, maxLines = 1)
    }
}

// ---- legal ---------------------------------------------------------------------------------------------------------------------

/** Terms of Service / Privacy Policy from `GET /v1/content/legal/{slug}`: public (works before login), plain text only. */
@Composable
fun LegalScreen(slug: String) {
    val app = LocalAppState.current
    val legal = LegalSlug.of(slug)
    var attempt by remember(slug) { mutableStateOf(0) }
    val load by produceState<LegalLoad>(LegalLoad.Loading, slug, attempt) {
        value = LegalLoad.Loading
        value = if (legal == null) LegalLoad.NotPublished else try {
            ServiceLocator.helpContent.legal(legal)?.let { LegalLoad.Loaded(it) } ?: LegalLoad.NotPublished
        } catch (e: CancellationException) { throw e } catch (_: Throwable) { LegalLoad.Failed }
    }
    LegalLayout(legal?.fallbackTitle ?: LegalSlug.TERMS.fallbackTitle, load, onBack = { app.back() }, onRetry = { attempt++ })
}

@Composable
fun LegalLayout(fallbackTitle: String, load: LegalLoad, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(TazColors.Cream).testTag("legal")) {
        Header((load as? LegalLoad.Loaded)?.document?.title ?: fallbackTitle, onBack = onBack)
        when (load) {
            LegalLoad.Loading -> Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                repeat(4) { SkeletonBlock(height = 64.dp, corner = 6.dp) }
            }
            LegalLoad.NotPublished -> EditorialEmptyState(TazIcons.Info, LegalCopy.NOT_PUBLISHED_TITLE, LegalCopy.NOT_PUBLISHED_BODY)
            LegalLoad.Failed -> EditorialEmptyState(TazIcons.Offline, LegalCopy.FAILED_TITLE, null, ProfileCopy.TRY_AGAIN, onAction = onRetry)
            // Lazy, so a very long document only lays out what is on screen; each paragraph is selectable plain text.
            is LegalLoad.Loaded -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                load.document.effectiveLine()?.let { line -> item(key = "effective") { Text(line, fontSize = TazType.captionSize, color = TazColors.TextSecondary, modifier = Modifier.testTag("legalEffective")) } }
                items(load.document.paragraphs.size) { i ->
                    SelectionContainer { Text(load.document.paragraphs[i], fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextPrimary) }
                }
                item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(TazSpace.xxl)) }
            }
        }
    }
}
