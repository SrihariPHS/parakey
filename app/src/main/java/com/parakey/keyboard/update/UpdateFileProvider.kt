package com.parakey.keyboard.update

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Minimal file provider for the downloaded update APK. The project avoids
 * AndroidX, so this exposes only files inside cache/updates and nothing else.
 */
class UpdateFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Only read access is supported")
        val context = context ?: throw FileNotFoundException("No context")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("Missing file name")
        if (name.contains('/') || name.contains("..")) throw FileNotFoundException("Bad file name")
        val file = File(File(context.cacheDir, "updates"), name)
        if (!file.isFile) throw FileNotFoundException(name)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read-only provider")

    companion object {
        fun uriFor(context: Context, file: File): Uri =
            Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.updates")
                .appendPath(file.name)
                .build()
    }
}
