package com.musab.niqdah.ui.finance

import com.musab.niqdah.domain.finance.AccountBalanceStatus
import com.musab.niqdah.domain.finance.FinanceData

data class QaDiagnostics(
    val sections: List<QaDiagnosticsSection>
)

data class QaDiagnosticsSection(
    val title: String,
    val rows: List<QaDiagnosticsRow>
)

data class QaDiagnosticsRow(
    val label: String,
    val value: String
)

fun buildQaDiagnostics(
    appVersionName: String,
    appVersionCode: Int,
    buildType: String,
    firebaseAuthStatus: String,
    isUidPresent: Boolean,
    isSmsPermissionGranted: Boolean,
    isNotificationPermissionGranted: Boolean,
    aiHealthStatus: String,
    data: FinanceData
): QaDiagnostics {
    val settings = data.bankMessageSettings
    val activeSalaryCycle = data.salaryCycles
        .filter { it.isActive }
        .maxByOrNull { it.salaryDepositDateMillis }

    return QaDiagnostics(
        sections = listOf(
            QaDiagnosticsSection(
                title = "App",
                rows = listOf(
                    QaDiagnosticsRow("Version", "$appVersionName ($appVersionCode)"),
                    QaDiagnosticsRow("Build type", buildType),
                    QaDiagnosticsRow("Firebase auth", firebaseAuthStatus),
                    QaDiagnosticsRow("Current UID", if (isUidPresent) "present" else "not present"),
                    QaDiagnosticsRow("AI health", aiHealthStatus)
                )
            ),
            QaDiagnosticsSection(
                title = "Permissions and setup",
                rows = listOf(
                    QaDiagnosticsRow("SMS permission", grantedText(isSmsPermissionGranted)),
                    QaDiagnosticsRow("Notification permission", grantedText(isNotificationPermissionGranted)),
                    QaDiagnosticsRow("Automatic SMS import", enabledText(settings.isAutomaticSmsImportEnabled)),
                    QaDiagnosticsRow("Daily-use sender", settings.dailyUseSource.senderName.ifBlank { "not configured" }),
                    QaDiagnosticsRow("Savings sender", settings.savingsSource.senderName.ifBlank { "not configured" }),
                    QaDiagnosticsRow("Daily-use suffix", presentText(settings.dailyUseAccountSuffix.isNotBlank())),
                    QaDiagnosticsRow("Savings suffix", presentText(settings.savingsAccountSuffix.isNotBlank()))
                )
            ),
            QaDiagnosticsSection(
                title = "Last parser decision",
                rows = listOf(
                    QaDiagnosticsRow("Sender", settings.lastReceivedSender.ifBlank { "None" }),
                    QaDiagnosticsRow("Sender matched", yesNo(settings.lastSenderMatched)),
                    QaDiagnosticsRow("Ignored reason", safeParserReason(settings.lastIgnoredReason)),
                    QaDiagnosticsRow("Parsed type", settings.lastParsedResult.ifBlank { "None" }),
                    QaDiagnosticsRow("Pending created", yesNo(settings.lastCreatedPendingImport)),
                    QaDiagnosticsRow("Duplicate blocked", yesNo(settings.lastDuplicateBlocked)),
                    QaDiagnosticsRow(
                        "Timestamp",
                        settings.lastParserDecisionAtMillis
                            .takeIf { it > 0L }
                            ?.let { formatTransactionDateTime(it) }
                            ?: "None"
                    )
                )
            ),
            QaDiagnosticsSection(
                title = "Balances and salary cycle",
                rows = listOf(
                    QaDiagnosticsRow("Daily-use balance", data.latestDailyUseBalanceStatus.diagnosticBalanceText()),
                    QaDiagnosticsRow("Savings balance", data.latestSavingsBalanceStatus.diagnosticBalanceText()),
                    QaDiagnosticsRow(
                        "Active salary cycle",
                        activeSalaryCycle?.let { cycle ->
                            "Active: ${cycle.cycleMonth}, ${formatMoneyMinor(cycle.salaryDepositAmountMinor, cycle.currency)}, ${cycle.source.label}"
                        } ?: "No active cycle"
                    )
                )
            )
        )
    )
}

private fun AccountBalanceStatus?.diagnosticBalanceText(): String =
    this?.let {
        "${formatMoneyMinor(it.amountMinor, it.currency)} - ${it.confidence.label}"
    } ?: "None"

private fun grantedText(value: Boolean): String =
    if (value) "granted" else "not granted"

private fun enabledText(value: Boolean): String =
    if (value) "enabled" else "disabled"

private fun presentText(value: Boolean): String =
    if (value) "present" else "not present"

private fun yesNo(value: Boolean): String =
    if (value) "yes" else "no"

private fun safeParserReason(reason: String): String {
    val trimmed = reason.trim()
    if (trimmed.isBlank()) return "None"
    val looksLikeSmsBody = Regex("""\b(AED|debited|credited|balance|account|card|OTP)\b""", RegexOption.IGNORE_CASE)
        .containsMatchIn(trimmed)
    return if (looksLikeSmsBody) "Raw SMS withheld" else trimmed
}
