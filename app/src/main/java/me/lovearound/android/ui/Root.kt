package me.lovearound.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import me.lovearound.android.AppModel
import me.lovearound.android.Palette
import me.lovearound.android.tr

@Composable
fun Root(model: AppModel, askNotifications: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.paper)) {
        if (model.signedIn) MainTabs(model, askNotifications) else SignInScreen(model)
        model.outgoing?.let { out -> Box(Modifier.zIndex(3f)) { SendGlow(out) } }
        // One at a time: what the app is for, then why it asks about notifications.
        if (model.howItWorks) {
            Box(Modifier.zIndex(4f)) { HowItWorks(tr("Начать")) { model.closeHowItWorks() } }
        } else if (model.notifyIntro) {
            Box(Modifier.zIndex(4f)) {
                NotifyIntro(allow = { model.closeNotifyIntro(); askNotifications() }, later = { model.closeNotifyIntro() })
            }
        }
        AnimatedVisibility(model.incoming, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.zIndex(5f)) {
            SignalOverlay(Palette.color(model.myColor), model.signalSeed) { model.dismissSignal() }
        }
    }
}

@Composable
private fun MainTabs(model: AppModel, askNotifications: () -> Unit) {
    // A page over the tabs: its own bar, back goes to the tabs.
    model.adding?.let { AddContactScreen(model, it); return }
    model.editing?.let { ContactEditor(model, it); return }
    model.pairing?.let { PairScreen(model, it); return }
    if (model.choosingClose) { ChooseCloseScreen(model); return }

    Scaffold(
        containerColor = C.paper,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = C.field) {
                for ((tab, icon, title) in listOf(
                    Triple(AppModel.Tab.Hearts, Icons.Filled.Favorite, tr("Сердечки")),
                    Triple(AppModel.Tab.Book, Icons.AutoMirrored.Filled.MenuBook, tr("Книга")),
                    Triple(AppModel.Tab.Toys, Icons.Filled.Lightbulb, tr("Игрушки")),
                    Triple(AppModel.Tab.Settings, Icons.Filled.Tune, tr("Настройки")),
                )) {
                    NavigationBarItem(model.tab == tab, { model.tab = tab }, icon = { Icon(icon, null) },
                        label = { Text(title, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = C.link, selectedTextColor = C.link,
                            indicatorColor = C.heartSoft, unselectedIconColor = C.muted, unselectedTextColor = C.muted))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            if (model.tab != AppModel.Tab.Hearts) androidx.activity.compose.BackHandler { model.tab = AppModel.Tab.Hearts }
            when (model.tab) {
                AppModel.Tab.Hearts -> HomeScreen(model)
                AppModel.Tab.Book -> BookScreen(model)
                AppModel.Tab.Toys -> ToysScreen(model)
                AppModel.Tab.Settings -> SettingsScreen(model, askNotifications)
            }
            AnimatedVisibility(model.toast != null, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp, start = 24.dp, end = 24.dp)) {
                model.toast?.let { toast ->
                    Text(toast.text, color = if (toast.failed) C.danger else C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .shadow(14.dp, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(C.paper)
                            .border(1.dp, C.ink.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 18.dp, vertical = 12.dp))
                }
            }
        }
    }
}
