package com.musab.niqdah.ui.finance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musab.niqdah.domain.finance.AccountBalanceStatus
import com.musab.niqdah.domain.finance.CategoryBudgetWarning
import com.musab.niqdah.domain.finance.CategorySpendingBreakdown
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.CashProtectionCalculator
import com.musab.niqdah.domain.finance.CashProtectionRiskLevel
import com.musab.niqdah.domain.finance.DepositType
import com.musab.niqdah.domain.finance.DisciplineStatus
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.NecessaryItemDue
import com.musab.niqdah.domain.finance.SalaryCycleRules
import com.musab.niqdah.domain.finance.SavingsFollowUpReminderRules
import com.musab.niqdah.domain.finance.SavingsFollowUpState
import com.musab.niqdah.domain.finance.effectiveMinorUnits
import com.musab.niqdah.domain.finance.minorUnitsToMajor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

private val DashboardBackground = Color(0xFFF7F8FA)
private val CardNavy = Color(0xFF101C31)
private val CardNavyMuted = Color(0xFF2B364A)
private val Coral = Color(0xFFFF5A5F)
private val MutedBlueText = Color(0xFF657084)
private val NecessaryGreen = Color(0xFF31C48D)
private val OptionalAmber = Color(0xFFF5B84B)
private val AvoidRed = Color(0xFFFF5A5F)
private val SavingsGreen = Color(0xFF37D67A)
private val ProtectionYellow = Color(0xFFFFD166)
private val ProtectionOrange = Color(0xFFFF9F43)
private val ProtectionNeutral = Color(0xFF8C96A8)

@Composable
fun DashboardScreen(
    uiState: FinanceUiState,
    padding: PaddingValues,
    onClearError: () -> Unit,
    userEmail: String? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HomeHeader(
                name = uiState.data.profile.displayName(userEmail),
                subtitle = userEmail?.takeIf { it.isNotBlank() } ?: "Your finance overview"
            )
        }
        item { ErrorBanner(message = uiState.errorMessage, onDismiss = onClearError) }
        item { StatusBanner(message = uiState.statusMessage, onDismiss = onClearError) }
        if (uiState.isLoading) {
            item { LoadingStateCard(message = "Loading your finance dashboard...") }
        } else {
            item { MainBalanceProgressCard(uiState = uiState) }
            item { DashboardMetricGrid(metrics = dashboardMetrics(uiState)) }
            if (uiState.shouldShowSalaryReminder()) {
                item {
                    LightInsightCard(
                        title = "Salary not recorded yet",
                        body = "Record salary manually or wait for a reviewed bank import."
                    )
                }
            }
            if (uiState.dashboard.overspendingAlerts.isNotEmpty()) {
                item { SectionHeader(title = "Category alerts") }
                uiState.dashboard.overspendingAlerts.take(3).forEach { alert ->
                    item {
                        OverspendingCard(
                            title = alert.category.name,
                            amount = formatMoney(-alert.remaining, uiState.data.profile.currency)
                        )
                    }
                }
            }
            item {
                RecentActivityCard(uiState = uiState)
            }
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
    }
}

@Composable
fun HomeHeader(
    name: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = CardNavy
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CardNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedBlueText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HeaderIconButton(icon = Icons.Rounded.Search, contentDescription = "Search")
        HeaderIconButton(icon = Icons.Rounded.Notifications, contentDescription = "Notifications")
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = CardNavy
            )
        }
    }
}

