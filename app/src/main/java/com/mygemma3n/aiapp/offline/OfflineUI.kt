package com.mygemma3n.aiapp.offline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * Offline status banner that appears when the app is offline
 */
@Composable
fun OfflineStatusBanner(
    isOnline: Boolean,
    queuedMessagesCount: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isOnline,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "You're offline",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (queuedMessagesCount > 0) {
                        Text(
                            text = "$queuedMessagesCount messages queued for sync",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // Pulsing offline indicator
                OfflinePulseIndicator()
            }
        }
    }
}

@Composable
private fun OfflinePulseIndicator() {
    var visible by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            visible = !visible
        }
    }
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = if (visible) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
    )
}

/**
 * Offline conversations list
 */
@Composable
fun OfflineConversationsList(
    conversations: List<OfflineManager.OfflineConversation>,
    onConversationClick: (OfflineManager.OfflineConversation) -> Unit,
    modifier: Modifier = Modifier
) {
    if (conversations.isEmpty()) {
        EmptyOfflineState(
            message = "No offline conversations available",
            icon = Icons.Default.ChatBubbleOutline
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversations) { conversation ->
                OfflineConversationCard(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation) }
                )
            }
        }
    }
}

@Composable
private fun OfflineConversationCard(
    conversation: OfflineManager.OfflineConversation,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val lastMessage = conversation.messages.lastOrNull()
                    if (lastMessage != null) {
                        Text(
                            text = lastMessage.content.take(100) + if (lastMessage.content.length > 100) "..." else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatOfflineTimestamp(conversation.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${conversation.messages.size} msgs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = "Available offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Empty state for offline mode
 */
@Composable
fun EmptyOfflineState(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Content will be available when you're back online",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Sync status card showing pending operations
 */
@Composable
fun OfflineSyncStatusCard(
    offlineStatus: OfflineManager.OfflineStatus,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                SyncStatusIndicator(isOnline = offlineStatus.isOnline)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Cached data info
            if (offlineStatus.hasCachedData) {
                OfflineInfoRow(
                    icon = Icons.Default.Storage,
                    label = "Cached conversations",
                    value = offlineStatus.cachedConversationsCount.toString()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Queued messages info
            if (offlineStatus.queuedMessagesCount > 0) {
                OfflineInfoRow(
                    icon = Icons.Default.Schedule,
                    label = "Queued messages",
                    value = offlineStatus.queuedMessagesCount.toString()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Last sync time
            offlineStatus.lastSyncTime?.let { lastSync ->
                OfflineInfoRow(
                    icon = Icons.Default.Sync,
                    label = "Last sync",
                    value = formatOfflineTimestamp(lastSync)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Retry sync button
            if (!offlineStatus.isOnline && offlineStatus.queuedMessagesCount > 0) {
                OutlinedButton(
                    onClick = onRetrySync,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry Sync")
                }
            }
        }
    }
}

@Composable
private fun SyncStatusIndicator(isOnline: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isOnline) Color.Green else Color.Red,
                    shape = RoundedCornerShape(4.dp)
                )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = if (isOnline) "Online" else "Offline",
            style = MaterialTheme.typography.labelMedium,
            color = if (isOnline) Color.Green else Color.Red
        )
    }
}

@Composable
private fun OfflineInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatOfflineTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> {
            val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
            formatter.format(Date(timestamp))
        }
    }
}