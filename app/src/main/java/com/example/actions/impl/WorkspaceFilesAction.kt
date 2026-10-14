package com.example.actions.impl

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.actions.Action
import com.example.actions.ActionResult
import com.example.actions.ConfirmationSpec
import com.example.actions.ParamDefinition
import com.example.actions.UndoStack
import com.example.actions.UndoableAction
import com.example.core.files.WorkspacePaths
import com.example.core.files.WorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Files and folders inside the folders the user granted (Folder Access). It can create folders,
 * create and write files (code, text, HTML...), append, read, list, rename, open and delete.
 * Everything except delete is undoable; delete asks for the user's tap first and keeps a copy of
 * small files so it can be undone too.
 */
class WorkspaceFilesAction(
    private val context: Context,
    private val store: WorkspaceStore,
    private val undoStack: UndoStack
) : Action {

    override val name: String = "workspace_files"
    override val description: String =
        "Creates, reads, writes, appends to, renames, deletes, lists and opens files and folders inside the " +
        "folders the user granted under Folder Access. Use it to make project folders and to write code or " +
        "text directly into files (give the COMPLETE file content). 'path' is relative to the granted folder, " +
        "for example 'site/index.html'."

    override val riskyLabel: String = "Delete files and folders"

    override val parameters: Map<String, ParamDefinition> = mapOf(
        "operation" to ParamDefinition(
            "string", "What to do", required = true,
            enumValues = listOf("list", "read", "create_folder", "create_file", "write", "write_many", "append", "rename", "delete", "open")
        ),
        "path" to ParamDefinition("string", "Path inside the granted folder; empty means the folder itself", required = false),
        "content" to ParamDefinition("string", "Text to put in the file (create_file, write, append)", required = false),
        "files_json" to ParamDefinition(
            "string",
            "For write_many: a JSON array like [{\"path\":\"index.html\",\"content\":\"...\"},{\"path\":\"css/style.css\",\"content\":\"...\"}]. Paths are inside 'path'.",
            required = false
        ),
        "new_name" to ParamDefinition("string", "New name for rename", required = false),
        "folder" to ParamDefinition("string", "Which granted folder; optional when only one is granted", required = false)
    )

    override fun confirmationFor(args: Map<String, Any?>): ConfirmationSpec? {
        if ((args["operation"] as? String)?.lowercase() != "delete") return null
        return ConfirmationSpec("Delete", "Delete '${args["path"]}' from your folder?")
    }

    override suspend fun execute(args: Map<String, Any?>): ActionResult = withContext(Dispatchers.IO) {
        val op = (args["operation"] as? String)?.lowercase()
            ?: return@withContext fail("Which file operation?")
        val (ws, problem) = store.resolve(args["folder"] as? String)
        if (ws == null) return@withContext fail(problem ?: "No folder available.")
        val root = DocumentFile.fromTreeUri(context, ws.uri) ?: return@withContext fail("Cannot open the folder ${ws.label}.")
        val segs = WorkspacePaths.segments((args["path"] as? String).orEmpty())
            ?: return@withContext fail("That path is not allowed.")
        val content = args["content"] as? String

        try {
            when (op) {
                "list" -> {
                    val dir = navigate(root, segs) ?: return@withContext fail("Folder not found.")
                    if (!dir.isDirectory) return@withContext fail("That is a file, not a folder.")
                    val names = dir.listFiles().map { if (it.isDirectory) "${it.name.orEmpty()}/" else it.name.orEmpty() }.sorted()
                    ActionResult(
                        if (names.isEmpty()) "The folder is empty." else names.take(100).joinToString(", "),
                        mapOf("folder" to ws.label, "entries" to names.take(100).joinToString("\n"))
                    )
                }
                "read" -> {
                    val f = navigate(root, segs)?.takeIf { it.isFile } ?: return@withContext fail("File not found.")
                    if (f.length() > MAX_READ_BYTES) return@withContext fail("That file is too large to read (over ${MAX_READ_BYTES / 1024} KB).")
                    val text = readText(f)
                    ActionResult(text.ifEmpty { "(empty file)" })
                }
                "create_folder" -> {
                    if (segs.isEmpty()) return@withContext fail("Give the folder a name or path.")
                    val before = navigate(root, segs)
                    val dir = ensureDirs(root, segs) ?: return@withContext fail("Could not create the folder.")
                    if (before == null) pushUndoDelete(dir, "Remove folder '${segs.last()}'")
                    ActionResult(if (before == null) "Created folder ${segs.joinToString("/")} in ${ws.label}." else "That folder already exists.")
                }
                "create_file", "write", "append" -> writeOp(op, root, ws.label, segs, content)
                "write_many" -> {
                    val arr = try { org.json.JSONArray(args["files_json"] as? String ?: "[]") }
                    catch (e: org.json.JSONException) { return@withContext fail("files_json is not valid JSON.") }
                    if (arr.length() == 0) return@withContext fail("files_json has no files.")
                    if (arr.length() > 40) return@withContext fail("Too many files at once (max 40).")
                    val made = ArrayList<String>()
                    val failed = ArrayList<String>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val rel = WorkspacePaths.segments(o.optString("path")) ?: run { failed += o.optString("path"); null } ?: continue
                        if (rel.isEmpty()) continue
                        val r = writeOp("write", root, ws.label, segs + rel, o.optString("content", ""))
                        if (r.isError) failed += "${o.optString("path")} (${r.spokenResult})" else made += o.optString("path")
                    }
                    val where = if (segs.isEmpty()) ws.label else "${ws.label}/${segs.joinToString("/")}"
                    ActionResult(
                        "Created ${made.size} file(s) in $where: ${made.joinToString(", ")}." +
                            if (failed.isNotEmpty()) " Failed: ${failed.joinToString("; ")}." else "",
                        mapOf("folder" to where, "entries" to made.joinToString("\n")),
                        isError = made.isEmpty()
                    )
                }
                "rename" -> {
                    val f = navigate(root, segs) ?: return@withContext fail("Not found.")
                    val newName = (args["new_name"] as? String)?.trim().orEmpty()
                    if (newName.isEmpty() || newName.contains('/')) return@withContext fail("Give a new name without slashes.")
                    val oldName = f.name.orEmpty()
                    if (!f.renameTo(newName)) return@withContext fail("Could not rename it.")
                    val uri = f.uri
                    undoStack.push(UndoableAction("Rename $newName back to $oldName") {
                        withContext(Dispatchers.IO) {
                            if (DocumentFile.fromTreeUri(context, uri)?.renameTo(oldName) == true) "Renamed back to $oldName."
                            else "Could not rename it back."
                        }
                    })
                    ActionResult("Renamed $oldName to $newName.")
                }
                "delete" -> {
                    if (segs.isEmpty()) return@withContext fail("I will not delete the whole granted folder.")
                    val target = navigate(root, segs) ?: return@withContext fail("Not found.")
                    val name = target.name.orEmpty()
                    val parentUri = target.parentFile?.uri
                    val backup = if (target.isFile && target.length() <= MAX_UNDO_BYTES) readBytes(target) else null
                    if (!target.delete()) return@withContext fail("Could not delete $name.")
                    if (backup != null && parentUri != null) {
                        undoStack.push(UndoableAction("Restore deleted $name") {
                            withContext(Dispatchers.IO) {
                                val parent = DocumentFile.fromTreeUri(context, parentUri)
                                val made = parent?.createFile(mimeFor(name), name)
                                if (made != null) { writeBytes(made, backup, "wt"); "Restored $name." } else "Could not restore $name."
                            }
                        })
                        ActionResult("Deleted $name. Say undo to bring it back.")
                    } else {
                        ActionResult("Deleted $name. This one cannot be undone.")
                    }
                }
                "open" -> {
                    val f = navigate(root, segs)?.takeIf { it.isFile } ?: return@withContext fail("File not found.")
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.type ?: mimeFor(f.name.orEmpty()))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    ActionResult("Opened ${f.name}.")
                }
                else -> fail("Unknown file operation: $op")
            }
        } catch (e: IOException) {
            fail("File error: ${e.message}")
        } catch (e: SecurityException) {
            fail("Android blocked that: the folder grant may have been revoked. Grant it again under Folder Access.")
        }
    }

    // ---- write / create / append ---------------------------------------------------------

    private suspend fun writeOp(op: String, root: DocumentFile, label: String, segs: List<String>, content: String?): ActionResult {
        if (segs.isEmpty()) return fail("Give the file a path, for example 'notes.txt'.")
        val text = content ?: if (op == "create_file") "" else return fail("No content was provided.")
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_WRITE_BYTES) return fail("That is too large to write in one go (over ${MAX_WRITE_BYTES / 1024} KB).")

        val fileName = segs.last()
        val existing = navigate(root, segs)
        if (existing != null && existing.isDirectory) return fail("'$fileName' is a folder.")
        if (op == "create_file" && existing != null) return fail("$fileName already exists. Use write to replace it.")

        val parentSegs = segs.dropLast(1)
        val createdDirs = navigate(root, parentSegs) == null
        val parent = ensureDirs(root, parentSegs) ?: return fail("Could not create the folders for that path.")

        if (existing != null) {
            val old = if (existing.length() <= MAX_UNDO_BYTES) readBytes(existing) else null
            val uri = existing.uri
            writeBytes(existing, bytes, if (op == "append") "wa" else "wt")
            if (old != null) {
                undoStack.push(UndoableAction("Restore previous contents of $fileName") {
                    withContext(Dispatchers.IO) {
                        val f = DocumentFile.fromTreeUri(context, uri)
                        if (f != null) { writeBytes(f, old, "wt"); "Restored $fileName." } else "Could not restore $fileName."
                    }
                })
            }
            return ActionResult(
                if (op == "append") "Added ${bytes.size} bytes to $fileName in $label." else "Replaced the contents of $fileName in $label.",
                mapOf("file" to segs.joinToString("/"), "folder" to label)
            )
        }

        var file = parent.createFile(mimeFor(fileName), fileName) ?: return fail("Could not create $fileName.")
        // Some providers add an extension to match the MIME type; keep the exact name asked for.
        if (file.name != fileName) { file.renameTo(fileName); file = parent.findFile(fileName) ?: file }
        writeBytes(file, bytes, "wt")
        pushUndoDelete(file, "Remove $fileName", also = if (createdDirs) parent else null)
        return ActionResult(
            "Created $fileName in $label (${bytes.size} bytes).",
            mapOf("file" to segs.joinToString("/"), "folder" to label)
        )
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun fail(msg: String) = ActionResult(msg, isError = true)

    private fun navigate(root: DocumentFile, segs: List<String>): DocumentFile? {
        var cur: DocumentFile = root
        for (s in segs) cur = cur.findFile(s) ?: return null
        return cur
    }

    private fun ensureDirs(root: DocumentFile, segs: List<String>): DocumentFile? {
        var cur: DocumentFile = root
        for (s in segs) {
            val next = cur.findFile(s)
            cur = when {
                next == null -> cur.createDirectory(s) ?: return null
                next.isDirectory -> next
                else -> return null
            }
        }
        return cur
    }

    private fun pushUndoDelete(created: DocumentFile, description: String, also: DocumentFile? = null) {
        val uri = created.uri
        val alsoUri = also?.uri
        undoStack.push(UndoableAction(description) {
            withContext(Dispatchers.IO) {
                val a = DocumentFile.fromTreeUri(context, uri)?.delete() ?: false
                if (alsoUri != null) DocumentFile.fromTreeUri(context, alsoUri)?.let { d ->
                    if (d.listFiles().isEmpty()) d.delete()
                }
                if (a) "Removed it." else "It was already gone."
            }
        })
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun readBytes(f: DocumentFile): ByteArray =
        context.contentResolver.openInputStream(f.uri)?.use { it.readBytes() } ?: throw IOException("cannot read ${f.name}")

    private fun readText(f: DocumentFile): String = readBytes(f).toString(Charsets.UTF_8).take(MAX_READ_CHARS)

    private fun writeBytes(f: DocumentFile, bytes: ByteArray, mode: String) {
        (context.contentResolver.openOutputStream(f.uri, mode) ?: throw IOException("cannot write ${f.name}"))
            .use { it.write(bytes) }
    }

    private companion object {
        const val MAX_READ_BYTES = 512 * 1024L
        const val MAX_READ_CHARS = 20_000
        const val MAX_WRITE_BYTES = 2 * 1024 * 1024
        const val MAX_UNDO_BYTES = 2L * 1024 * 1024
    }
}