@Composable
fun MainBalanceProgressCard(
    uiState: FinanceUiState,
    modifier: Modifier = Modifier
) {
    val status = uiState.data.latestDailyUseBalanceStatus
    val month = FinanceDates.currentYearMonth()
    val cycle = SalaryCycleRules.activeCycle(uiState.data, month)
    val cashProtection = CashProtectionCalculator.evaluate(uiState.data, month)
    val savingsFollowUp = SavingsFollowUpReminderRules.evaluate(uiState.data, month, LocalDate.now())
    val currency = status?.currency ?: uiState.data.profile.currency
    val amountText = status?.let { formatMoneyMinor(it.amountMinor, it.currency) } ?: "Not confirmed yet"
    val cycleStatus = when {
        cycle == null -> "Salary cycle not active"
        !cycle.isOpeningBalanceConfirmed -> "Opening balance not confirmed"
        else -> "Since salary deposit"
    }
    val ringLabel = when (cashProtection.riskLevel) {
        CashProtectionRiskLevel.UNKNOWN_OPENING_BALANCE -> "--"
        else -> formatProgress(cashProtection.ringProgress)
    }
    val supportLines = buildList {
        add(cashProtection.message)
        add("Unpaid protected obligations: ${formatMoneyMinor(cashProtection.protectedUnpaidObligationsMinor, currency)}")
        if (cashProtection.riskLevel != CashProtectionRiskLevel.UNKNOWN_OPENING_BALANCE) {
            add("Flexible buffer left: ${formatMoneyMinor(cashProtection.flexibleBufferLeftMinor, currency)}")
        }
        if (savingsFollowUp.state == SavingsFollowUpState.DUE ||
            savingsFollowUp.state == SavingsFollowUpState.NOT_DUE
        ) {
            add("Savings transfer pending: ${formatMoneyMinor(savingsFollowUp.remainingSavingsTargetMinor, currency)}")
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Daily-use / Checking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = cycleStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB9C2D2)
                    )
                }
                Text(
                    text = cashProtection.riskLevel.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cashProtection.riskLevel.color()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CashProtectionRainbowRing(
                    progress = cashProtection.ringProgress,
                    riskLevel = cashProtection.riskLevel,
                    label = ringLabel,
                    modifier = Modifier.size(112.dp),
                    strokeWidth = 12.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    supportLines.take(4).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD5DBE6),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    status.lastUpdatedLine().firstOrNull()?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA8B2C4),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardMetricGrid(
    metrics: List<DashboardMetric>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowMetrics.forEach { metric ->
                    CircularMetricCard(
                        metric = metric,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.72f)
                    )
                }
                if (rowMetrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CircularMetricCard(
    metric: DashboardMetric,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = metric.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SegmentedCircularProgressRing(
                totalBudgetMinor = metric.totalBudgetMinor,
                necessaryMinor = metric.necessaryMinor,
                optionalMinor = metric.optionalMinor,
                avoidMinor = metric.avoidMinor,
                ringBackgroundColor = CardNavyMuted,
                necessaryColor = metric.necessaryColor,
                optionalColor = metric.optionalColor,
                avoidColor = metric.avoidColor,
                centerLabel = metric.centerLabel,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(76.dp),
                strokeWidth = 8.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = metric.amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metric.supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (metric.isOverBudget) Coral else Color(0xFFB9C2D2),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                metric.legendLines.forEach { line ->
                    MetricLegendRow(line = line)
                }
            }
        }
    }
}

