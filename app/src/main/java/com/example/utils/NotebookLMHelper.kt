package com.example.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object NotebookLMHelper {

    const val NOTEBOOK_LM_URL = "https://notebooklm.google.com"

    /**
     * Copies direct PDF link to clipboard and notifies user.
     */
    fun copyPdfLink(context: Context, url: String, title: String? = null) {
        if (url.isBlank()) {
            Toast.makeText(context, "পিডিএফ লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("PDF Link", url)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(
            context,
            "পিডিএফ লিংক কপি হয়েছে! নোটবুক এলএম-এ সরাসরি 'Add Source' করতে পারবেন।",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Opens Google NotebookLM in the user's default browser or custom tabs.
     */
    fun openNotebookLMWeb(context: Context, urlToCopy: String? = null) {
        if (!urlToCopy.isNullOrBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("PDF Link", urlToCopy)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(
                context,
                "পিডিএফ লিংক কপি হয়েছে! NotebookLM-এ New Notebook খুলে পেস্ট করুন।",
                Toast.LENGTH_LONG
            ).show()
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(NOTEBOOK_LM_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "ব্রাউজার ওপেন করা সম্ভব হয়নি: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares the PDF file via Android System Share Sheet to Google Drive / NotebookLM / Gmail.
     */
    fun sharePdfToNotebookLM(
        context: Context,
        file: File?,
        remoteUrl: String?,
        title: String
    ) {
        if (file != null && file.exists() && file.length() > 0) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "গুগল নোটবুক এলএম (NotebookLM)-এ পড়ার জন্য লেকচার ফাইল: $title\n\nনোটবুক এলএম: $NOTEBOOK_LM_URL"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "নোটবুক এলএম বা ড্রাইভে শেয়ার করুন").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return
            } catch (e: Exception) {
                android.util.Log.e("NotebookLMHelper", "Error sharing PDF file: ${e.message}", e)
            }
        }

        // Fallback if local file not ready: share remote link
        if (!remoteUrl.isNullOrBlank()) {
            copyPdfLink(context, remoteUrl, title)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "গুগল নোটবুক এলএম (NotebookLM)-এ পড়ার জন্য লেকচার স্লাইড: $title\n\nডাউনলোড লিংক: $remoteUrl\nনোটবুক এলএম: $NOTEBOOK_LM_URL"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "নোটবুক এলএম বা অন্যান্য অ্যাপে লিংক শেয়ার করুন").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } else {
            Toast.makeText(context, "শেয়ার করার মতো ফাইল বা লিংক প্রস্তুত হয়নি", Toast.LENGTH_SHORT).show()
        }
    }
}
