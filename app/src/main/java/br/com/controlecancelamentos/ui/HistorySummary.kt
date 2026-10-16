package br.com.controlecancelamentos.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.com.controlecancelamentos.data.*
import br.com.controlecancelamentos.report.Reports
import java.io.File
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Filters(
    val search: String = "", val from: String = "", val to: String = "",
    val storeId: String = "TODAS", val operator: String = "", val reason: String = "Todos",
    val minAmount: String = "", val maxAmount: String = ""
)

private fun invalidFilter(f: Filters): String? {
    if (f.from.isNotBlank() && parseLocalDate(f.from) == null) return "A data inicial deve estar em dd/MM/aaaa."
    if (f.to.isNotBlank() && parseLocalDate(f.to) == null) return "A data final deve estar em dd/MM/aaaa."
    if (f.from.isNotBlank() && f.to.isNotBlank() && parseLocalDate(f.from)!! > parseLocalDate(f.to)!!)
        return "A data inicial é posterior à data final."
    if (f.minAmount.isNotBlank() && parseMoney(f.minAmount) == null) return "O valor mínimo é inválido."
    if (f.maxAmount.isNotBlank() && parseMoney(f.maxAmount) == null) return "O valor máximo é inválido."
    if (f.minAmount.isNotBlank() && f.maxAmount.isNotBlank() && parseMoney(f.minAmount)!! > parseMoney(f.maxAmount)!!)
        return "O valor mínimo é maior que o máximo."
    return null
}

private fun filtered(items: List<CancellationEntity>, f: Filters): List<CancellationEntity> {
    if (invalidFilter(f) != null) return emptyList()
    val start = parseLocalDate(f.from)
    val end = parseLocalDate(f.to)
    val minimum = parseMoney(f.minAmount)
    val maximum = parseMoney(f.maxAmount)
    return items.filter { e ->
        (start == null || e.dateIso >= start) && (end == null || e.dateIso <= end) &&
        (f.storeId == "TODAS" || e.storeId == f.storeId) &&
        (f.reason == "Todos" || e.reason == f.reason) &&
        (f.operator.isBlank() || e.operatorName.contains(f.operator.trim(), true)) &&
        (minimum == null || e.amountCents >= minimum) && (maximum == null || e.amountCents <= maximum) &&
        (f.search.isBlank() || listOf(e.coupon, e.operatorName, e.fiscalName, e.managerName, e.storeName,
            e.reason, e.ccf, e.pdv).any { it.contains(f.search.trim(), ignoreCase = true) })
    }
}

