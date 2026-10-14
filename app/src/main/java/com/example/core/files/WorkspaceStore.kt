package com.example.core.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

data class Workspace(val label: String, val uri: Uri)

/** Path handling shared by the file tool and its tests. */
object WorkspacePaths {
    /** Splits a relative path into segments; null when it tries to escape the folder. */
    fun segments(path: String): List<String>? {
        val parts = path.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." || it.contains('\u0000') }) return null
        return parts
    }
}

/**
 * Folders the user granted through the system folder picker (Storage Access Framework). The
 * grant is persistable, read AND write, and limited to that folder tree: the app never gets
 * blanket storage access.
 */
class WorkspaceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_workspaces", Context.MODE_PRIVATE)

    fun list(): List<Workspace> {
        val held = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }.map { it.uri }.toSet()
        return try {
            val arr = JSONArray(prefs.getString("items", "[]"))
            (0 until arr.length()).mapNotNull {
                val o = arr.optJSONObject(it) ?: return@mapNotNull null
                val uri = Uri.parse(o.optString("uri"))
                if (uri in held) Workspace(o.optString("label"), uri) else null
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Takes the persistable grant for [uri] and remembers it. Returns null if Android refuses. */
    fun add(uri: Uri): Workspace? {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { return null }
        val existing = list()
        existing.firstOrNull { it.uri == uri }?.let { return it }
        val base = DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() } ?: "Folder"
        var label = base; var n = 2
        while (existing.any { it.label.equals(label, ignoreCase = true) }) label = "$base ${n++}"
        val ws = Workspace(label, uri)
        save(existing + ws)
        return ws
    }

    fun remove(label: String) {
        val all = list()
        all.firstOrNull { it.label == label }?.let {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        save(all.filterNot { it.label == label })
    }

    /** Picks the folder named [label]; with no label, the only folder (else asks which). */
    fun resolve(label: String?): Pair<Workspace?, String?> {
        val all = list()
        if (all.isEmpty()) return null to "No folder access yet. Open the permissions screen and choose a folder under Folder Access."
        if (label.isNullOrBlank()) {
            return if (all.size == 1) all[0] to null
            else null to "Which folder? Granted folders: ${all.joinToString { it.label }}."
        }
        val match = all.firstOrNull { it.label.equals(label, ignoreCase = true) }
            ?: all.firstOrNull { it.label.contains(label, ignoreCase = true) }
        return if (match != null) match to null
        else null to "No granted folder called '$label'. Granted folders: ${all.joinToString { it.label }}."
    }

    private fun save(items: List<Workspace>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("label", it.label).put("uri", it.uri.toString())) }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
