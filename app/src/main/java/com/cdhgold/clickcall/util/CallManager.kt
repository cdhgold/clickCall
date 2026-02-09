package com.cdhgold.clickcall.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

class CallManager(private val context: Context) {

    fun makeCall(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            ContextCompat.startActivity(context, intent, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun dialNumber(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            ContextCompat.startActivity(context, intent, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

