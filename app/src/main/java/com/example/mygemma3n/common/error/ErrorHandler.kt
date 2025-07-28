package com.example.mygemma3n.common.error

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Centralized error handling system
 */
@Singleton
class ErrorHandler @Inject constructor() {
    
    sealed class AppError(
        open val message: String,
        open val cause: Throwable? = null,
        open val userMessage: String = message,
        open val isRetryable: Boolean = true
    ) {
        data class NetworkError(
            override val message: String = "Network connection failed",
            override val cause: Throwable? = null,
            override val userMessage: String = "Please check your internet connection and try again"
        ) : AppError(message, cause, userMessage, isRetryable = true)
        
        data class ServerError(
            override val message: String = "Server error occurred",
            override val cause: Throwable? = null,
            override val userMessage: String = "Something went wrong on our end. Please try again later"
        ) : AppError(message, cause, userMessage, isRetryable = true)
        
        data class ValidationError(
            override val message: String,
            override val userMessage: String = message
        ) : AppError(message, userMessage = userMessage, isRetryable = false)
        
        data class DatabaseError(
            override val message: String = "Database operation failed",
            override val cause: Throwable? = null,
            override val userMessage: String = "Unable to save data. Please try again"
        ) : AppError(message, cause, userMessage, isRetryable = true)
        
        data class AIModelError(
            override val message: String = "AI model error",
            override val cause: Throwable? = null,
            override val userMessage: String = "The AI tutor is temporarily unavailable. Please try again in a moment"
        ) : AppError(message, cause, userMessage, isRetryable = true)
        
        data class FileError(
            override val message: String = "File operation failed",
            override val cause: Throwable? = null,
            override val userMessage: String = "Unable to access file. Please check permissions and try again"
        ) : AppError(message, cause, userMessage, isRetryable = true)
        
        data class AuthenticationError(
            override val message: String = "Authentication failed",
            override val cause: Throwable? = null,
            override val userMessage: String = "Please sign in again to continue"
        ) : AppError(message, cause, userMessage, isRetryable = false)
        
        data class PermissionError(
            override val message: String = "Permission denied",
            override val userMessage: String = "This feature requires additional permissions. Please grant them in settings"
        ) : AppError(message, userMessage = userMessage, isRetryable = false)
        
        data class UnknownError(
            override val message: String = "An unexpected error occurred",
            override val cause: Throwable? = null,
            override val userMessage: String = "Something unexpected happened. Please try again"
        ) : AppError(message, cause, userMessage, isRetryable = true)
    }
    
    /**
     * Convert exceptions to structured app errors
     */
    fun handleException(throwable: Throwable): AppError {
        return when (throwable) {
            is CancellationException -> {
                // Don't log cancellation exceptions as errors
                AppError.UnknownError("Operation cancelled", throwable, "Operation was cancelled")
            }
            
            is UnknownHostException, 
            is java.net.ConnectException -> {
                Timber.w(throwable, "Network connectivity issue")
                AppError.NetworkError(cause = throwable)
            }
            
            is TimeoutException -> {
                Timber.w(throwable, "Operation timed out")
                AppError.NetworkError(
                    message = "Request timed out",
                    cause = throwable,
                    userMessage = "The request took too long. Please try again"
                )
            }
            
            is SecurityException -> {
                Timber.e(throwable, "Security/Permission error")
                AppError.PermissionError(
                    message = throwable.message ?: "Permission denied",
                )
            }
            
            is IllegalArgumentException,
            is IllegalStateException -> {
                Timber.e(throwable, "Validation error")
                AppError.ValidationError(
                    message = throwable.message ?: "Invalid input",
                    userMessage = "Please check your input and try again"
                )
            }
            
            is java.io.IOException -> {
                Timber.e(throwable, "File/IO error")
                AppError.FileError(cause = throwable)
            }
            
            else -> {
                Timber.e(throwable, "Unhandled exception")
                AppError.UnknownError(
                    message = throwable.message ?: "Unknown error",
                    cause = throwable
                )
            }
        }
    }
    
    /**
     * Log error with appropriate level
     */
    fun logError(error: AppError) {
        when (error) {
            is AppError.NetworkError -> Timber.w(error.cause, "Network error: ${error.message}")
            is AppError.ValidationError -> Timber.i("Validation error: ${error.message}")
            is AppError.PermissionError -> Timber.w("Permission error: ${error.message}")
            else -> Timber.e(error.cause, "App error: ${error.message}")
        }
    }
    
    /**
     * Get user-friendly error message
     */
    fun getUserMessage(error: AppError): String {
        return error.userMessage
    }
    
    /**
     * Get appropriate icon for error type
     */
    fun getErrorIcon(error: AppError): ImageVector {
        return when (error) {
            is AppError.NetworkError -> Icons.Default.CloudOff
            is AppError.ServerError -> Icons.Default.ErrorOutline
            is AppError.ValidationError -> Icons.Default.Warning
            is AppError.DatabaseError -> Icons.Default.Storage
            is AppError.AIModelError -> Icons.Default.SmartToy
            is AppError.FileError -> Icons.Default.FolderOff
            is AppError.AuthenticationError -> Icons.Default.Lock
            is AppError.PermissionError -> Icons.Default.Security
            is AppError.UnknownError -> Icons.Default.Error
        }
    }
}

/**
 * Result wrapper for handling success/failure states
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val error: ErrorHandler.AppError) : AppResult<Nothing>()
    object Loading : AppResult<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    fun isLoading(): Boolean = this is Loading
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    fun errorOrNull(): ErrorHandler.AppError? = when (this) {
        is Error -> error
        else -> null
    }
}

/**
 * Safe execution wrapper that converts exceptions to AppResult
 */
inline fun <T> safeCall(
    errorHandler: ErrorHandler,
    action: () -> T
): AppResult<T> {
    return try {
        AppResult.Success(action())
    } catch (e: Exception) {
        val error = errorHandler.handleException(e)
        errorHandler.logError(error)
        AppResult.Error(error)
    }
}

/**
 * Safe execution wrapper for suspend functions
 */
suspend inline fun <T> safeSuspendCall(
    errorHandler: ErrorHandler,
    crossinline action: suspend () -> T
): AppResult<T> {
    return try {
        AppResult.Success(action())
    } catch (e: Exception) {
        val error = errorHandler.handleException(e)
        errorHandler.logError(error)
        AppResult.Error(error)
    }
}

/**
 * Error display composables
 */
@Composable
fun ErrorCard(
    error: ErrorHandler.AppError,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = ErrorHandler().getErrorIcon(error),
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = error.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            if (onRetry != null && error.isRetryable) {
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onErrorContainer
                        ).brush
                    )
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

@Composable
fun ErrorDialog(
    error: ErrorHandler.AppError,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    title: String = "Error"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = ErrorHandler().getErrorIcon(error),
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            if (onRetry != null && error.isRetryable) {
                TextButton(onClick = onRetry) {
                    Text("Try Again")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun FullScreenError(
    error: ErrorHandler.AppError,
    onRetry: (() -> Unit)? = null,
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
            imageVector = ErrorHandler().getErrorIcon(error),
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = error.userMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (onRetry != null && error.isRetryable) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

/**
 * Loading states
 */
@Composable
fun LoadingCard(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FullScreenLoading(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}