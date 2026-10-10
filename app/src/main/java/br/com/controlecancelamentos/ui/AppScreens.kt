package br.com.controlecancelamentos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.controlecancelamentos.R
import br.com.controlecancelamentos.data.CancellationEntity

private enum class Page { RESUMO, REGISTRAR, CANCELAMENTOS, ADMIN }

@Composable
fun CancelamentosApp(vm: AppViewModel) {
    val appearance by vm.appearance.collectAsState()
    PremiumTheme(appearance) {
        val brand by vm.branding.collectAsState()
        val ready by vm.ready.collectAsState()
        val requiresSetup by vm.requiresSetup.collectAsState()
        val session by vm.session.collectAsState()
        val msg by vm.message.collectAsState()
        val busy by vm.busy.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(msg) {
            if (msg != null) {
                snackbar.showSnackbar(msg!!)
                vm.dismissMessage()
            }
        }
        CompositionLocalProvider(LocalBranding provides brand) {
            Box(Modifier.fillMaxSize().background(CanvasColor)) {
                if (!ready) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Image(painter = painterResource(R.drawable.elite_brand), contentDescription = null, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
                        Text("Controle de Cancelamentos Elite", color = Ink, style = MaterialTheme.typography.titleMedium)
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                } else if (session == null) {
                    AccessScreen(
                        isSetup = requiresSetup,
                        busy = busy,
                        onSetup = { n, e, p -> vm.setup(n, e, p) },
                        onLogin = { e, p -> vm.login(e, p) },
                        onInvite = { n, e, p, code -> vm.registerWithInvite(n, e, p, code) }
                    )
                } else {
                    var page by remember { mutableStateOf(Page.RESUMO) }
                    var editing by remember { mutableStateOf<CancellationEntity?>(null) }
                    var formDirty by remember { mutableStateOf(false) }
                    var pendingPage by remember { mutableStateOf<Page?>(null) }
                    var pendingLogout by remember { mutableStateOf(false) }

                    fun navigate(destination: Page) {
                        if (page == destination) return
                        if (page == Page.REGISTRAR && formDirty) {
                            pendingPage = destination
                        } else {
                            page = destination
                            editing = null
                            formDirty = false
                        }
                    }

                    BackHandler(enabled = page == Page.REGISTRAR && formDirty) {
                        pendingPage = Page.CANCELAMENTOS
                    }

                    val actor = session!!
                    Scaffold(
                        containerColor = CanvasColor,
                        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                        topBar = {
                            Surface(color = Navy, shadowElevation = 1.dp) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Brush.linearGradient(listOf(Navy, NavyLight)))
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.elite_brand),
                                            contentDescription = "Logo do app",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(actor.name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                                        Text(
                                            if (actor.role == "ADMIN") "Administrador" else "Operador",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFD3DEF1)
                                        )
                                    }
                                    TextButton(
                                        enabled = !busy,
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                        onClick = {
                                            if (page == Page.REGISTRAR && formDirty) pendingLogout = true
                                            else {
                                                vm.logout()
                                                page = Page.RESUMO
                                                editing = null
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Logout, "Sair", Modifier.size(18.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Sair")
                                    }
                                }
                            }
                        },
                        bottomBar = {
                            NavigationBar(
                                modifier = Modifier.navigationBarsPadding(),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp
                            ) {
                                val pages = buildList {
                                    add(Triple(Page.RESUMO, "Resumo", Icons.Default.Dashboard))
                                    add(Triple(Page.REGISTRAR, "Registrar", Icons.Default.AddCircleOutline))
                                    add(Triple(Page.CANCELAMENTOS, "Histórico", Icons.Default.ReceiptLong))
                                    if (actor.role == "ADMIN") add(Triple(Page.ADMIN, "Gestão", Icons.Default.AdminPanelSettings))
                                }
                                pages.forEach { (target, title, image) ->
                                    NavigationBarItem(
                                        selected = page == target,
                                        enabled = !busy,
                                        onClick = { navigate(target) },
                                        icon = { Icon(image, contentDescription = title) },
                                        label = { Text(title) }
                                    )
                                }
                            }
                        },
                        snackbarHost = { SnackbarHost(snackbar) }
                    ) { insets ->
                        Box(Modifier.fillMaxSize().padding(insets)) {
                            when (page) {
                                Page.RESUMO -> SummaryScreen(vm)
                                Page.REGISTRAR -> RegisterScreen(
                                    vm,
                                    editing,
                                    busy,
                                    onDirtyChange = { formDirty = it },
                                    onDone = {
                                        formDirty = false
                                        editing = null
                                        page = Page.CANCELAMENTOS
                                    }
                                )
                                Page.CANCELAMENTOS -> HistoryScreen(vm, onEdit = {
                                    editing = it
                                    formDirty = false
                                    page = Page.REGISTRAR
                                })
                                Page.ADMIN -> AdminScreen(vm, onLogout = {
                                    vm.logout()
                                    page = Page.RESUMO
                                })
                            }
                        }
                    }

                    if (pendingLogout) {
                        AlertDialog(
                            onDismissRequest = { pendingLogout = false },
                            title = { Text("Sair sem salvar?") },
                            text = { Text("O formulário contém informações não salvas. Deseja sair da conta e descartá-las?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingLogout = false
                                    formDirty = false
                                    editing = null
                                    page = Page.RESUMO
                                    vm.logout()
                                }) { Text("Sair e descartar") }
                            },
                            dismissButton = { TextButton(onClick = { pendingLogout = false }) { Text("Continuar preenchendo") } }
                        )
                    }
                    pendingPage?.let { target ->
                        AlertDialog(
                            onDismissRequest = { pendingPage = null },
                            title = { Text("Descartar alterações?") },
                            text = { Text("Há informações de cancelamento ainda não salvas. Se sair, elas serão perdidas.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    formDirty = false
                                    page = target
                                    editing = null
                                    pendingPage = null
                                }) { Text("Descartar e sair") }
                            },
                            dismissButton = { TextButton(onClick = { pendingPage = null }) { Text("Continuar preenchendo") } }
                        )
                    }
                }
                if (session == null) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                        SnackbarHost(snackbar)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessScreen(
    isSetup: Boolean,
    busy: Boolean,
    onSetup: (String, String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    onInvite: (String, String, String, String) -> Unit
) {
    var join by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Hero(
            if (isSetup) "Bem-vindo ao controle" else "Acesso seguro",
            if (isSetup) "Configure a primeira conta administradora" else "Entre para gerenciar seus cancelamentos"
        )
        Spacer(Modifier.height(18.dp))
        BrandShowcase(modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(18.dp))
        PremiumPanel(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Navy, NavyLight)))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.elite_brand),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column {
                    SectionTitle(when {
                        isSetup -> "Criar administrador"
                        join -> "Aceitar convite"
                        else -> "Entrar na conta"
                    })
                    Text("Login protegido com identidade visual do Cancelamentos Elite", style = MaterialTheme.typography.bodySmall, color = Grey)
                }
            }
            if (isSetup || join) AppTextField(name, { name = it }, "Nome completo")
            AppTextField(email, { email = it }, "E-mail")
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha (mínimo 8 caracteres)") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            if (join && !isSetup) AppTextField(invite, { invite = it }, "Código do convite")
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    when {
                        isSetup -> onSetup(name, email, password)
                        join -> onInvite(name, email, password, invite)
                        else -> onLogin(email, password)
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(when {
                    isSetup -> "Iniciar aplicativo"
                    join -> "Criar minha conta"
                    else -> "Entrar"
                })
            }
            if (!isSetup) TextButton(onClick = { join = !join }) { Text(if (join) "Já tenho conta" else "Tenho um convite") }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Android nativo • registros locais neste aparelho",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Grey
        )
    }
}

@Composable
private fun BrandShowcase(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 4.dp,
        color = Color.Transparent
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(Navy, NavyLight, Color(0xFF355FE8))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.elite_brand),
                        contentDescription = "Logo do Cancelamentos Elite",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(LocalBranding.current.appName, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Controle profissional de cancelamentos", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDCE7FF))
                    Text("OCR • Auditoria • Relatórios • Segurança", style = MaterialTheme.typography.labelMedium, color = Mint)
                }
            }
            Text(
                "Gestão segura de registros • Android nativo",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFC8D9FA)
            )
        }
    }
}
