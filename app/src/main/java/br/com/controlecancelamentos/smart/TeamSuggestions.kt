package br.com.controlecancelamentos.smart

import br.com.controlecancelamentos.data.CancellationEntity
import br.com.controlecancelamentos.data.CancellationForm

/** A sugestão não é confirmação de quem está de turno. */
data class TeamSuggestion(
    val operator: String?,
    val fiscal: String?,
    val manager: String?,
    val lossPrevention: String?,
    val samePdv: Boolean,
    val previousDate: String
) {
    val people: List<String> get() = listOfNotNull(operator, fiscal, manager, lossPrevention).distinct()
}

object TeamSuggestions {
    fun suggest(records: List<CancellationEntity>, storeId: String?, pdv: String): TeamSuggestion? {
        if (storeId.isNullOrBlank()) return null
        val recent = records.filter { it.deletedAt == null && it.storeId == storeId }
            .sortedWith(compareByDescending<CancellationEntity> { it.dateIso }
                .thenByDescending { it.time }.thenByDescending { it.updatedAt })
        val key = pdv.trim().trimStart('0').ifBlank { "0" }
        val matching = if (pdv.isNotBlank()) recent.firstOrNull {
            it.pdv.isNotBlank() && it.pdv.trim().trimStart('0').ifBlank { "0" } == key
        } else null
        val source = matching ?: recent.firstOrNull() ?: return null
        val result = TeamSuggestion(
            operator = matching?.operatorName?.takeIf { it.isNotBlank() },
            fiscal = source.fiscalName.takeIf { it.isNotBlank() },
            manager = source.managerName.takeIf { it.isNotBlank() },
            lossPrevention = source.lossPreventionName.takeIf { it.isNotBlank() },
            samePdv = matching != null,
            previousDate = source.dateIso
        )
        return result.takeIf { it.people.isNotEmpty() }
    }

    fun applyMissing(form: CancellationForm, team: TeamSuggestion): CancellationForm = form.copy(
        operator = form.operator.ifBlank { team.operator.orEmpty() },
        fiscal = form.fiscal.ifBlank { team.fiscal.orEmpty() },
        manager = form.manager.ifBlank { team.manager.orEmpty() },
        lossPrevention = form.lossPrevention.ifBlank { team.lossPrevention.orEmpty() }
    )
}