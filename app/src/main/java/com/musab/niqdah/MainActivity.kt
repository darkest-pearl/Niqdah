package com.musab.niqdah

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.musab.niqdah.ui.NiqdahApp
import com.musab.niqdah.ui.theme.NiqdahTheme

class MainActivity : ComponentActivity() {
    private var openTransactionsRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Niqdah)
        super.onCreate(savedInstanceState)
        if (intent.shouldOpenTransactions()) {
            openTransactionsRequest += 1
        }
        enableEdgeToEdge()
        setContent {
            NiqdahTheme {
                NiqdahApp(openTransactionsRequest = openTransactionsRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.shouldOpenTransactions()) {
            openTransactionsRequest += 1
        }
    }

    private fun Intent?.shouldOpenTransactions(): Boolean =
        this?.getBooleanExtra(EXTRA_OPEN_TRANSACTIONS, false) == true

    companion object {
        const val EXTRA_OPEN_TRANSACTIONS = "com.musab.niqdah.OPEN_TRANSACTIONS"
    }
}