@Composable
private fun FilterPanel(f: Filters, change: (Filters) -> Unit, stores: List<StoreEntity>, includeSearch: Boolean) {
    var expanded by remember { mutableStateOf(includeSearch) }
    PremiumPanel {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FilterList, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Filtros", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { change(Filters()) }) { Text("Limpar") }
            IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
        }
        if (includeSearch) AppTextField(f.search, { change(f.copy(search = it)) }, "Buscar cupom ou responsável")
        invalidFilter(f)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (expanded) {
            Text("Período", color = Grey, style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    "Hoje" to DateFilterUtils.Shortcut.TODAY,
                    "7 dias" to DateFilterUtils.Shortcut.LAST_7_DAYS,
                    "30 dias" to DateFilterUtils.Shortcut.LAST_30_DAYS,
                    "Este mês" to DateFilterUtils.Shortcut.THIS_MONTH,
                    "Mês anterior" to DateFilterUtils.Shortcut.PREVIOUS_MONTH
                )
                options.forEach { (label, shortcut) ->
                    val period = remember(shortcut) { DateFilterUtils.period(shortcut) }
                    FilterChip(
                        selected = f.from == period.from && f.to == period.to,
                        onClick = { change(f.copy(from = period.from, to = period.to)) },
                        label = { Text(label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DateFilterField("Data inicial", f.from, { change(f.copy(from = it)) }, Modifier.weight(1f))
                DateFilterField("Data final", f.to, { change(f.copy(to = it)) }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Digite somente números ou toque no calendário.", modifier = Modifier.weight(1f),
                    color = Grey, style = MaterialTheme.typography.labelSmall)
                TextButton(
                    onClick = { change(f.copy(from = "", to = "")) },
                    enabled = f.from.isNotBlank() || f.to.isNotBlank()
                ) { Text("Limpar datas") }
            }
            val storeNames = listOf("Todas as lojas") + stores.map { it.name }
            SimpleSelector("Loja", stores.firstOrNull { it.id == f.storeId }?.name ?: "Todas as lojas", storeNames) { name ->
                change(f.copy(storeId = stores.firstOrNull { it.name == name }?.id ?: "TODAS"))
            }
            AppTextField(f.operator, { change(f.copy(operator = it)) }, "Operador")
            SimpleSelector("Motivo", f.reason, listOf("Todos") + REASONS) { change(f.copy(reason = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(f.minAmount, { change(f.copy(minAmount = it)) }, "Valor mínimo R$", Modifier.weight(1f))
                AppTextField(f.maxAmount, { change(f.copy(maxAmount = it)) }, "Valor máximo R$", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, onEdit: (CancellationEntity) -> Unit) {
    val items by vm.cancellations.collectAsState()
    val stores by vm.stores.collectAsState()
    val actor by vm.session.collectAsState()
    val brand by vm.branding.collectAsState()
    val context = LocalContext.current
    var filters by remember { mutableStateOf(Filters()) }
    val entries = remember(items, filters) { filtered(items, filters) }
    var visibleCount by remember { mutableIntStateOf(50) }
    LaunchedEffect(filters) { visibleCount = 50 }
    val filtersValid = invalidFilter(filters) == null
    var deleteTarget by remember { mutableStateOf<CancellationEntity?>(null) }
    var exportData by remember { mutableStateOf<ByteArray?>(null) }
    var documentPending by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportData ?: byteArrayOf()) }
                ?: error("Não foi possível gravar o arquivo.")
        }.onSuccess { vm.notify("PDF exportado com sucesso.") }
            .onFailure { vm.notify("Erro ao exportar PDF: ${it.message}") }
        exportData = null
        documentPending = false
    }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportData ?: byteArrayOf()) }
                ?: error("Não foi possível gravar o arquivo.")
        }.onSuccess { vm.notify("CSV exportado com sucesso.") }
            .onFailure { vm.notify("Erro ao exportar CSV: ${it.message}") }
        exportData = null
        documentPending = false
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero("Cancelamentos", "Consulte, filtre, edite e emita relatórios")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FilterPanel(filters, { filters = it }, stores, includeSearch = true)
            PremiumPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${entries.size} registros", fontWeight = FontWeight.Bold)
                        Text("Total: ${showMoney(entries.sumOf { it.amountCents })}", color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Default.Assessment, null, tint = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            exporting = true
                            try {
                                exportData = withContext(Dispatchers.Default) { Reports.pdf(entries, brand) }
                                documentPending = true
                                pdfExport.launch("cancelamentos.pdf")
                            } catch (ex: Exception) { vm.notify("Falha no PDF: ${ex.message}") }
                            finally { exporting = false }
                        }
                    }, enabled = !exporting && !documentPending && filtersValid,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                        Icon(Icons.Default.PictureAsPdf, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("PDF")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            exporting = true
                            try {
                                exportData = withContext(Dispatchers.Default) { Reports.csv(entries) }
                                documentPending = true
                                csvExport.launch("cancelamentos.csv")
                            } catch (ex: Exception) { vm.notify("Falha no CSV: ${ex.message}") }
                            finally { exporting = false }
                        }
                    }, enabled = !exporting && !documentPending && filtersValid,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                        Icon(Icons.Default.TableChart, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("CSV")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            exporting = true
                            try {
                                val pdf = withContext(Dispatchers.Default) { Reports.pdf(entries, brand) }
                                Reports.print(context, pdf)
                            } catch (ex: Exception) { vm.notify("Falha na impressão: ${ex.message}") }
                            finally { exporting = false }
                        }
                    }, enabled = !exporting && !documentPending && filtersValid,
                        modifier = Modifier.weight(1.15f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                        Icon(Icons.Default.Print, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Imprimir")
                    }
                }
                if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (entries.isEmpty()) PremiumPanel {
                Icon(Icons.Default.Inbox, null, tint = MaterialTheme.colorScheme.primary)
                SectionTitle("Nenhum cancelamento encontrado")
                Text("Registre um novo cupom ou ajuste os filtros.", color = Grey)
            }
            entries.take(visibleCount).forEach { e ->
                PremiumPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cupom #${e.coupon}", style = MaterialTheme.typography.titleMedium)
                            Text("${fromIsoDate(e.dateIso)} • ${e.time} • ${e.storeName.ifBlank { "Sem loja" }}",
                                color = Grey, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(showMoney(e.amountCents), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(color = CanvasColor)
                    Text(e.reason, style = MaterialTheme.typography.bodyMedium)
                    Text("Operador: ${e.operatorName} | Fiscal: ${e.fiscalName}", color = Grey,
                        style = MaterialTheme.typography.bodySmall)
                    Text("Gerente: ${e.managerName} | Prevenção: ${e.lossPreventionName}", color = Grey,
                        style = MaterialTheme.typography.bodySmall)
                    if (e.pdv.isNotBlank() || e.ccf.isNotBlank()) Text("PDV: ${e.pdv} • CCF: ${e.ccf}", color = Grey, style = MaterialTheme.typography.bodySmall)
                    if (e.notes.isNotBlank()) Text("Obs.: ${e.notes}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (e.photoPath != null) {
                            TextButton(onClick = {
                                val file = File(e.photoPath)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    runCatching { context.startActivity(intent) }.onFailure { vm.notify("Nenhum visualizador disponível.") }
                                } else vm.notify("Imagem não localizada.")
                            }) { Text("Ver foto") }
                        }
                        if (actor?.role == "ADMIN" || actor?.id == e.createdBy) {
                            TextButton(onClick = { onEdit(e) }) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Text("Editar") }
                            TextButton(onClick = { deleteTarget = e }) {
                                Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp)); Text("Excluir", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (entries.size > visibleCount) {
                OutlinedButton(onClick = { visibleCount += 50 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Carregar mais • ${entries.size - visibleCount} restantes")
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    deleteTarget?.let { e ->
        AlertDialog(onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir cancelamento?") },
            text = { Text("O cupom ${e.coupon} sairá dos relatórios e indicadores. A operação ficará no histórico administrativo.") },
            confirmButton = { TextButton(onClick = { vm.delete(e); deleteTarget = null }) { Text("Confirmar exclusão") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } })
    }
}

@Composable
fun SummaryScreen(vm: AppViewModel) {
    val items by vm.cancellations.collectAsState()
    val stores by vm.stores.collectAsState()
    var filters by remember { mutableStateOf(Filters()) }
    val entries = remember(items, filters) { filtered(items, filters) }
    val total = entries.sumOf { it.amountCents }
    val average = if (entries.isNotEmpty()) total / entries.size else 0L
    val greatest = entries.maxOfOrNull { it.amountCents } ?: 0L
    val currentMonth = remember { YearMonth.now() }
    val months = remember(currentMonth) { (5 downTo 0).map { currentMonth.minusMonths(it.toLong()) } }
    val byMonth = remember(entries, months) {
        months.map { month ->
            month to entries.filter { it.dateIso.startsWith(month.toString()) }.sumOf { it.amountCents }
        }
    }
    val monthMaximum = byMonth.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero("Visão geral", "Indicadores reais dos seus registros")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FilterPanel(filters, { filters = it }, stores, includeSearch = false)
            PremiumPanel {
                Text("VALOR TOTAL CANCELADO", color = Grey, style = MaterialTheme.typography.labelMedium)
                Text(showMoney(total), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("${entries.size} cupons no período selecionado", color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Quantidade", entries.size.toString(), Modifier.weight(1f))
                StatCard("Valor médio", showMoney(average), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Maior cupom", showMoney(greatest), Modifier.weight(1f))
                StatCard("Lojas", entries.map { it.storeName }.distinct().count { it.isNotBlank() }.toString(), Modifier.weight(1f))
            }
            PremiumPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    SectionTitle("Evolução dos últimos 6 meses")
                }
                Text("Valores dos registros filtrados, agrupados por mês.",
                    color = Grey, style = MaterialTheme.typography.bodySmall)
                if (byMonth.none { it.second > 0L }) {
                    Text("Sem cancelamentos nos últimos seis meses para os filtros atuais.",
                        color = Grey, style = MaterialTheme.typography.bodySmall)
                }
                byMonth.forEach { (month, amount) ->
                    val label = month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag("pt-BR"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${label.replaceFirstChar { it.uppercase() }} ${month.year}",
                            modifier = Modifier.weight(1f), color = Grey, style = MaterialTheme.typography.bodySmall)
                        Text(showMoney(amount), style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(
                        progress = { (amount.toDouble() / monthMaximum.toDouble()).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            Ranking("Ranking por operador", entries.groupBy { it.operatorName }.mapValues { it.value.sumOf { c -> c.amountCents } })
            Ranking("Ranking por motivo", entries.groupBy { it.reason }.mapValues { it.value.sumOf { c -> c.amountCents } })
            Ranking("Ranking por loja", entries.groupBy { it.storeName.ifBlank { "Não informada" } }.mapValues { it.value.sumOf { c -> c.amountCents } })
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Grey, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Ranking(title: String, values: Map<String, Long>) {
    PremiumPanel {
        SectionTitle(title)
        val sorted = values.entries.sortedByDescending { it.value }.take(8)
        if (sorted.isEmpty()) Text("Sem dados neste período.", color = Grey)
        val max = sorted.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
        sorted.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", modifier = Modifier.width(26.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(item.key.ifBlank { "Não informado" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(showMoney(item.value), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(progress = { item.value.toFloat() / max.toFloat() },
                modifier = Modifier.fillMaxWidth().height(7.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