@Composable
fun SegmentedCircularProgressRing(
    totalBudgetMinor: Long,
    necessaryMinor: Long,
    optionalMinor: Long,
    avoidMinor: Long,
    ringBackgroundColor: Color,
    necessaryColor: Color,
    optionalColor: Color,
    avoidColor: Color,
    centerLabel: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp
) {
    val spentMinor = necessaryMinor + optionalMinor + avoidMinor
    val visualScale = when {
        totalBudgetMinor <= 0L || spentMinor <= 0L -> 0.0
        spentMinor > totalBudgetMinor -> totalBudgetMinor.toDouble() / spentMinor.toDouble()
        else -> 1.0
    }
    val necessaryProgress by animateFloatAsState(
        targetValue = segmentProgress(necessaryMinor, totalBudgetMinor, visualScale),
        animationSpec = tween(durationMillis = 700),
        label = "necessarySegment"
    )
    val optionalProgress by animateFloatAsState(
        targetValue = segmentProgress(optionalMinor, totalBudgetMinor, visualScale),
        animationSpec = tween(durationMillis = 700),
        label = "optionalSegment"
    )
    val avoidProgress by animateFloatAsState(
        targetValue = segmentProgress(avoidMinor, totalBudgetMinor, visualScale),
        animationSpec = tween(durationMillis = 700),
        label = "avoidSegment"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = ringBackgroundColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            var startAngle = -90f
            listOf(
                necessaryProgress to necessaryColor,
                optionalProgress to optionalColor,
                avoidProgress to avoidColor
            ).forEach { (progress, color) ->
                if (progress > 0f) {
                    val sweep = progress * 360f
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = stroke
                    )
                    startAngle += sweep
                }
            }
        }
        Text(
            text = centerLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun CashProtectionRainbowRing(
    progress: Double,
    riskLevel: CashProtectionRiskLevel,
    label: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (riskLevel == CashProtectionRiskLevel.UNKNOWN_OPENING_BALANCE) {
            0f
        } else {
            progress.coerceIn(0.0, 1.0).toFloat()
        },
        animationSpec = tween(durationMillis = 700),
        label = "cashProtectionProgress"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            if (riskLevel == CashProtectionRiskLevel.UNKNOWN_OPENING_BALANCE) {
                drawArc(
                    color = CardNavyMuted,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
            } else {
                listOf(
                    AvoidRed,
                    ProtectionOrange,
                    ProtectionYellow,
                    SavingsGreen
                ).forEachIndexed { index, color ->
                    drawArc(
                        color = color.copy(alpha = 0.32f),
                        startAngle = -90f + (index * 90f),
                        sweepAngle = 82f,
                        useCenter = false,
                        style = stroke
                    )
                }
                val sweepProgress = if (riskLevel == CashProtectionRiskLevel.PROTECTED_FUNDS_AT_RISK) {
                    1f
                } else {
                    animatedProgress
                }
                drawArc(
                    color = riskLevel.color(),
                    startAngle = -90f,
                    sweepAngle = sweepProgress * 360f,
                    useCenter = false,
                    style = stroke
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

private fun CashProtectionRiskLevel.color(): Color =
    when (this) {
        CashProtectionRiskLevel.HEALTHY -> SavingsGreen
        CashProtectionRiskLevel.WATCH -> ProtectionYellow
        CashProtectionRiskLevel.TIGHT -> ProtectionOrange
        CashProtectionRiskLevel.PROTECTED_FUNDS_AT_RISK -> AvoidRed
        CashProtectionRiskLevel.UNKNOWN_OPENING_BALANCE -> ProtectionNeutral
    }

@Composable
private fun MetricLegendRow(line: MetricLegendLine) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(7.dp), shape = CircleShape, color = line.color) {}
        Text(
            text = "${line.label} ${line.value}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB9C2D2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun segmentProgress(amountMinor: Long, totalBudgetMinor: Long, visualScale: Double): Float =
    if (totalBudgetMinor <= 0L || amountMinor <= 0L) {
        0f
    } else {
        ((amountMinor.toDouble() * visualScale) / totalBudgetMinor.toDouble()).toFloat().coerceIn(0f, 1f)
    }

data class DashboardMetric(
    val title: String,
    val amountText: String,
    val supportingText: String,
    val totalBudgetMinor: Long,
    val necessaryMinor: Long,
    val optionalMinor: Long = 0L,
    val avoidMinor: Long = 0L,
    val centerLabel: String,
    val isOverBudget: Boolean = false,
    val necessaryColor: Color = NecessaryGreen,
    val optionalColor: Color = OptionalAmber,
    val avoidColor: Color = AvoidRed,
    val legendLines: List<MetricLegendLine> = emptyList()
)

data class MetricLegendLine(
    val label: String,
    val value: String,
    val color: Color
)

private fun dashboardMetrics(uiState: FinanceUiState): List<DashboardMetric> {
    val currency = uiState.data.profile.currency
    val metrics = mutableListOf<DashboardMetric>()
    savingsMetric(uiState)?.let(metrics::add)
    debtMetric(uiState)?.let(metrics::add)
    metrics += uiState.dashboard.categorySpendingBreakdowns
        .filter { breakdown ->
            breakdown.category.type != CategoryType.SAVINGS &&
                breakdown.category.type != CategoryType.DEBT &&
                (breakdown.budgetMinor > 0L || breakdown.spentMinor > 0L)
        }
        .sortedWith(compareBy<CategorySpendingBreakdown> { categoryPriority(it.category.id, it.category.name) }.thenBy { it.category.name })
        .take(8)
        .map { breakdown ->
            val budgetText = formatMoney(breakdown.budget, currency)
            DashboardMetric(
                title = breakdown.category.name.displayCategoryName(),
                amountText = formatMoney(breakdown.spent, currency),
                supportingText = if (breakdown.budgetMinor > 0L) {
                    if (breakdown.isOverspent) {
                        "${formatMoney(breakdown.overBudgetAmount, currency)} over budget"
                    } else {
                        "of $budgetText budget"
                    }
                } else {
                    "No monthly budget"
                },
                totalBudgetMinor = breakdown.budgetMinor,
                necessaryMinor = breakdown.necessaryMinor,
                optionalMinor = breakdown.optionalMinor,
                avoidMinor = breakdown.avoidMinor,
                centerLabel = if (breakdown.budgetMinor > 0L) formatProgress(breakdown.visualProgress) else "--",
                isOverBudget = breakdown.isOverspent,
                legendLines = listOf(
                    MetricLegendLine("Necessary", formatMoney(breakdown.necessarySpent, currency), NecessaryGreen),
                    MetricLegendLine("Optional", formatMoney(breakdown.optionalSpent, currency), OptionalAmber),
                    MetricLegendLine("Avoid", formatMoney(breakdown.avoidSpent, currency), AvoidRed)
                )
            )
        }
    if (metrics.isEmpty()) {
        metrics += DashboardMetric(
            title = "Budgets",
            amountText = formatMoney(uiState.dashboard.totalSpent, currency),
            supportingText = "Add category budgets to fill the grid",
            totalBudgetMinor = 0L,
            necessaryMinor = 0L,
            centerLabel = "--"
        )
    }
    return metrics
}

private fun savingsMetric(uiState: FinanceUiState): DashboardMetric? {
    val status = uiState.data.latestSavingsBalanceStatus
    val goal = uiState.data.primaryGoal
    val currency = status?.currency ?: uiState.data.profile.currency
    val target = goal?.targetAmount?.takeIf { it > 0.0 } ?: return status?.let {
        DashboardMetric(
            title = "Savings",
            amountText = formatMoneyMinor(it.amountMinor, it.currency),
            supportingText = "No goal target set",
            totalBudgetMinor = 0L,
            necessaryMinor = 0L,
            centerLabel = "--"
        )
    }
    val savingsBalanceText = status?.let { "Balance ${formatMoneyMinor(it.amountMinor, it.currency)}" }
    val saved = goal.savedAmount
    val progress = saved / target
    val targetMinor = effectiveMinorUnits(goal.targetAmountMinor, goal.targetAmount)
    val savedMinor = effectiveMinorUnits(goal.savedAmountMinor, goal.savedAmount)
    val remaining = max(0.0, target - saved)
    val requiredMonthly = uiState.dashboard.disciplineStatus.januaryCountdown.requiredMonthlySavings
    val targetDateText = goal.targetDate.takeIf { it.isNotBlank() }?.let { "by $it" } ?: "target"
    return DashboardMetric(
        title = "Savings",
        amountText = formatMoney(saved, currency),
        supportingText = when {
            progress >= 1.0 -> "Target completed"
            savingsBalanceText != null -> "$savingsBalanceText; ${formatMoney(remaining, currency)} left"
            requiredMonthly > 0.0 -> "Need ${formatMoney(requiredMonthly, currency)}/month"
            else -> "${formatMoney(remaining, currency)} left"
        },
        totalBudgetMinor = targetMinor,
        necessaryMinor = savedMinor.coerceAtMost(targetMinor),
        centerLabel = formatProgress(progress),
        necessaryColor = SavingsGreen,
        optionalColor = SavingsGreen,
        avoidColor = SavingsGreen,
        legendLines = listOfNotNull(
            MetricLegendLine("Target", "${formatMoney(target, currency)} $targetDateText", SavingsGreen),
            savingsPaceLine(progress, requiredMonthly, uiState.data.profile.monthlySavingsTarget, currency)
        )
    )
}

private fun debtMetric(uiState: FinanceUiState): DashboardMetric? {
    val debt = uiState.data.debt
    val currency = uiState.data.profile.currency
    if (debt.startingAmount <= 0.0 && debt.startingAmountMinor <= 0L) return null
    val starting = minorUnitsToMajor(effectiveMinorUnits(debt.startingAmountMinor, debt.startingAmount))
    val remaining = minorUnitsToMajor(effectiveMinorUnits(debt.remainingAmountMinor, debt.remainingAmount))
    val paid = max(0.0, starting - remaining)
    val progress = if (starting > 0.0) paid / starting else null
    val startingMinor = effectiveMinorUnits(debt.startingAmountMinor, debt.startingAmount)
    val paidMinor = max(0L, startingMinor - effectiveMinorUnits(debt.remainingAmountMinor, debt.remainingAmount))
    return DashboardMetric(
        title = "Debt",
        amountText = formatMoney(remaining, currency),
        supportingText = "remaining from ${formatMoney(starting, currency)}",
        totalBudgetMinor = startingMinor,
        necessaryMinor = paidMinor,
        centerLabel = progress?.let { formatProgress(it) } ?: "--",
        necessaryColor = Coral,
        optionalColor = Coral,
        avoidColor = Coral,
        legendLines = listOf(
            MetricLegendLine("Paid", formatMoney(paid, currency), Coral)
        )
    )
}

private fun savingsPaceLine(
    progress: Double,
    requiredMonthly: Double,
    currentMonthlyTarget: Double,
    currency: String
): MetricLegendLine {
    val label = when {
        progress >= 1.0 -> "Complete"
        currentMonthlyTarget > 0.0 && requiredMonthly < currentMonthlyTarget -> "Ahead"
        else -> "Required"
    }
    val value = when (label) {
        "Complete" -> "done"
        "Ahead" -> "of schedule"
        else -> "${formatMoney(requiredMonthly, currency)}/mo"
    }
    return MetricLegendLine(label, value, SavingsGreen)
}

private fun categoryPriority(id: String, name: String): Int {
    val key = "${id.lowercase()} ${name.lowercase()}"
    return when {
        FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID in key || "food" in key || "transport" in key -> 0
        "subscription" in key -> 1
        "family" in key -> 2
        "bill" in key || "rent" in key || "phone" in key || "internet" in key -> 3
        "medical" in key -> 4
        "clothing" in key || "shopping" in key -> 5
        else -> 10
    }
}

private fun String.displayCategoryName(): String =
    when {
        equals("Food/transport", ignoreCase = true) -> "Food/Transportation"
        equals("Family", ignoreCase = true) -> "Family Support"
        else -> this
    }

private fun com.musab.niqdah.domain.finance.UserProfile.displayName(userEmail: String?): String {
    val localPart = userEmail
        ?.substringBefore("@")
        ?.replace('.', ' ')
        ?.replace('_', ' ')
        ?.trim()
        .orEmpty()
    return localPart
        .takeIf { it.isNotBlank() }
        ?.split(" ")
        ?.joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase() } }
        ?: "Niqdah"
}

private fun FinanceUiState.shouldShowSalaryReminder(): Boolean {
    if (data.profile.salary <= 0.0 && data.profile.salaryMinor <= 0L) return false
    val salaryDay = data.profile.salaryDayOfMonth.coerceIn(1, 31)
    val today = LocalDate.now()
    if (today.dayOfMonth < salaryDay) return false
    val currentMonth = FinanceDates.currentYearMonth()
    return data.incomeTransactions.none {
        it.yearMonth == currentMonth && it.depositType == DepositType.SALARY
    }
}

private fun AccountBalanceStatus?.lastUpdatedLine(): List<String> =
    this?.takeIf { it.lastUpdatedMillis > 0L }?.let {
        listOf("Updated ${formatTransactionDateTime(it.lastUpdatedMillis)} from ${it.source.label}.")
    } ?: emptyList()

@Composable
private fun LightInsightCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = body, color = MutedBlueText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RecentActivityCard(uiState: FinanceUiState) {
    val currency = uiState.data.profile.currency
    val categoryById = uiState.data.categories.associateBy { it.id }
    val recent = (
        uiState.data.transactions.map {
            val category = categoryById[it.categoryId]
            val prefix = when (category?.type) {
                CategoryType.SAVINGS -> "Saved"
                CategoryType.DEBT -> "Debt paid"
                else -> "Spent"
            }
            "$prefix ${formatMoney(it.amount, it.currency.ifBlank { currency })} - ${it.note.ifBlank { category?.name ?: "Activity" }}" to it.occurredAtMillis
        } +
            uiState.data.incomeTransactions.map { "${formatMoney(it.amount, it.currency.ifBlank { currency })} - ${it.source.ifBlank { "Income" }}" to it.occurredAtMillis }
        )
        .sortedByDescending { it.second }
        .take(3)
    if (recent.isEmpty()) return
    LightInsightCard(
        title = "Recent activity",
        body = recent.joinToString(separator = "\n") { it.first }
    )
}

@Composable
private fun OverspendingCard(title: String, amount: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8E8))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Coral
            )
            Text(
                text = "$title is over budget by $amount.",
                color = CardNavy,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun warningSummary(warnings: List<CategoryBudgetWarning>): String =
    if (warnings.isEmpty()) {
        "No categories are near their warning line."
    } else {
        warnings.take(3).joinToString(separator = "\n") { it.message }
    }

private fun dueSummary(items: List<NecessaryItemDue>): String =
    if (items.isEmpty()) {
        "No necessary items are due soon."
    } else {
        items.take(3).joinToString(separator = "\n") { due ->
            val timing = when (due.daysUntilDue) {
                0L -> "today"
                1L -> "tomorrow"
                else -> "in ${due.daysUntilDue} days"
            }
            "${due.item.title} is due $timing."
        }
    }

@Suppress("unused")
private fun disciplineSummary(disciplineStatus: DisciplineStatus, currency: String): String =
    listOf(
        "Savings ${formatMoney(disciplineStatus.savingsTarget.savedThisMonth, currency)} of ${formatMoney(disciplineStatus.savingsTarget.targetAmount, currency)}",
        "Safe to spend ${formatMoney(disciplineStatus.safeToSpendAmount, currency)}",
        warningSummary(disciplineStatus.categoryWarnings),
        dueSummary(disciplineStatus.necessaryItemsDueSoon)
    ).joinToString(separator = "\n")
