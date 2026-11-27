package com.roufsyed.cabin.system

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

enum class Saved { Done, Cancelled, Failed, NoPicker }

sealed interface Opened {
    data object Cancelled : Opened
    data object NoPicker : Opened
    data object Failed : Opened
    data object TooBig : Opened
    data class Text(val text: String) : Opened
}

interface BackupDocuments {
    fun save(name: String, text: String, done: (Saved) -> Unit)
    fun open(done: (Opened) -> Unit)
}

class FixedBackupDocuments(var file: String? = null) : BackupDocuments {
    var picker = true
    var cancels = false
    var writable = true
    var name: String? = null

    override fun save(name: String, text: String, done: (Saved) -> Unit) {
        done(
            when {
                !picker -> Saved.NoPicker
                cancels -> Saved.Cancelled
                !writable -> Saved.Failed
                else -> {
                    this.name = name
                    file = text
                    Saved.Done
                }
            },
        )
    }

    override fun open(done: (Opened) -> Unit) {
        done(
            when {
                !picker -> Opened.NoPicker
                cancels -> Opened.Cancelled
                else -> file?.let(Opened::Text) ?: Opened.Failed
            },
        )
    }
}

class BackupChooser(val create: (name: String) -> Boolean, val open: () -> Boolean)

class AndroidBackupDocuments(private val context: Context, private val scope: CoroutineScope) : BackupDocuments {
    var chooser: BackupChooser? = null
    private var saving: Pair<String, (Saved) -> Unit>? = null
    private var opening: ((Opened) -> Unit)? = null

    override fun save(name: String, text: String, done: (Saved) -> Unit) {
        saving = text to done
        if (chooser?.create?.invoke(name) != true) {
            saving = null
            done(Saved.NoPicker)
        }
    }

    fun created(document: Uri?) {
        val (text, done) = saving ?: return
        saving = null
        if (document == null) {
            done(Saved.Cancelled)
        } else {
            scope.launch(Dispatchers.IO) { done(if (write(document, text)) Saved.Done else Saved.Failed) }
        }
    }

    override fun open(done: (Opened) -> Unit) {
        opening = done
        if (chooser?.open?.invoke() != true) {
            opening = null
            done(Opened.NoPicker)
        }
    }

    fun opened(document: Uri?) {
        val done = opening ?: return
        opening = null
        if (document == null) {
            done(Opened.Cancelled)
        } else {
            scope.launch(Dispatchers.IO) { done(read(document)) }
        }
    }

    fun write(document: Uri, text: String): Boolean {
        val written = MODES.any { mode -> wrote(document, text, mode) }
        if (!written) discard(document)
        return written
    }

    private fun wrote(document: Uri, text: String, mode: String): Boolean = try {
        val stream = context.contentResolver.openOutputStream(document, mode)
        stream?.use { it.write(text.toByteArray()) }
        stream != null
    } catch (e: IOException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    fun read(document: Uri): Opened = try {
        val bytes = context.contentResolver.openInputStream(document)?.use { it.upTo(LIMIT) }
        when {
            bytes == null -> Opened.Failed
            bytes.size > LIMIT -> Opened.TooBig
            else -> Opened.Text(bytes.decodeToString())
        }
    } catch (e: IOException) {
        Opened.Failed
    } catch (e: RuntimeException) {
        Opened.Failed
    }

    private fun InputStream.upTo(limit: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (bytes.size() <= limit) {
            val count = read(buffer)
            if (count < 0) break
            bytes.write(buffer, 0, count)
        }
        return bytes.toByteArray()
    }

    private fun discard(document: Uri) {
        try {
            if (DocumentsContract.isDocumentUri(context, document)) {
                DocumentsContract.deleteDocument(context.contentResolver, document)
            }
        } catch (e: IOException) {
            return
        } catch (e: RuntimeException) {
            return
        }
    }

    private companion object {
        const val LIMIT = 1024 * 1024
        val MODES = listOf("wt", "w")
    }
}
