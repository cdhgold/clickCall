package com.cdhgold.clickcall.util

import android.widget.ImageView
import coil.load
import com.cdhgold.clickcall.R

fun ImageView.loadContactImage(imageUri: String?) {
    if (imageUri.isNullOrEmpty()) {
        // Load default smiley image
        this.load(R.drawable.ic_default_smiley) {
            crossfade(true)
        }
    } else {
        // Load user's image with default fallback
        this.load(imageUri) {
            crossfade(true)
            error(R.drawable.ic_default_smiley)
            fallback(R.drawable.ic_default_smiley)
        }
    }
}


