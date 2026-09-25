package com.inkfirst.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.BackupFolder
import com.inkfirst.app.data.DocumentTreeFileTree
import com.inkfirst.app.data.coursesRootFor
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.CourseBackup
import com.inkfirst.core.FileTree
import com.inkfirst.core.LocalFileTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Copying the shelf somewhere it survives this device, and copying it back.
 *
 * The promise in design.md §1 is that a reader's study material is plain
 * files they can read in ten years. On the device that is currently only half
 * true: the courses live in the app's own external-files directory, whose
 * permissions give every other app nothing — so the files are inspectable
 * over adb from a laptop and nowhere else. Backing up to a folder the reader
 * picked is what makes the promise true on the device itself, and it is also
 * the only copy of ~15 MB that would otherwise exist on one consumer
 * e-reader.
 *
 * Backing up is one button and takes everything, because a backup you have to
 * curate is one you stop taking. Restoring is per book and asks first,
 * because it is the direction that can destroy something.
 *
 * A book travels as one zip. It used to travel as its own files, which is the
 * shape §1 argues for — and on a Boox those files are indexed: ninety-six of
 * them turned up in the reader's Images and Documents libraries, a chapter
 * away from being swept into the bin during a tidy-up. One file per book
 * keeps the folder legible and keeps a zip out of the picture galleries, and
 * costs a double-click to read. See [CourseArchive].
 */
