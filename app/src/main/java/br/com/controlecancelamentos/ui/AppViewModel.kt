package br.com.controlecancelamentos.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import br.com.controlecancelamentos.ControleApp
import br.com.controlecancelamentos.data.*
import br.com.controlecancelamentos.security.Passwords
import br.com.controlecancelamentos.security.EncryptedArchive
import br.com.controlecancelamentos.security.CompleteBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import java.util.Base64

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val db = (application as ControleApp).db
    private val dao = db.dao()
    private val prefs = context.getSharedPreferences("session", 0)
    val users = dao.users().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val stores = dao.stores().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cancellations = dao.cancellations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val audit = dao.audit().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val branding = MutableStateFlow(BrandingStore.read(context))
    val appearance = MutableStateFlow(AppearanceStore.read(context))
    fun setAppearance(mode: Int) {
        if (mode !in AppearanceStore.SYSTEM..AppearanceStore.DARK) return
        AppearanceStore.save(context, mode)
        appearance.value = mode
    }
    val ready = MutableStateFlow(false)
    val requiresSetup = MutableStateFlow(false)
    val session = MutableStateFlow<UserEntity?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    data class RestorePreview(val stores: Int, val records: Int, val photos: Int = 0, val isComplete: Boolean = false)
    val restorePreview = MutableStateFlow<RestorePreview?>(null)
    private var pendingRestore: PortableBackup.Snapshot? = null
    private var pendingCompleteRestore: CompleteBackup.Prepared? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Cadastro inicial idempotente: preserva IDs, nomes e cancelamentos anteriores.
                db.withTransaction {
                    val missing = StoreCatalog.missing(dao.snapshotStores())
                    missing.forEach { dao.insertStore(StoreEntity(name = it.displayName)) }
                }
                val existing = dao.getUsers()
                requiresSetup.value = existing.isEmpty()
                val uid = prefs.getString("userId", null)
                session.value = uid?.let { dao.userById(it) }?.takeIf { it.enabled }
                ready.value = true
            }
        }
    }


    fun saveBranding(name: String, credit: String, idea: String) {
        if (session.value?.role != "ADMIN") { notify("Somente o administrador pode alterar a identidade."); return }
        val title = name.trim(); val author = credit.trim()
        if (title.length !in 3..50 || author.length !in 3..80 || idea.trim().length > 80) {
            notify("Nome: 3 a 50 caracteres; assinatura: 3 a 80; idealização: até 80."); return
        }
        val updated = BrandingSettings(title, author, idea.trim())
        BrandingStore.save(context, updated)
        branding.value = updated
        notify("Identidade do aplicativo atualizada neste aparelho.")
    }

    fun dismissMessage() { message.value = null }
    fun notify(text: String) { message.value = text }

    fun setup(name: String, email: String, password: String) = viewModelScope.launch {
        val err = credentialError(name, email, password)
        if (err != null) { notify(err); return@launch }
        busy.value = true
        try {
            val user = withContext(Dispatchers.IO) {
                db.withTransaction {
                    check(dao.getUsers().isEmpty()) { "O administrador já foi criado." }
                    val salt = Passwords.salt()
                    UserEntity(name = name.trim(), email = email.trim().lowercase(), salt = salt,
                        passwordHash = Passwords.hash(password, salt), role = "ADMIN").also { dao.insertUser(it) }
                }
            }
            requiresSetup.value = false
            startSession(user)
            notify("Conta administradora criada.")
        } catch (ex: Exception) { notify(ex.message ?: "Falha ao criar conta.") }
        finally { busy.value = false }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        busy.value = true
        try {
            val user = withContext(Dispatchers.IO) {
                dao.userByEmail(email.trim())?.takeIf { it.enabled && Passwords.verify(password, it.salt, it.passwordHash) }
            }
            if (user == null) notify("E-mail ou senha incorretos, ou conta desativada.")
            else startSession(user)
        } catch (_: Exception) { notify("Não foi possível entrar.") }
        finally { busy.value = false }
    }

    private fun startSession(user: UserEntity) {
        prefs.edit().putString("userId", user.id).apply()
        session.value = user
    }
    fun logout() {
        dismissRestore()
        prefs.edit().remove("userId").apply()
        session.value = null
    }

    fun save(form: CancellationForm, imageUri: Uri?, onSaved: () -> Unit) = viewModelScope.launch {
        val actor = session.value ?: return@launch
        val err = form.validate()
        if (err != null) { notify(err); return@launch }
        val oldItem = form.id?.let { code -> cancellations.value.firstOrNull { it.id == code } }
        if (form.id != null && oldItem == null) { notify("Registro não encontrado."); return@launch }
        if (oldItem != null && actor.role != "ADMIN" && oldItem.createdBy != actor.id) {
            notify("Você não tem permissão para editar este registro."); return@launch
        }
        busy.value = true
        try {
            withContext(Dispatchers.IO) {
                val recordId = oldItem?.id ?: id()
                // O anexo é copiado com limite para evitar crescimento descontrolado do armazenamento.
                var newPhoto: File? = null
                val imagePath = if (imageUri != null) {
                    val directory = File(context.filesDir, "cupons").apply { mkdirs() }
                    val file = File(directory, "$recordId-${System.currentTimeMillis()}.img")
                    try {
                        context.contentResolver.openInputStream(imageUri)?.use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var written = 0L
                                while (true) {
                                    val size = input.read(buffer)
                                    if (size < 0) break
                                    written += size
                                    check(written <= 12L * 1024 * 1024) { "Foto maior que 12 MB. Escolha uma imagem menor." }
                                    output.write(buffer, 0, size)
                                }
                                check(written > 0) { "Foto vazia. Escolha outra imagem." }
                            }
                        } ?: error("Não foi possível abrir a imagem.")
                        newPhoto = file
                        file.absolutePath
                    } catch (ex: Exception) { file.delete(); throw ex }
                } else oldItem?.photoPath ?: form.photoPath
                val store = stores.value.firstOrNull { it.id == form.storeId }
                check(form.storeId == null || store != null) { "A loja selecionada não existe mais. Escolha outra loja." }
                val now = System.currentTimeMillis()
                val e = CancellationEntity(
                    id = recordId, dateIso = parseLocalDate(form.date)!!, time = form.time.trim(),
                    amountCents = parseMoney(form.amount)!!, coupon = form.coupon.trim(),
                    reason = form.reason, reasonDetail = form.reasonDetail.trim(),
                    operatorName = form.operator.trim(), fiscalName = form.fiscal.trim(),
                    managerName = form.manager.trim(), lossPreventionName = form.lossPrevention.trim(),
                    storeId = store?.id,
                    storeName = store?.name.orEmpty(),
                    pdv = form.pdv.trim(), ccf = form.ccf.trim(), notes = form.notes.trim(),
                    photoPath = imagePath, createdBy = oldItem?.createdBy ?: actor.id, updatedBy = actor.id,
                    createdAt = oldItem?.createdAt ?: now, updatedAt = now
                )
                try {
                    db.withTransaction {
                        val duplicates = dao.countDuplicate(e.dateIso, e.coupon, e.storeId, e.pdv, e.id)
                        check(duplicates == 0) { "Já existe esse cupom nessa data, loja e PDV. Confira antes de salvar." }
                        if (oldItem == null) dao.insertCancellation(e) else dao.updateCancellation(e)
                        dao.log(AuditEntity(action = if (oldItem == null) "CRIAR" else "EDITAR", subjectId = e.id, actorId = actor.id,
                            description = "Cupom ${e.coupon} • ${e.storeName}"))
                    }
                } catch (ex: Exception) {
                    newPhoto?.delete()
                    throw ex
                }
                if (newPhoto != null && oldItem?.photoPath != null && oldItem.photoPath != imagePath) {
                    File(oldItem.photoPath).delete()
                }
            }
            notify(if (oldItem == null) "Cancelamento registrado." else "Cancelamento atualizado.")
            onSaved()
        } catch (ex: Exception) { notify("Não foi possível salvar: ${ex.message}") }
        finally { busy.value = false }
    }

    fun delete(entry: CancellationEntity) = viewModelScope.launch {
        val actor = session.value ?: return@launch
        if (actor.role != "ADMIN" && actor.id != entry.createdBy) {
            notify("Você não tem permissão para excluir este registro."); return@launch
        }
        try {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    val count = dao.softDelete(entry.id, actor.id, System.currentTimeMillis())
                    if (count > 0) dao.log(AuditEntity(action = "EXCLUIR", subjectId = entry.id, actorId = actor.id, description = "Cupom ${entry.coupon}"))
                }
            }
            notify("Registro excluído da listagem. Histórico preservado.")
        } catch (_: Exception) { notify("Falha ao excluir. Tente novamente.") }
    }

    fun restoreStoreCatalog() = viewModelScope.launch {
        if (session.value?.role != "ADMIN") {
            notify("Somente o administrador pode gerenciar o catálogo de lojas.")
            return@launch
        }
        busy.value = true
        try {
            val number = withContext(Dispatchers.IO) {
                db.withTransaction {
                    val missing = StoreCatalog.missing(dao.snapshotStores())
                    missing.forEach { dao.insertStore(StoreEntity(name = it.displayName)) }
                    missing.size
                }
            }
            notify(if (number == 0) "As 15 unidades já constam no catálogo ou há registros com os mesmos códigos."
                   else "$number unidade(s) adicionada(s). As lojas existentes foram preservadas.")
        } catch (_: Exception) {
            notify("Não foi possível atualizar o catálogo de lojas.")
        } finally { busy.value = false }
    }

    fun addStore(name: String) = viewModelScope.launch {
        val actor = session.value ?: return@launch
        if (actor.role != "ADMIN") return@launch
        if (name.trim().length < 2) { notify("Informe o nome da loja."); return@launch }
        if (stores.value.any { it.name.equals(name.trim(), true) }) { notify("Essa loja já está cadastrada."); return@launch }
        withContext(Dispatchers.IO) { dao.insertStore(StoreEntity(name = name.trim())) }
        notify("Loja cadastrada.")
    }
    fun toggleStore(store: StoreEntity) = viewModelScope.launch {
        if (session.value?.role != "ADMIN") return@launch
        withContext(Dispatchers.IO) { dao.setStoreActive(store.id, !store.active) }
    }
    fun toggleUser(user: UserEntity) = viewModelScope.launch {
        if (session.value?.role != "ADMIN" || session.value?.id == user.id) return@launch
        withContext(Dispatchers.IO) { dao.setUserEnabled(user.id, !user.enabled) }
    }

    fun createInvite(onCreated: (String) -> Unit) = viewModelScope.launch {
        val actor = session.value ?: return@launch
        if (actor.role != "ADMIN") return@launch
        val random = ByteArray(18).also { SecureRandom().nextBytes(it) }
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(random)
        withContext(Dispatchers.IO) {
            dao.addInvite(InviteEntity(code = code, createdBy = actor.id,
                expiresAt = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
        }
        onCreated(code)
    }

    fun registerWithInvite(name: String, email: String, password: String, code: String) = viewModelScope.launch {
        val err = credentialError(name, email, password)
        if (err != null) { notify(err); return@launch }
        busy.value = true
        try {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    val invitation = dao.validInvite(code.trim(), System.currentTimeMillis())
                        ?: error("Convite inexistente, usado ou expirado.")
                    check(dao.userByEmail(email.trim()) == null) { "E-mail já cadastrado." }
                    val salt = Passwords.salt()
                    dao.insertUser(UserEntity(name = name.trim(), email = email.trim().lowercase(), salt = salt,
                        passwordHash = Passwords.hash(password, salt), role = invitation.role))
                    check(dao.consumeInvite(invitation.id, System.currentTimeMillis()) == 1)
                }
            }
            notify("Conta criada. Entre com seu e-mail e senha.")
        } catch (ex: Exception) { notify(ex.message ?: "Convite inválido.") }
        finally { busy.value = false }
    }

    fun exportEncryptedBackup(password: String, onReady: (ByteArray) -> Unit) = viewModelScope.launch {
        if (session.value?.role != "ADMIN") { notify("Exportação restrita ao administrador."); return@launch }
        if (password.length < 10) { notify("Use uma senha de backup com no mínimo 10 caracteres."); return@launch }
        busy.value = true
        try {
            val bytes = withContext(Dispatchers.IO) {
                val json = PortableBackup.encode(dao.snapshotStores(), dao.snapshotCancellations(), branding.value)
                val pw = password.toCharArray()
                try { EncryptedArchive.encrypt(json, pw) } finally { pw.fill('\u0000') }
            }
            onReady(bytes)
        } catch (_: Exception) { notify("Não foi possível preparar o backup.") }
        finally { busy.value = false }
    }

    /** Backup novo com fotos, em arquivo cifrado, sem carregar imagens em memória. */
    fun exportCompleteBackup(uri: Uri, password: String) = viewModelScope.launch {
        if (session.value?.role != "ADMIN") { notify("Acesso restrito ao administrador."); return@launch }
        if (password.length < 10) { notify("Informe uma senha de pelo menos 10 caracteres."); return@launch }
        busy.value = true
        try {
            val photoCount = withContext(Dispatchers.IO) {
                val chars = password.toCharArray()
                try {
                    CompleteBackup.export(context, uri, chars, dao.snapshotStores(),
                        dao.snapshotCancellations(), branding.value)
                } finally { chars.fill('\u0000') }
            }
            notify("Backup completo criado com $photoCount foto(s). Guarde o arquivo e a senha em local seguro.")
        } catch (e: Exception) {
            notify("Falha ao exportar backup completo: ${e.message ?: "verifique os anexos e espaço livre"}")
        } finally { busy.value = false }
    }

    /** Lê autenticação AES-GCM e mostra contagem ANTES de importar. */
    fun prepareCompleteRestore(uri: Uri, password: String) = viewModelScope.launch {
        if (session.value?.role != "ADMIN") { notify("Acesso restrito ao administrador."); return@launch }
        if (password.length < 10) { notify("Informe a senha do arquivo."); return@launch }
        busy.value = true
        dismissRestore()
        try {
            val user = session.value?.id ?: return@launch
            val prepared = withContext(Dispatchers.IO) {
                val chars = password.toCharArray()
                try { CompleteBackup.prepare(context, uri, chars, user) }
                finally { chars.fill('\u0000') }
            }
            pendingCompleteRestore = prepared
            pendingRestore = prepared.snapshot
            restorePreview.value = RestorePreview(
                prepared.snapshot.stores.size, prepared.snapshot.records.size,
                prepared.images.size, isComplete = true
            )
        } catch (e: Exception) {
            dismissRestore()
            notify("Backup não pôde ser autenticado ou lido: ${e.message ?: "arquivo inválido"}")
        } finally { busy.value = false }
    }

    /** Etapa 1: validar e pré-visualizar, sem gravar nenhuma informação. */
    fun prepareRestore(archive: ByteArray, password: String) = viewModelScope.launch {
        if (session.value?.role != "ADMIN") { notify("Acesso restrito ao administrador."); return@launch }
        busy.value = true
        dismissRestore()
        try {
            val actorId = session.value?.id ?: return@launch
            val snapshot = withContext(Dispatchers.IO) {
                val pw = password.toCharArray()
                try {
                    PortableBackup.decode(EncryptedArchive.decrypt(archive, pw), actorId)
                } finally { pw.fill('\u0000') }
            }
            // Disponível apenas até confirmar ou cancelar. Não armazenar senhas nem arquivo cifrado no estado de UI.
            pendingRestore = snapshot
            restorePreview.value = RestorePreview(snapshot.stores.size, snapshot.records.size)
        } catch (_: Exception) {
            dismissRestore()
            notify("Backup inválido ou senha incorreta. Nenhum dado foi modificado.")
        } finally { busy.value = false }
    }

    fun dismissRestore() {
        pendingCompleteRestore?.clean()
        pendingCompleteRestore = null
        pendingRestore = null
        restorePreview.value = null
    }

    /** Importação só após confirmação; fotos e banco seguem uma transação recuperável. */
    fun confirmRestore() = viewModelScope.launch {
        val actor = session.value ?: return@launch
        if (actor.role != "ADMIN") { dismissRestore(); return@launch }
        val snapshot = pendingRestore ?: return@launch
        val complete = pendingCompleteRestore
        val newPhotoFiles = mutableListOf<File>()
        var databaseCommitted = false
        busy.value = true
        try {
            val (count, copiedPhotos) = withContext(Dispatchers.IO) {
                db.withTransaction {
                    val localStores = dao.snapshotStores().toMutableList()
                    val storeMap = mutableMapOf<String, String>()
                    snapshot.stores.forEach { incoming ->
                        val matching = localStores.filter { StoreCatalog.sameStore(it, incoming) }.singleOrNull()
                        if (matching != null) storeMap[incoming.id] = matching.id
                        else {
                            dao.importStore(incoming)
                            localStores.add(incoming)
                            storeMap[incoming.id] = incoming.id
                        }
                    }
                    var imported = 0
                    var photoCount = 0
                    snapshot.records.forEach { original ->
                        val localId = original.storeId?.let { storeMap[it] ?: it }
                        val localName = localStores.firstOrNull { it.id == localId }?.name
                        var row = original.copy(storeId = localId,
                            storeName = localName ?: original.storeName)
                        val originalPhoto = complete?.images?.get(row.id)
                        val existing = dao.cancellationById(row.id)
                        if (existing != null) {
                            val previousFile = existing.photoPath?.let { File(it) }
                            if (originalPhoto != null && (previousFile == null || !previousFile.isFile)) {
                                val targetDir = File(context.filesDir, "cupons").apply { mkdirs() }
                                val target = File(targetDir, "${row.id}-restored-${System.nanoTime()}.img")
                                originalPhoto.copyTo(target, overwrite = false)
                                newPhotoFiles.add(target)
                                dao.updateCancellation(existing.copy(
                                    photoPath = target.absolutePath,
                                    updatedAt = System.currentTimeMillis(), updatedBy = actor.id
                                ))
                                photoCount++
                            }
                        } else if (dao.countDuplicate(row.dateIso, row.coupon, row.storeId, row.pdv, row.id) == 0) {
                            val imageSource = complete?.images?.get(row.id)
                            var photoCopy: File? = null
                            if (imageSource != null) {
                                val targetDir = File(context.filesDir, "cupons").apply { mkdirs() }
                                photoCopy = File(targetDir, "${row.id}-restored-${System.nanoTime()}.img")
                                imageSource.copyTo(photoCopy, overwrite = false)
                                newPhotoFiles.add(photoCopy)
                                row = row.copy(photoPath = photoCopy.absolutePath)
                            }
                            if (dao.importCancellation(row) != -1L) {
                                imported++
                                if (photoCopy != null) photoCount++
                            } else {
                                photoCopy?.delete()
                                if (photoCopy != null) newPhotoFiles.remove(photoCopy)
                            }
                        }
                    }
                    dao.log(AuditEntity(action = "IMPORTAR", subjectId = "BACKUP",
                        actorId = actor.id,
                        description = "Importados $imported de ${snapshot.records.size} registros; $photoCount fotos"))
                    imported to photoCount
                }
            }
            databaseCommitted = true
            val brandingSaved = runCatching { BrandingStore.save(context, snapshot.brand) }.isSuccess
            if (brandingSaved) branding.value = snapshot.brand
            notify("Restauração concluída: $count registro(s) e $copiedPhotos foto(s). " +
                (if (brandingSaved) "Dados anteriores preservados."
                 else "Dados preservados; não foi possível atualizar a identidade visual."))
        } catch (e: Exception) {
            if (!databaseCommitted) withContext(Dispatchers.IO) { newPhotoFiles.forEach { it.delete() } }
            notify("Restauração não concluída: ${e.message ?: "confira espaço e dados"}. O banco não foi importado.")
        } finally { dismissRestore(); busy.value = false }
    }

    private fun credentialError(name: String, email: String, password: String): String? = when {
        name.trim().length < 2 -> "Informe seu nome."
        !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) -> "Informe um e-mail válido."
        password.length < 8 -> "Use uma senha com pelo menos 8 caracteres."
        else -> null
    }
}
