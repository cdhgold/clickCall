package com.cdhgold.clickcall.util

import android.widget.ImageView
import coil.load
import com.cdhgold.clickcall.R
import java.io.File

fun ImageView.loadContactImage(imageUri: String?) {
    if (imageUri.isNullOrEmpty()) {
        this.load(R.drawable.ic_default_smiley) {
            crossfade(true)
        }
    } else if (imageUri.startsWith("/")) {
        // File path: load from File object
        this.load(File(imageUri)) {
            crossfade(true)
            error(R.drawable.ic_default_smiley)
            fallback(R.drawable.ic_default_smiley)
        }
    } else {
        // content:// URI or other
        this.load(imageUri) {
            crossfade(true)
            error(R.drawable.ic_default_smiley)
            fallback(R.drawable.ic_default_smiley)
        }
    }
}


