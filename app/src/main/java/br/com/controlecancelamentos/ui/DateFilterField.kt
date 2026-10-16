package br.com.controlecancelamentos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset

/** Campo de período Android nativo: digite 10082026 ou selecione pelo calendário. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var calendarOpen by remember { mutableStateOf(false) }
    val date = remember(value) { DateFilterUtils.parse(value) }
    val invalid = value.isNotBlank() && value.length == 10 && date == null
    OutlinedTextField(
        value = value,
        onValueChange = { entered ->
            // Pressionar apagar em cima da barra deve funcionar sem aprisionar o cursor.
            onValueChange(DateFilterUtils.formatEdit(value, entered))
        },
        modifier = modifier,
        singleLine = true,
        isError = invalid,
        label = { Text(label) },
        placeholder = { Text("dd/mm/aaaa") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            IconButton(onClick = { calendarOpen = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Abrir calendário para $label")
            }
        }
    )
    if (calendarOpen) {
        val selected = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selected,
            yearRange = 1900..(LocalDate.now().year + 1)
        )
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val chosen = java.time.Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        onValueChange(DateFilterUtils.show(chosen))
                    }
                    calendarOpen = false
                }) { Text("Selecionar") }
            },
            dismissButton = {
                TextButton(onClick = { calendarOpen = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = true)
        }
    }
}
