package com.ymcchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymcchat.app.data.auth.AuthUser
import com.ymcchat.app.data.auth.TokenStore
import com.ymcchat.app.repository.AuthRepository
import kotlinx.coroutines.launch

private const val THEME_PREFS = "ymcchat_preferences"
private const val THEME_KEY = "selected_theme"

private enum class YmcTheme(val label: String, val description: String) {
    YMC("Ymc", "Rouge et noir — identité YmcCreations"),
    GRAY("Gris", "Une apparence neutre et équilibrée"),
    DARK("Sombre", "Graphite et contrastes doux"),
    BLACK("Noir", "Noir profond pour les écrans OLED"),
    LIGHT("Clair", "Fond lumineux et texte sombre")
}

private data class AppPalette(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val isLight: Boolean
)

private fun paletteFor(theme: YmcTheme): AppPalette = when (theme) {
    YmcTheme.YMC -> AppPalette(
        Color(0xFF101014), Color(0xFF19191F), Color(0xFF24242C),
        Color(0xFFE53945), Color(0xFFF5F5F7), Color(0xFF9999A5),
        Color(0xFF34343D), false
    )
    YmcTheme.GRAY -> AppPalette(
        Color(0xFF202124), Color(0xFF292B30), Color(0xFF35383E),
        Color(0xFFB0B6C1), Color(0xFFF1F2F4), Color(0xFFB2B5BC),
        Color(0xFF454951), false
    )
    YmcTheme.DARK -> AppPalette(
        Color(0xFF17191D), Color(0xFF22252B), Color(0xFF2B3038),
        Color(0xFF8B9DB8), Color(0xFFF0F2F5), Color(0xFFA8AFBA),
        Color(0xFF3A404A), false
    )
    YmcTheme.BLACK -> AppPalette(
        Color(0xFF000000), Color(0xFF090909), Color(0xFF151515),
        Color(0xFFE53945), Color(0xFFF5F5F5), Color(0xFF969696),
        Color(0xFF292929), false
    )
    YmcTheme.LIGHT -> AppPalette(
        Color(0xFFF4F5F7), Color(0xFFFFFFFF), Color(0xFFE9EBEF),
        Color(0xFFD52F3B), Color(0xFF17191D), Color(0xFF626873),
        Color(0xFFD8DCE2), true
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YmcChatApp() }
    }
}

