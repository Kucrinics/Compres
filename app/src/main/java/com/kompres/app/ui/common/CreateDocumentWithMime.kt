package com.kompres.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.kompres.app.data.SafHelper

class CreateDocumentWithMime(
    private val mimeProvider: () -> String
) : ActivityResultContract<String, Uri?>() {

    override fun createIntent(context: Context, input: String): Intent =
        SafHelper.createDocument(input, mimeProvider())

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
