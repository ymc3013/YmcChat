
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

private val Background = Color(0xFF101014)
private val SurfaceDark = Color(0xFF19191F)
private val Red = Color(0xFFE53945)
private val TextWhite = Color(0xFFF5F5F5)
private val TextGray = Color(0xFF9999A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YmcChatApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YmcChatApp() {
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )
    val scope = rememberCoroutineScope()

    var selectedPage by remember {
        mutableStateOf("Messages")
    }

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
                    text = "YmcChat",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = Red,
                    modifier = Modifier.padding(20.dp)
                )

                HorizontalDivider(
                    color = Color.DarkGray
                )

                pages.forEach { page ->
                    NavigationDrawerItem(
                        label = {
                            Text(page)
                        },
                        selected = selectedPage == page,
                        onClick = {
                            selectedPage = page
                            scope.launch {
                                drawerState.close()
                            }
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
                                scope.launch {
                                    drawerState.open()
                                }
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedPage) {
                    "Messages" -> MessagesScreen()
                    "Amis" -> EmptyScreen("Tes amis apparaîtront ici.")
                    "Communautés" -> EmptyScreen("Tes communautés apparaîtront ici.")
                    "Paramètres" -> EmptyScreen("Les paramètres arriveront bientôt.")
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Discussions",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            SurfaceDark,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .background(
                                Red,
                                RoundedCornerShape(50)
                            ),
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
                        text = conversation,
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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = TextGray,
            fontSize = 16.sp
        )
    }
}