@Composable
fun YmcChatApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { AuthRepository() }
    var user by remember { mutableStateOf<AuthUser?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedTheme by remember {
        mutableStateOf(
            runCatching {
                val saved = context.getSharedPreferences(THEME_PREFS, 0)
                    .getString(THEME_KEY, YmcTheme.YMC.name)
                YmcTheme.valueOf(saved ?: YmcTheme.YMC.name)
            }.getOrDefault(YmcTheme.YMC)
        )
    }
    val palette = remember(selectedTheme) { paletteFor(selectedTheme) }

    LaunchedEffect(selectedTheme) {
        context.getSharedPreferences(THEME_PREFS, 0).edit()
            .putString(THEME_KEY, selectedTheme.name)
            .apply()
    }

    LaunchedEffect(Unit) {
        val token = TokenStore.get(context)
        if (token != null) {
            repository.me(token).onSuccess { user = it }
                .onFailure { TokenStore.clear(context) }
        }
        loading = false
    }

    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            secondary = palette.accent
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            secondary = palette.accent
        )
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.background
        ) {
            when {
                loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = palette.accent)
                }
                user == null -> AuthScreen(
                    repository = repository,
                    palette = palette,
                    onAuthenticated = {
                        TokenStore.save(context, it.token)
                        user = it.user
                    }
                )
                else -> HomeScreen(
                    user = user!!,
                    palette = palette,
                    selectedTheme = selectedTheme,
                    onThemeChange = { selectedTheme = it },
                    onLogout = {
                        TokenStore.clear(context)
                        user = null
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    repository: AuthRepository,
    palette: AppPalette,
    onAuthenticated: (com.ymcchat.app.data.auth.AuthResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center
    ) {
        val contentWidth = if (maxWidth > 600.dp) 460.dp else maxWidth
        Column(
            modifier = Modifier.widthIn(max = contentWidth).fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(68.dp).background(palette.accent, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Y", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(14.dp))
            Text("YmcChat", color = palette.accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Connecte-toi à ta communauté", color = palette.muted, fontSize = 14.sp)
            Spacer(Modifier.height(30.dp))
            Text(
                if (isRegister) "Créer un compte" else "Bienvenue",
                color = palette.text, fontSize = 23.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))
            if (isRegister) {
                AuthField(username, { username = it }, "Nom d'utilisateur", palette)
                Spacer(Modifier.height(12.dp))
                AuthField(email, { email = it }, "Adresse email", palette)
            } else {
                AuthField(identifier, { identifier = it }, "Email ou nom d'utilisateur", palette)
            }
            Spacer(Modifier.height(12.dp))
            AuthField(password, { password = it }, "Mot de passe", palette, true)
            Spacer(Modifier.height(16.dp))
            if (error.isNotBlank()) {
                Text(error, color = palette.accent)
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = {
                    if (!loading) {
                        loading = true
                        error = ""
                        scope.launch {
                            val result = if (isRegister) {
                                repository.register(username.trim(), email.trim(), password)
                            } else {
                                repository.login(identifier.trim(), password)
                            }
                            result.onSuccess(onAuthenticated)
                                .onFailure { error = it.message ?: "Une erreur est survenue." }
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (loading) CircularProgressIndicator(
                    Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp
                ) else Text(
                    if (isRegister) "Créer mon compte" else "Se connecter",
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = { isRegister = !isRegister; error = "" }) {
                Text(
                    if (isRegister) "J'ai déjà un compte" else "Créer un compte",
                    color = palette.muted
                )
            }
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    palette: AppPalette,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.border,
            focusedLabelColor = palette.accent,
            unfocusedLabelColor = palette.muted,
            cursorColor = palette.accent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    user: AuthUser,
    palette: AppPalette,
    selectedTheme: YmcTheme,
    onThemeChange: (YmcTheme) -> Unit,
    onLogout: () -> Unit
) {
    var selectedPage by remember { mutableStateOf("Messages") }
    val pages = listOf("Messages", "Amis", "Communautés", "Paramètres")
    val icons = listOf(Icons.Default.Chat, Icons.Default.People, Icons.Default.Groups, Icons.Default.Settings)

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val expanded = maxWidth >= 700.dp

        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = palette.surface,
                    contentColor = palette.text,
                    header = {
                        Box(
                            Modifier.padding(vertical = 18.dp).size(44.dp)
                                .background(palette.accent, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Y", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
                        }
                    }
                ) {
                    pages.forEachIndexed { index, page ->
                        NavigationRailItem(
                            selected = selectedPage == page,
                            onClick = { selectedPage = page },
                            icon = { Icon(icons[index], contentDescription = page) },
                            label = { Text(page) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = palette.accent,
                                selectedTextColor = palette.accent,
                                indicatorColor = palette.accent.copy(alpha = 0.15f),
                                unselectedIconColor = palette.muted,
                                unselectedTextColor = palette.muted
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(
                        selected = false,
                        onClick = onLogout,
                        icon = { Icon(Icons.Default.Logout, contentDescription = "Déconnexion") },
                        label = { Text("Quitter") },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            unselectedIconColor = palette.accent,
                            unselectedTextColor = palette.accent
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    HomeTopBar(selectedPage, user, palette, onLogout)
                    HorizontalDivider(color = palette.border)
                    PageContent(selectedPage, user, palette, selectedTheme, onThemeChange)
                }
            }
        } else {
            Scaffold(
                containerColor = palette.background,
                topBar = { HomeTopBar(selectedPage, user, palette, onLogout) },
                bottomBar = {
                    NavigationBar(
                        containerColor = palette.surface,
                        contentColor = palette.text
                    ) {
                        pages.forEachIndexed { index, page ->
                            NavigationBarItem(
                                selected = selectedPage == page,
                                onClick = { selectedPage = page },
                                icon = { Icon(icons[index], contentDescription = page) },
                                label = { Text(page, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = palette.accent,
                                    selectedTextColor = palette.accent,
                                    indicatorColor = palette.accent.copy(alpha = 0.15f),
                                    unselectedIconColor = palette.muted,
                                    unselectedTextColor = palette.muted
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    PageContent(selectedPage, user, palette, selectedTheme, onThemeChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    selectedPage: String,
    user: AuthUser,
    palette: AppPalette,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(selectedPage, color = palette.text, fontWeight = FontWeight.Bold)
                Text("Connecté en tant que " + user.username, color = palette.muted, fontSize = 11.sp)
            }
        },
        actions = {
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.Logout, contentDescription = "Déconnexion", tint = palette.accent)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
    )
}

@Composable
private fun PageContent(
    page: String,
    user: AuthUser,
    palette: AppPalette,
    selectedTheme: YmcTheme,
    onThemeChange: (YmcTheme) -> Unit
) {
    when (page) {
        "Messages" -> MessagesScreen(palette)
        "Amis" -> PlaceholderScreen(
            title = "Tes amis",
            message = "Ta liste d'amis apparaîtra ici.",
            icon = Icons.Default.People,
            palette = palette
        )
        "Communautés" -> PlaceholderScreen(
            title = "Communautés",
            message = "Les communautés arriveront dans une prochaine mise à jour.",
            icon = Icons.Default.Groups,
            palette = palette
        )
        "Paramètres" -> SettingsScreen(user, palette, selectedTheme, onThemeChange)
    }
}

@Composable
private fun MessagesScreen(palette: AppPalette) {
    val conversations = listOf(
        "Bienvenue sur YmcChat" to "Ton espace de discussion est prêt.",
        "Exemple de conversation" to "Les conversations réelles arrivent bientôt."
    )
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Tes discussions", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Retrouve ici tes conversations.", color = palette.muted, fontSize = 14.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(conversations) { conversation ->
                Row(
                    Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(16.dp))
                        .clickable { }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(48.dp).background(palette.accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(conversation.first, color = palette.text, fontWeight = FontWeight.SemiBold)
                        Text(conversation.second, color = palette.muted, fontSize = 13.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.muted)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    user: AuthUser,
    palette: AppPalette,
    selectedTheme: YmcTheme,
    onThemeChange: (YmcTheme) -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Personnalisation", color = palette.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Choisis l'apparence de YmcChat.", color = palette.muted, fontSize = 14.sp)
        }
        YmcTheme.values().forEach { theme ->
            val preview = paletteFor(theme)
            Row(
                Modifier.fillMaxWidth().background(
                    if (selectedTheme == theme) palette.accent.copy(alpha = 0.10f) else palette.surface,
                    RoundedCornerShape(14.dp)
                ).clickable { onThemeChange(theme) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.width(54.dp).height(38.dp).background(preview.background, RoundedCornerShape(9.dp))
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(preview.surface, RoundedCornerShape(4.dp)))
                    Box(Modifier.width(9.dp).fillMaxHeight().background(preview.accent, RoundedCornerShape(4.dp)))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(theme.label, color = palette.text, fontWeight = FontWeight.SemiBold)
                    Text(theme.description, color = palette.muted, fontSize = 12.sp)
                }
                RadioButton(
                    selected = selectedTheme == theme,
                    onClick = { onThemeChange(theme) },
                    colors = RadioButtonDefaults.colors(selectedColor = palette.accent)
                )
            }
        }
        HorizontalDivider(color = palette.border)
        Text("Compte", color = palette.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Column(
            Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(14.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(user.username, color = palette.text, fontWeight = FontWeight.SemiBold)
            Text(user.email, color = palette.muted, fontSize = 13.sp)
        }
        Text(
            "Ton thème est enregistré automatiquement sur cet appareil.",
            color = palette.muted, fontSize = 12.sp
        )
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: AppPalette
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(48.dp))
            Text(title, color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = palette.muted, fontSize = 14.sp)
        }
    }
}
