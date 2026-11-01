package com.example.soruhavuzu.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Kayıtta seçilebilen hedef sınavlar; id'ler backend kataloğundaki (V2__katalog.sql) ile aynı.
 * AYT'nin henüz konusu olmadığı için listede yok; konuları eklenince buraya da eklenmeli.
 */
private val TARGET_EXAMS = listOf("exam_kpss" to "KPSS", "exam_tyt" to "TYT")

@Composable
fun AuthScreen(
    form: AuthFormState,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, nickname: String, targetExamId: String, birthYear: Int?) -> Unit,
    onFieldChanged: () -> Unit
) {
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var targetExam by rememberSaveable { mutableStateOf(TARGET_EXAMS.first().first) }
    var birthYear by rememberSaveable { mutableStateOf("") }

    fun edit(change: () -> Unit) {
        change()
        if (form.error != null) onFieldChanged()
    }

    val canSubmit = !form.isSubmitting && email.isNotBlank() && password.length >= 8 &&
        (!isRegister || nickname.trim().length >= 3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("SoruHavuzu", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "İstediğin zorlukta soru çöz; sorular topluluğun çözümleriyle kendi zorluğunu bulur.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isRegister,
                    onClick = { edit { isRegister = false } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("auth_tab_login")
                ) { Text("Giriş Yap") }
                SegmentedButton(
                    selected = isRegister,
                    onClick = { edit { isRegister = true } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("auth_tab_register")
                ) { Text("Kayıt Ol") }
            }

            if (isRegister) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { edit { nickname = it } },
                    label = { Text("Takma ad") },
                    supportingText = { Text("3-24 karakter; harf, rakam, _ ve .") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_nickname")
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { edit { email = it } },
                label = { Text("E-posta") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email")
            )
            OutlinedTextField(
                value = password,
                onValueChange = { edit { password = it } },
                label = { Text("Şifre") },
                supportingText = if (isRegister) ({ Text("En az 8 karakter") }) else null,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password")
            )

            if (isRegister) {
                Text("Hedef sınav", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TARGET_EXAMS.forEach { (id, label) ->
                        FilterChip(
                            selected = targetExam == id,
                            onClick = { edit { targetExam = id } },
                            label = { Text(label) },
                            modifier = Modifier.testTag("auth_exam_$id")
                        )
                    }
                }
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { value -> edit { birthYear = value.filter { it.isDigit() }.take(4) } },
                    label = { Text("Doğum yılı (isteğe bağlı)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            form.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("auth_error")
                )
            }

            Button(
                onClick = {
                    if (isRegister) onRegister(email, password, nickname, targetExam, birthYear.toIntOrNull())
                    else onLogin(email, password)
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auth_submit")
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isRegister) "Hesap Oluştur" else "Giriş Yap", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
