package com.inkfirst.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.R
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.ContentReport
import com.inkfirst.core.ReportReason
import com.inkfirst.core.ReportedContent
import com.inkfirst.core.reportsAsEmailBody

/**
 * Reporting a page, an answer or a picture.
 *
 * Play requires an in-app way to report offensive AI output, reachable
 * WITHOUT leaving the app (docs/play/ai-content-policy.md). The reporting
 * surface is therefore the dialog, and sending is a separate, optional step:
 * the report is on disk the moment it is written, so declining to send still
 * leaves a complete record. That ordering is the whole design — a report that
 * only exists if an email client opens is not an in-app report.
 *
 * It is deliberately one dialog with two entry points, the reader's menu and
 * the Ask panel. A fresh answer is the likeliest thing to be offensive and
 * the easiest moment to flag, but the chapters are the bulk of what the app
 * generates, so a reader mid-page needs somewhere to go too.
 *
 * App chrome, so plain string resources in the device language — nothing
 * here is typeset into a book or carried by the EPUB. See strings.xml.
 */
@Composable
internal fun ReportDialog(
    what: ReportedContent,
    passage: String,
    courseSlug: String,
    courseTitle: String,
    chapterN: Int,
    chapterTitle: String,
    blockIndex: Int,
    today: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = rememberCourseStore()
    var reason by remember { mutableStateOf(ReportReason.OFFENSIVE) }
    var note by remember { mutableStateOf("") }
    // Two states, not two dialogs: once written, the SAME window reports what
    // happened. Popping on success is the "generate did nothing" defect in
    // another costume — the result line is the only evidence the save
    // occurred, and this app has made that mistake twice before.
    var saved by remember { mutableStateOf(false) }

    if (!saved) {
        InkDialog(
            title = stringResource(R.string.report_title),
            onDismiss = onDismiss,
            confirmLabel = stringResource(R.string.report_save),
            onConfirm = {
                store.appendReport(
                    ContentReport(
                        reason = reason,
                        what = what,
                        note = note,
                        courseSlug = courseSlug,
                        courseTitle = courseTitle,
                        chapterN = chapterN,
                        chapterTitle = chapterTitle,
                        blockIndex = blockIndex,
                        passage = passage,
                        created = today,
                    ),
                )
                saved = true
            },
            dismissLabel = stringResource(R.string.cancel),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.report_what_is_wrong),
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    color = Ink,
                )
                ReportReason.entries.forEach { r ->
                    ReasonRow(
                        label = stringResource(reasonLabel(r)),
                        selected = reason == r,
                        onClick = { reason = r },
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.report_note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        InkDialog(
            title = stringResource(R.string.report_saved_title),
            onDismiss = onDismiss,
            // Sending is OFFERED, never required. An email client is another
            // app, and the policy is about the reporting being in-app.
            confirmLabel = stringResource(R.string.report_send_email),
            onConfirm = {
                sendReportsByEmail(context, store.readReports())
                onDismiss()
            },
            dismissLabel = stringResource(R.string.done),
        ) {
            Text(
                text = stringResource(R.string.report_saved_body),
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = Ink,
            )
        }
    }
}

private fun reasonLabel(reason: ReportReason): Int = when (reason) {
    ReportReason.OFFENSIVE -> R.string.report_reason_offensive
    ReportReason.HARMFUL -> R.string.report_reason_harmful
    ReportReason.WRONG -> R.string.report_reason_wrong
    ReportReason.OTHER -> R.string.report_reason_other
}

/**
 * One choice. A bordered box rather than a Material RadioButton for the
 * standing reason: an unrestyled M3 control arrives purple and tinted, and
 * this panel is pure black on pure white because the display cannot render
 * anything else cleanly.
 */
@Composable
private fun ReasonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(1.dp, Ink, RoundedCornerShape(4.dp))
                else Modifier,
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = Ink,
        )
    }
}

/**
 * Hands the whole report file to whatever the reader uses for mail.
 *
 * `ACTION_SENDTO` with a `mailto:` URI rather than `ACTION_SEND`, so the
 * chooser only offers mail clients instead of every app that can take text.
 * Failing silently is correct here: the report is already saved, so a device
 * with no mail client loses the convenience and not the record.
 */
private fun sendReportsByEmail(context: Context, markdown: String) {
    if (markdown.isBlank()) return
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, REPORT_SUBJECT)
        putExtra(Intent.EXTRA_TEXT, reportsAsEmailBody(markdown))
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No mail client. The file is on disk; nothing is lost.
    }
}

private const val REPORT_EMAIL = "aliaksei.kavaliou@kiloverse.com"
private const val REPORT_SUBJECT = "Zhuzha — content report"
