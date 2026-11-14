package com.ymcchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ymcchat.app.data.auth.TokenStore
import com.ymcchat.app.data.auth.AuthUser
import com.ymcchat.app.repository.AuthRepository
import kotlinx.coroutines.launch

private val Background = Color(0xFF101014)
private val SurfaceDark = Color(0xFF19191F)
private val Red = Color(0xFFE53945)
private val TextWhite = Color(0xFFF5F5F5)
private val TextGray = Color(0xFF9999A5)

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
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<AuthUser?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var success by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val token = TokenStore.get(context)

        if (token != null) {
            repository.me(token).onSuccess {
                user = it
            }.onFailure {
                TokenStore.clear(context)
            }
        }

        loading = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Red,
            background = Background,
            surface = SurfaceDark
        )
    ) {
        if (loading) {
            Box(
                Modifier.fillMaxSize().background(Background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Red)
            }
        } else if (user == null) {
            AuthScreen(
                repository = repository,
                onAuthenticated = {
                    TokenStore.save(context, it.token)
                    user = it.user
                    error = ""
                    success = ""
                }
            )
        } else {
            HomeScreen(
                user = user!!,
                onLogout = {
                    TokenStore.clear(context)
                    user = null
                }
            )
        }
    }
}

@Composable
fun AuthScreen(
    repository: AuthRepository,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "YmcChat",
            color = Red,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            if (isRegister) "Créer un compte" else "Bienvenue",
            color = TextWhite,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        if (isRegister) {
            AuthField(
                value = username,
                onValueChange = { username = it },
                label = "Nom d'utilisateur"
            )

            Spacer(Modifier.height(12.dp))

            AuthField(
                value = email,
                onValueChange = { email = it },
                label = "Adresse email"
            )
        } else {
            AuthField(
                value = identifier,
                onValueChange = { identifier = it },
                label = "Email ou nom d'utilisateur"
            )
        }

        Spacer(Modifier.height(12.dp))

        AuthField(
            value = password,
            onValueChange = { password = it },
            label = "Mot de passe",
            password = true
        )

        Spacer(Modifier.height(16.dp))

        if (error.isNotBlank()) {
            Text(error, color = Red)
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                error = ""

                if (loading) return@Button

                loading = true

                scope.launch {
                    val result = if (isRegister) {
                        repository.register(
                            username.trim(),
                            email.trim(),
                            password
                        )
                    } else {
                        repository.login(
                            identifier.trim(),
                            password
                        )
                    }

                    result.onSuccess {
                        onAuthenticated(it)
                    }.onFailure {
                        error = it.message ?: "Erreur inconnue."
                    }

                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Red
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            } else {
                Text(
                    if (isRegister) "Créer mon compte" else "Se connecter"
                )
            }
        }

        TextButton(
            onClick = {
                isRegister = !isRegister
                error = ""
            }
        ) {
            Text(
                if (isRegister)
                    "J'ai déjà un compte"
                else
                    "Créer un compte",
                color = TextGray
            )
        }
    }
}

@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (password)
            PasswordVisualTransformation()
        else
            androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            focusedBorderColor = Red,
            unfocusedBorderColor = TextGray,
            focusedLabelColor = Red,
            unfocusedLabelColor = TextGray
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: AuthUser,
    onLogout: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selectedPage by remember { mutableStateOf("Messages") }

    val pages = listOf(
        "Messages",
        "Amis",
        "Communautés",
        "Paramètres"
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceDark,
                drawerContentColor = TextWhite
            ) {
                Spacer(Modifier.height(28.dp))

                Text(
                    "YmcChat",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = Red,
                    modifier = Modifier.padding(20.dp)
                )

                Text(
                    user.username,
                    color = TextGray,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color.DarkGray
                )

                pages.forEach { page ->
                    NavigationDrawerItem(
                        label = { Text(page) },
                        selected = selectedPage == page,
                        onClick = {
                            selectedPage = page
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                imageVector = when (page) {
                                    "Messages" -> Icons.Default.Chat
                                    "Amis" -> Icons.Default.People
                                    "Communautés" -> Icons.Default.Groups
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = null
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Red.copy(alpha = 0.2f),
                            selectedTextColor = Red,
                            selectedIconColor = Red,
                            unselectedTextColor = TextGray,
                            unselectedIconColor = TextGray
                        ),
                        modifier = Modifier.padding(
                            NavigationDrawerItemDefaults.ItemPadding
                        )
                    )
                }

                Spacer(Modifier.weight(1f))

                NavigationDrawerItem(
                    label = { Text("Déconnexion") },
                    selected = false,
                    onClick = onLogout,
                    icon = {
                        Icon(Icons.Default.Logout, contentDescription = null)
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedTextColor = Red,
                        unselectedIconColor = Red
                    ),
                    modifier = Modifier.padding(
                        NavigationDrawerItemDefaults.ItemPadding
                    )
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = Background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            selectedPage,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = TextWhite
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDark
                    )
                )
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedPage) {
                    "Messages" -> MessagesScreen()
                    "Amis" -> EmptyScreen("Tes amis apparaîtront ici.")
                    "Communautés" -> EmptyScreen(
                        "Tes communautés apparaîtront ici."
                    )
                    "Paramètres" -> EmptyScreen(
                        "Compte : ${user.username}\n${user.email}"
                    )
                }
            }
        }
    }
}

@Composable
fun MessagesScreen() {
    val conversations = listOf(
        "Bienvenue sur YmcChat",
        "Exemple de conversation"
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Discussions",
            color = TextWhite,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(conversations) { conversation ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            SurfaceDark,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(45.dp)
                            .background(Red, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        conversation,
                        color = TextWhite,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyScreen(message: String) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = TextGray,
            fontSize = 16.sp
        )
    }
}
