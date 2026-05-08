package com.musab.niqdah.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseProvider {
    fun auth(context: Context): FirebaseAuth? = runCatching {
        if (ensureFirebaseApp(context)) FirebaseAuth.getInstance() else null
    }.getOrNull()

    fun firestore(context: Context): FirebaseFirestore? = runCatching {
        if (ensureFirebaseApp(context)) FirebaseFirestore.getInstance() else null
    }.getOrNull()

    private fun ensureFirebaseApp(context: Context): Boolean {
        val appContext = context.applicationContext
        return FirebaseApp.getApps(appContext).isNotEmpty() ||
            FirebaseApp.initializeApp(appContext) != null
    }
}