@Composable
fun BackupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = rememberCourseStore()
    val folder = remember { BackupFolder(context) }
    val scope = rememberCoroutineScope()

    var folderUri by remember { mutableStateOf(folder.uri()) }
    var folderLabel by remember { mutableStateOf<String?>(null) }
    var shelfHere by remember { mutableStateOf<String?>(null) }
    var shelfThere by remember { mutableStateOf<String?>(null) }
    var backupCourses by remember { mutableStateOf<List<String>>(emptyList()) }
    // Which of them are packed as a zip — the rest are folders an older
    // backup wrote, and restoring those still works.
    var packedThere by remember { mutableStateOf<Set<String>>(emptySet()) }
    // The loose files an earlier version of the backup wrote, where a zip
    // beside them now holds the same book. Offered for removal; the sweep
    // itself re-reads the folder and checks each archive before deleting.
    var loose by remember { mutableStateOf<List<CourseBackup.LooseCopy>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var pendingSweep by remember { mutableStateOf(false) }
    // Bumped after anything that changes what is on either side, to re-read
    // both listings. Not a boolean: two backups in a row have to re-read too.
    var reading by remember { mutableStateOf(0) }

    val titlesHere = remember(reading) { store.list().associate { it.slug to it.title } }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { picked: Uri? ->
        if (picked != null) {
            folder.remember(picked)
            folderUri = picked
            status = null
            reading++
        }
    }

    LaunchedEffect(folderUri, reading) {
        val local = withContext(Dispatchers.IO) { LocalFileTree(coursesRootFor(context)).list() }
        shelfHere = describeShelf(local)

        val uri = folderUri
        if (uri == null) {
            folderLabel = null
            shelfThere = null
            backupCourses = emptyList()
            return@LaunchedEffect
        }
        folderLabel = withContext(Dispatchers.IO) { folder.label(uri) }
        val there = withContext(Dispatchers.IO) {
            runCatching { DocumentTreeFileTree(context, uri).list() }.getOrNull()
        }
        if (there == null) {
            // The grant survived but the folder did not answer — card pulled,
            // folder deleted. Say so instead of offering an empty backup.
            shelfThere = "This folder cannot be read any more. Choose it again."
            backupCourses = emptyList()
            loose = emptyList()
        } else {
            shelfThere = describeBackup(there)
            backupCourses = booksInBackup(there)
            packedThere = CourseBackup.archivedIn(there).toSet()
            loose = CourseBackup.looseCopiesIn(there)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backup", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Your books live in a folder only this app can open. A backup copies " +
                    "them somewhere you can reach — a memory card, a USB stick, a folder that " +
                    "syncs itself. Nothing is sent anywhere you did not choose.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (folderUri == null) {
                Text(
                    text = "${BackupFolder.SUGGESTED_PATH} is a good place — the picker " +
                        "starts in ${BackupFolder.SUGGESTED_PARENT}, and the folder icon at " +
                        "the top makes a new one. Any folder works; a memory card is the " +
                        "safest one.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folderLabel?.let { "Folder: $it" } ?: "No folder chosen",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = !busy,
                    onClick = { picker.launch(folder.suggestion()) },
                ) { Text(if (folderUri == null) "Choose folder" else "Change") }
            }

            HorizontalDivider()

            Text("On this device", style = MaterialTheme.typography.titleMedium)
            Text(shelfHere ?: "Counting…", style = MaterialTheme.typography.bodySmall)

            Button(
                enabled = folderUri != null && !busy,
                onClick = {
                    val uri = folderUri ?: return@Button
                    scope.launch {
                        busy = true
                        val here = LocalFileTree(coursesRootFor(context))
                        status = packAcross(
                            from = here,
                            to = DocumentTreeFileTree(context, uri),
                            source = withContext(Dispatchers.IO) { here.list() },
                            setStatus = { status = it },
                        )
                        busy = false
                        reading++
                    }
                },
            ) { Text("Back up everything") }

            if (folderUri != null) {
                Text(
                    text = "Each book becomes one file, so the folder stays tidy and the " +
                        "reader's library does not fill up with pages. Only books that " +
                        "changed are packed again.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            if (folderUri != null) {
                HorizontalDivider()

                Text("In the backup", style = MaterialTheme.typography.titleMedium)
                Text(shelfThere ?: "Reading the folder…", style = MaterialTheme.typography.bodySmall)

                if (loose.isNotEmpty()) {
                    Text(
                        text = describeLooseCopies(loose),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Only the loose files go. The zips stay.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = !busy,
                            onClick = { pendingSweep = true },
                        ) { Text("Remove old copies") }
                    }
                }

                backupCourses.forEach { slug ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titlesHere[slug] ?: slug,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = if (titlesHere.containsKey(slug)) slug
                                else "$slug · not on this device",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = { pendingRestore = slug },
                        ) { Text("Restore") }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onDone) { Text("Done") }
        }
    }

    if (pendingSweep) {
        val books = loose.filterNot { it.isShelf }.map { titlesHere[it.name] ?: it.name }
        InkDialog(
            title = "Remove the old copies",
            onDismiss = { pendingSweep = false },
            confirmLabel = "Remove",
            onConfirm = {
                val uri = folderUri
                pendingSweep = false
                if (uri != null) {
                    scope.launch {
                        busy = true
                        status = sweepAcross(DocumentTreeFileTree(context, uri)) { status = it }
                        busy = false
                        reading++
                    }
                }
            },
            dismissLabel = "Cancel",
        ) {
            Text(
                text = "Delete the loose files this app left in the backup folder before it " +
                    "started packing a book into one zip. " +
                    (if (books.isEmpty()) "" else books.joinToString("; ") + ". ") +
                    "Each zip is opened and read through first, and anything it does not " +
                    "hold is kept. The books themselves stay in the backup.",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }

    pendingRestore?.let { slug ->
        val replacing = titlesHere.containsKey(slug)
        InkDialog(
            title = titlesHere[slug] ?: slug,
            onDismiss = { pendingRestore = null },
            confirmLabel = "Restore",
            onConfirm = {
                val uri = folderUri
                pendingRestore = null
                if (uri != null) {
                    val packed = slug in packedThere
                    scope.launch {
                        busy = true
                        val there = DocumentTreeFileTree(context, uri)
                        val here = LocalFileTree(coursesRootFor(context))
                        status = if (packed) {
                            unpackHere(from = there, book = slug, to = here) { status = it }
                        } else {
                            // A book an older backup left as loose files.
                            // Still restorable, by the path that wrote it.
                            copyAcross(
                                from = there,
                                to = here,
                                source = withContext(Dispatchers.IO) {
                                    entriesForCourse(there.list(), slug)
                                },
                                // The reader asked for the backup's copy, so
                                // take it whatever is sitting here — an
                                // incremental restore would skip exactly the
                                // files that got changed, which are the ones
                                // being restored.
                                mode = CourseBackup.Mode.OVERWRITE,
                                setStatus = { status = it },
                            )
                        }
                        busy = false
                        reading++
                    }
                }
            },
            dismissLabel = "Cancel",
        ) {
            Text(
                text = if (replacing) {
                    "Put the backup's copy of this book over the one on this device. " +
                        "Anything read, asked or written since the last backup is lost."
                } else {
                    "Copy this book from the backup onto this device."
                },
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

/**
 * Plans and runs one direction, reporting as it goes, and returns the line to
 * leave on screen.
 *
 * [source] is passed in rather than read from [from] because a restore is one
 * book out of a backup of several: narrowing the source listing is all it
 * takes, and nothing below needs to know that a course is a thing.
 *
 * Every failure comes back as a sentence rather than an exception. A backup
 * screen that crashes is one nobody trusts with the only copy of their books.
 */
private suspend fun copyAcross(
    from: FileTree,
    to: FileTree,
    source: List<FileTree.Entry>,
    mode: CourseBackup.Mode,
    setStatus: (String) -> Unit,
): String = withContext(Dispatchers.IO) {
    try {
        setStatus("Looking at the folder…")
        val plan = CourseBackup.plan(source, to.list(), mode)
        if (plan.copy.isEmpty()) return@withContext describePlan(plan)

        setStatus("Copying ${plan.copy.size} files, ${humanBytes(plan.bytes)}…")
        val report = CourseBackup.run(from, to, plan) { done, total ->
            // A line that changes a handful of times, not an animation: on
            // e-ink every change is a panel repaint, so reporting each of 400
            // files would cost more than the copying does.
            if (done == total || done % PROGRESS_STEP == 0) {
                setStatus("Copying $done of $total…")
            }
        }
        describeReport(report)
    } catch (e: SecurityException) {
        "This folder is not available any more. Choose it again."
    } catch (e: Exception) {
        "Could not finish: ${e.message ?: e::class.simpleName}"
    }
}

/**
 * Packs every book that changed into its own archive at the far end.
 *
 * The plan reads what is already in each archive rather than trusting its
 * timestamp, which costs a pass over the folder and buys the one thing a
 * backup screen should never get wrong: a run interrupted half-way leaves a
 * zip that is NEWER than everything in the book and holds only part of it.
 * Contents cannot lie about that the way a stamp can. See
 * [CourseBackup.planArchives].
 */
private suspend fun packAcross(
    from: FileTree,
    to: FileTree,
    source: List<FileTree.Entry>,
    setStatus: (String) -> Unit,
): String = withContext(Dispatchers.IO) {
    try {
        setStatus("Looking at the backup…")
        val plan = CourseBackup.planArchives(source, CourseBackup.archiveReader(to))
        if (plan.write.isEmpty()) return@withContext describeArchivePlan(plan)

        setStatus("Packing ${describeArchivePlan(plan)}")
        val report = CourseBackup.writeArchives(from, to, plan) { done, total ->
            if (done == total || done % PROGRESS_STEP == 0) {
                setStatus("Packing $done of $total…")
            }
        }
        describeArchiveReport(plan, report)
    } catch (e: SecurityException) {
        "This folder is not available any more. Choose it again."
    } catch (e: Exception) {
        "Could not finish: ${e.message ?: e::class.simpleName}"
    }
}

/**
 * Deletes the loose copies an earlier version of the backup wrote.
 *
 * Everything that makes this safe lives in [CourseBackup.removeLooseCopies] —
 * it re-reads the folder, opens each archive and reads it through past its
 * CRCs, and deletes a group only when the archive holds every file in it.
 * Reading 28 MB to delete 28 MB looks wasteful and is the point: this is the
 * moment the second copy stops existing.
 */
private suspend fun sweepAcross(
    to: FileTree,
    setStatus: (String) -> Unit,
): String = withContext(Dispatchers.IO) {
    try {
        setStatus("Checking the zips before deleting anything…")
        val swept = CourseBackup.removeLooseCopies(to) { done, total ->
            if (done == total || done % PROGRESS_STEP == 0) {
                setStatus("Checked $done of $total…")
            }
        }
        describeSweep(swept)
    } catch (e: SecurityException) {
        "This folder is not available any more. Choose it again."
    } catch (e: Exception) {
        "Could not finish: ${e.message ?: e::class.simpleName}"
    }
}

/** Unpacks one book's archive back onto the device. */
private suspend fun unpackHere(
    from: FileTree,
    book: String,
    to: FileTree,
    setStatus: (String) -> Unit,
): String = withContext(Dispatchers.IO) {
    try {
        setStatus("Opening the backup…")
        val report = CourseBackup.restoreArchive(from, book, to) { done ->
            if (done % PROGRESS_STEP == 0) setStatus("Restoring $done files…")
        }
        describeReport(report)
    } catch (e: SecurityException) {
        "This folder is not available any more. Choose it again."
    } catch (e: Exception) {
        "Could not finish: ${e.message ?: e::class.simpleName}"
    }
}

private const val PROGRESS_STEP = 25
