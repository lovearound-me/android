package me.lovearound.android.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import me.lovearound.android.AppModel
import me.lovearound.android.Config
import me.lovearound.android.R
import me.lovearound.android.tr

/** Firebase's web client: Google's ID token is made out to it, and Firebase takes it. */
private const val GOOGLE_SERVER_CLIENT = "989287714644-q2pesremv367tm3epv34qv740li30h46.apps.googleusercontent.com"

/** The app's mark: the warm gradient circle with the white heart. */
@Composable
fun AppMark(size: Dp) {
    Box(Modifier
        .size(size)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFFF07A62), Color(0xFFE86A9A)))),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** Three ways in: Google's own account chooser, Apple through the site, and email with a password. */
@Composable
fun SignInScreen(model: AppModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var withEmail by remember { mutableStateOf(false) }
    if (withEmail) {
        EmailSignIn(model) { withEmail = false }
        return
    }
    Column(Modifier
        .fillMaxSize()
        .background(C.paper)
        .systemBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(72.dp))
        Box(Modifier.shadow(18.dp, CircleShape, ambientColor = C.heart, spotColor = C.heart)) { AppMark(96.dp) }
        Text("Love Around", color = C.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
        Text(tr("Думаешь о ком-то? Нажми, и он почувствует, что его любят и он нужен. А от кого, не узнает."),
            color = C.muted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 23.sp,
            modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp).widthIn(max = 480.dp))
        Spacer(Modifier.height(56.dp))
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WideButton(tr("Войти через Google"), {
                scope.launch {
                    val token = googleIdToken(context as Activity) { model.signInProblem = it } ?: return@launch
                    model.signInWithGoogle(token)
                }
            }, filled = false, enabled = !model.busy, painter = painterResource(R.drawable.ic_google))
            WideButton(tr("Войти через Apple"), {
                model.signInProblem = null
                openPage(context, model.siteSignInUri().toString(), Color.Black)
            }, filled = false, enabled = !model.busy, painter = painterResource(R.drawable.ic_apple), iconTint = C.ink)
            WideButton(tr("Войти по почте"), {
                model.signInProblem = null
                withEmail = true
            }, filled = false, enabled = !model.busy, icon = Icons.Filled.Email)
        }
        if (model.busy) CircularProgressIndicator(color = C.link, modifier = Modifier.padding(top = 18.dp).size(28.dp))
        model.signInProblem?.let {
            Text(it, color = C.danger, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(tr("Книга хранится в аккаунте"), color = C.faint, fontSize = 13.sp, modifier = Modifier.padding(top = 28.dp))
        Text(tr("Конфиденциальность"), color = C.link, fontSize = 13.sp,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button) { openPage(context, Config.PRIVACY_PAGE, Color(0xFFFFFBF6)) }
                .padding(12.dp))
    }
}

/** Google's account chooser (Credential Manager); its ID token, or null if closed or failed. */
private suspend fun googleIdToken(activity: Activity, problem: (String) -> Unit): String? {
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(GOOGLE_SERVER_CLIENT).build())
        .build()
    return try {
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            problem(tr("Не получилось войти. Попробуй ещё раз.")); null
        }
    } catch (e: GetCredentialCancellationException) {
        null
    } catch (e: NoCredentialException) {
        problem(tr("На этом телефоне нет аккаунта Google. Добавь его в настройках или войди по почте.")); null
    } catch (e: Exception) {
        problem(tr("Не получилось войти. Попробуй ещё раз.")); null
    }
}

/** «Войти по почте»: as on the site, one button signs in, or makes the account if this email has none. */
@Composable
fun EmailSignIn(model: AppModel, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var noteIsProblem by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = close)
    LaunchedEffect(Unit) { focus.requestFocus() }
    val address = email.trim()

    fun say(text: String?, problem: Boolean = true) { note = text; noteIsProblem = problem }
    fun submit() {
        if (address.isEmpty()) return say(tr("Введи почту"))
        if (!address.contains("@")) return say(tr("Проверь адрес почты"))
        if (password.length < 8) return say(tr("Пароль нужен не короче 8 символов"))
        say(null)
        scope.launch {
            val problem = model.signInWithEmail(address, password)
            if (problem != null) say(problem)
        }
    }

    Column(Modifier.fillMaxSize().background(C.paper).systemBarsPadding().imePadding()) {
        PageBar(tr("Почта и пароль"), close)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.link, unfocusedBorderColor = C.faint.copy(alpha = 0.4f), focusedLabelColor = C.link,
                cursorColor = C.link, focusedContainerColor = C.field, unfocusedContainerColor = C.field)
            OutlinedTextField(email, { email = it }, label = { Text(tr("Почта")) }, singleLine = true, colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus))
            OutlinedTextField(password, { password = it }, label = { Text(tr("Пароль")) }, singleLine = true, colors = colors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                supportingText = { Text(tr("Не меньше 8 символов"), color = C.muted) },
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus))
            WideButton(if (model.busy) tr("Входим…") else tr("Войти или создать аккаунт"), { submit() }, enabled = !model.busy)
            WideButton(tr("Забыли пароль?"), {
                if (address.isEmpty()) say(tr("Сначала введи почту")) else scope.launch {
                    val text = model.sendPasswordReset(address)
                    say(text, problem = text != tr("Письмо для нового пароля отправлено"))
                }
            }, filled = false, enabled = !model.busy)
            note?.let { Text(it, color = if (noteIsProblem) C.danger else C.muted, fontSize = 14.sp) }
        }
    }
}
