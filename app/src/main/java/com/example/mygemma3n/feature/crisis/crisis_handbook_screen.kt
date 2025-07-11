package com.example.mygemma3n.feature.crisis

import android.Manifest
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import timber.log.Timber

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CrisisHandbookScreen(
    viewModel: CrisisHandbookViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission states
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val microphonePermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // UI State
    var queryText by remember { mutableStateOf("") }
    var isVoiceInputActive by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<EmergencyContactInfo?>(null) }

    // Location
    var userLocation by remember { mutableStateOf<Location?>(null) }

    // Voice input launcher
    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spokenText ->
            queryText = spokenText
            viewModel.handleEmergencyQuery(spokenText, userLocation)
        }
        isVoiceInputActive = false
    }

    // Get user location on launch
    LaunchedEffect(locationPermission.status) {
        if (locationPermission.status.isGranted) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        userLocation = it
                        viewModel.updateUserLocation(it)
                    }
                }
            } catch (e: SecurityException) {
                Timber.e(e, "Location permission error")
            }
        }
    }

    Scaffold(
        topBar = {
            CrisisTopBar(
                onBackClick = { /* Handle back navigation */ }
            )
        },
        floatingActionButton = {
            if (!showMap) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (microphonePermission.status.isGranted) {
                            startVoiceInput(voiceInputLauncher)
                            isVoiceInputActive = true
                        } else {
                            microphonePermission.launchPermissionRequest()
                        }
                    },
                    icon = {
                        Icon(
                            if (isVoiceInputActive) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice input"
                        )
                    },
                    text = { Text(if (isVoiceInputActive) "Listening..." else "Voice Help") },
                    containerColor = if (isVoiceInputActive)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content or map
            AnimatedContent(
                targetState = showMap,
                label = "content_transition"
            ) { isShowingMap ->
                if (isShowingMap && state.mapState != null) {
                    CrisisMapView(
                        mapState = state.mapState!!,
                        onBackClick = { showMap = false },
                        onFacilitySelected = { facilityId ->
                            viewModel.selectFacility(facilityId)
                        }
                    )
                } else {
                    CrisisMainContent(
                        state = state,
                        queryText = queryText,
                        onQueryChange = { queryText = it },
                        onQuerySubmit = {
                            viewModel.handleEmergencyQuery(queryText, userLocation)
                        },
                        onQuickAction = { action ->
                            action.action()
                        },
                        onContactClick = { contact ->
                            selectedContact = contact
                        },
                        onShowMap = { showMap = true },
                        onRequestLocationPermission = {
                            locationPermission.launchPermissionRequest()
                        },
                        isLocationGranted = locationPermission.status.isGranted,
                        quickActions = viewModel.getQuickActions()
                    )
                }
            }

            // Loading overlay
            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Processing emergency request...")
                        }
                    }
                }
            }
        }
    }

    // Emergency contact dialog
    selectedContact?.let { contact ->
        EmergencyContactDialog(
            contact = contact,
            onDismiss = { selectedContact = null }
        )
    }

    // Error handling
    state.error?.let { error ->
        LaunchedEffect(error) {
            // Show snackbar or alert
            Timber.e("Crisis error: $error")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrisisTopBar(
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crisis Handbook")
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            titleContentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    )
}

@Composable
private fun CrisisMainContent(
    state: CrisisHandbookState,
    queryText: String,
    onQueryChange: (String) -> Unit,
    onQuerySubmit: () -> Unit,
    onQuickAction: (CrisisHandbookViewModel.QuickAction) -> Unit,
    onContactClick: (EmergencyContactInfo) -> Unit,
    onShowMap: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    isLocationGranted: Boolean,
    quickActions: List<CrisisHandbookViewModel.QuickAction>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Emergency banner
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Emergency: 112",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tap for immediate help",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Quick actions
        item {
            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(quickActions) { action ->
                    QuickActionCard(
                        action = action,
                        onClick = { onQuickAction(action) }
                    )
                }
            }
        }

        // Search input
        item {
            OutlinedTextField(
                value = queryText,
                onValueChange = onQueryChange,
                label = { Text("Describe your emergency") },
                placeholder = { Text("e.g., severe headache, car accident, chest pain") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = onQuerySubmit) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit")
                    }
                },
                singleLine = false,
                maxLines = 3
            )
        }

        // Emergency contacts
        if (state.emergencyContacts.isNotEmpty()) {
            item {
                Text(
                    "Emergency Contacts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.emergencyContacts) { contact ->
                EmergencyContactCard(
                    contact = contact,
                    onClick = { onContactClick(contact) }
                )
            }
        }

        // Nearby facilities
        if (state.nearbyFacilities.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Nearby Facilities",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onShowMap) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Map")
                    }
                }
            }

            items(state.nearbyFacilities) { facility ->
                FacilityCard(
                    facility = facility,
                    onClick = onShowMap
                )
            }
        }

        // Response display
        state.response?.let { response ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Emergency Guidance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(response)
                    }
                }
            }
        }

        // Location permission prompt
        if (!isLocationGranted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Location Access Needed",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Enable location to find nearest facilities",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = onRequestLocationPermission) {
                            Text("Enable")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    action: CrisisHandbookViewModel.QuickAction,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = getIconForAction(action.icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                action.title,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmergencyContactCard(
    contact: EmergencyContactInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Primary: ${contact.primaryNumber}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                contact.secondaryNumber?.let {
                    Text(
                        "Secondary: $it",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "More options"
            )
        }
    }
}

@Composable
private fun FacilityCard(
    facility: com.example.mygemma3n.remote.Hospital,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocalHospital,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    facility.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    facility.address,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "${String.format("%.1f", facility.distanceKm)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "~${facility.estimatedMinutes} min",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.Directions,
                    contentDescription = "Get directions",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CrisisMapView(
    mapState: OfflineMapState,
    onBackClick: () -> Unit,
    onFacilitySelected: (String) -> Unit
) {
    val cameraPositionState = rememberCameraPositionState {
        mapState.userLocation?.let {
            position = CameraPosition.fromLatLngZoom(
                LatLng(it.latitude, it.longitude),
                14f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = mapState.userLocation != null
            )
        ) {
            // User location marker
            mapState.userLocation?.let { location ->
                Marker(
                    state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                    title = "Your Location",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                )
            }

            // Facility markers
            mapState.facilities.forEach { marker ->
                Marker(
                    state = MarkerState(
                        position = LatLng(marker.location.latitude, marker.location.longitude)
                    ),
                    title = marker.title,
                    snippet = marker.description,
                    onClick = {
                        onFacilitySelected(marker.id)
                        false
                    }
                )
            }

            // Route polyline
            mapState.route?.let { route ->
                Polyline(
                    points = route.map { LatLng(it.latitude, it.longitude) },
                    color = MaterialTheme.colorScheme.primary,
                    width = 10f
                )
            }
        }

        // Map controls overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Emergency Facilities Map",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    mapState.estimatedTime?.let { time ->
                        Text(
                            "~$time min",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyContactDialog(
    contact: EmergencyContactInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        context.startActivity(intent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { dialNumber(contact.primaryNumber) },
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(contact.description)
        },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dialNumber(contact.primaryNumber) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Call primary",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Primary: ${contact.primaryNumber}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                contact.secondaryNumber?.let { number ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dialNumber(number) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Call secondary",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Secondary: $number",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                contact.smsNumber?.let { number ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("sms:$number")
                                }
                                context.startActivity(smsIntent)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = "Send SMS",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SMS: $number")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Tap any number to call or SMS directly",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun getIconForAction(iconName: String): ImageVector {
    return when (iconName) {
        "phone" -> Icons.Default.Phone
        "hospital" -> Icons.Default.LocalHospital
        "medical" -> Icons.Default.MedicalServices
        "warning" -> Icons.Default.Warning
        else -> Icons.Default.Emergency
    }
}

private fun startVoiceInput(launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>) {
    val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your emergency")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    launcher.launch(intent)
}