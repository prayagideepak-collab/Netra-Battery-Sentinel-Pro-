package com.example.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engines.festival.FestivalContextEngine
import com.example.service.GeminiStudioEngine
import com.example.viewmodel.BatteryViewModel
import kotlinx.coroutines.launch

enum class StudioTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FESTIVAL_THEME("Festivals & Themes", Icons.Filled.Palette),
    IMAGE_GEN("Image Studio", Icons.Filled.Image),
    VIDEO_VEO("Veo Video", Icons.Filled.Movie),
    VISION_ANALYZE("Vision & Video", Icons.Filled.Visibility),
    FLASH_LITE("Flash Lite", Icons.Filled.Bolt),
    DEEP_THINKING("Deep Thinking", Icons.Filled.Psychology),
    MAPS_GROUNDING("Maps Data", Icons.Filled.Place)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiProductionStudioScreen(
    viewModel: BatteryViewModel,
    onClose: () -> Unit
) {
    var activeTab by remember { mutableStateOf(StudioTab.FESTIVAL_THEME) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val batteryState by viewModel.sanitizedBatteryState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AI Studio & Festival Engine",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Gemini 3 Multimodal & Dynamic Calendar Themes",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("studio_back_btn")) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal Tab Bar
            ScrollableTabRow(
                selectedTabIndex = activeTab.ordinal,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                StudioTab.values().forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { Text(tab.title, fontSize = 12.sp, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (activeTab) {
                    StudioTab.FESTIVAL_THEME -> FestivalThemeSubScreen(viewModel = viewModel)
                    StudioTab.IMAGE_GEN -> ImageStudioSubScreen()
                    StudioTab.VIDEO_VEO -> VeoVideoSubScreen()
                    StudioTab.VISION_ANALYZE -> VisionAnalyzeSubScreen()
                    StudioTab.FLASH_LITE -> FlashLiteSubScreen()
                    StudioTab.DEEP_THINKING -> DeepThinkingSubScreen(batteryState = batteryState)
                    StudioTab.MAPS_GROUNDING -> MapsGroundingSubScreen()
                }
            }
        }
    }
}

// 1. Festival & Themes Sub-Screen
@Composable
fun FestivalThemeSubScreen(viewModel: BatteryViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentFestival by FestivalContextEngine.currentFestival.collectAsStateWithLifecycle()
    var showImportDialog by remember { mutableStateOf(false) }

    val themesList = listOf(
        "FESTIVAL_AUTO" to "Auto Festival (Daily Calendar Sync)",
        "DIWALI" to "Diwali (Golden Diya & Amber)",
        "HOLI" to "Holi (Vibrant Gulal Colors)",
        "NAVRATRI" to "Navratri & Durga Puja (Ruby Red)",
        "EID" to "Eid Mubarak (Emerald & Crescent Gold)",
        "CHRISTMAS" to "Christmas (Pine & Berry Crimson)",
        "INDEPENDENCE" to "Independence / Tiranga",
        "MAKAR_SANKRANTI" to "Makar Sankranti (Solar Gold & Sky Blue)",
        "GANESH_CHATURTHI" to "Ganesh Chaturthi (Marigold & Gold)",
        "NEW_YEAR" to "New Year (Midnight Sparkle)",
        "DARK" to "Cyber Sentinel (Neon Green Dark)",
        "LIGHT" to "Standard Crisp Light",
        "AMOLED" to "Pure Pitch Black AMOLED",
        "DYNAMIC" to "Dynamic Battery Level Morphing",
        "OCEAN_BLUE" to "Oceanic Blue & Cyan",
        "SOLAR_GOLD" to "Solar Gold & Warm Amber",
        "AURORA_PURPLE" to "Aurora Purple & Violet",
        "FOREST_EMERALD" to "Forest Emerald Jade"
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Today's Festival Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Active Calendar Festival", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        currentFestival?.festivalName ?: "No Active Festival",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        currentFestival?.description ?: "No active festival detected for current date or location",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {
                                viewModel.updateSettings(settings.copy(theme = "FESTIVAL_AUTO"))
                            },
                            label = { Text("Apply Festival Theme") },
                            leadingIcon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        OutlinedButton(onClick = { showImportDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Festival", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Select App Theme & Festival Palette", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            Text("Changes apply instantly across all screens and widgets.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(themesList) { (key, label) ->
            val isSelected = settings.theme.equals(key, ignoreCase = true)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        viewModel.updateSettings(settings.copy(theme = key))
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.updateSettings(settings.copy(theme = key)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Mode: $key", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (isSelected) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("ACTIVE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        var festName by remember { mutableStateOf("") }
        var festDate by remember { mutableStateOf("08-15") }
        var festTheme by remember { mutableStateOf("DIWALI") }
        var festDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Calendar Festival") },
            text = {
                Column {
                    OutlinedTextField(
                        value = festName,
                        onValueChange = { festName = it },
                        label = { Text("Festival Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = festDate,
                        onValueChange = { festDate = it },
                        label = { Text("Date (MM-dd, e.g. 10-15)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = festDesc,
                        onValueChange = { festDesc = it },
                        label = { Text("Celebration Details") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (festName.isNotBlank()) {
                        FestivalContextEngine.importCalendarFestival(festName, festDate, festTheme, festDesc)
                        viewModel.updateSettings(settings.copy(theme = festTheme))
                    }
                    showImportDialog = false
                }) {
                    Text("Import & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// 2. High-Quality Image Studio Sub-Screen (gemini-3-pro-image-preview & gemini-3.1-flash-image-preview)
@Composable
fun ImageStudioSubScreen() {
    var prompt by remember { mutableStateOf("Vibrant Indian festival celebration with golden oil lamps and festive rangoli in 4K studio lighting") }
    var selectedSize by remember { mutableStateOf("2K") } // 1K, 2K, 4K
    var selectedAspect by remember { mutableStateOf("1:1") } // 1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9
    var useProModel by remember { mutableStateOf(true) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    val aspectRatios = listOf("1:1", "2:3", "3:2", "3:4", "4:3", "9:16", "16:9", "21:9")
    val sizes = listOf("1K", "2K", "4K")

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("High-Quality Image Studio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Powered by gemini-3-pro-image-preview & gemini-3.1-flash-image-preview", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Prompt / Image Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Aspect Ratio Selector
            Text("Aspect Ratio", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                items(aspectRatios) { ratio ->
                    FilterChip(
                        selected = selectedAspect == ratio,
                        onClick = { selectedAspect = ratio },
                        label = { Text(ratio, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Resolution / Size Selector
            Text("Resolution & Quality", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                sizes.forEach { size ->
                    FilterChip(
                        selected = selectedSize == size,
                        onClick = { selectedSize = size },
                        label = { Text(size, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useProModel, onCheckedChange = { useProModel = it })
                Text("Use Pro Model (gemini-3-pro-image-preview)", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        isGenerating = true
                        val result = GeminiStudioEngine.generateStudioImage(
                            prompt = prompt,
                            aspectRatio = selectedAspect,
                            imageSize = selectedSize,
                            useProModel = useProModel
                        )
                        generatedBitmap = result.getOrNull()
                        isGenerating = false
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating $selectedSize Image...")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Image ($selectedSize • $selectedAspect)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Output Display Box
            generatedBitmap?.let { bmp ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Generated Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Output: $selectedSize • $selectedAspect • Pro Studio Verified", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// 3. Veo Video Sub-Screen (veo-3.1-fast-generate-preview)
@Composable
fun VeoVideoSubScreen() {
    var prompt by remember { mutableStateOf("Animate festive diya lights glowing gently with cinematic camera pan and sparkler particles") }
    var selectedAspect by remember { mutableStateOf("16:9") } // 16:9 or 9:16
    var isGenerating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("Veo Video Animator", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Powered by veo-3.1-fast-generate-preview", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Video Animation Prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Aspect Ratio", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf("16:9" to "Landscape (16:9)", "9:16" to "Portrait / Reel (9:16)").forEach { (ratio, label) ->
                    FilterChip(
                        selected = selectedAspect == ratio,
                        onClick = { selectedAspect = ratio },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        isGenerating = true
                        val result = GeminiStudioEngine.animateImageToVideo(
                            prompt = prompt,
                            bitmap = null,
                            aspectRatio = selectedAspect
                        )
                        statusMessage = result.getOrNull()
                        isGenerating = false
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rendering Veo Video...")
                } else {
                    Icon(Icons.Filled.Movie, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Synthesize Video ($selectedAspect)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            statusMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Veo Generation Pipeline", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(msg, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// 4. Vision & Video Inspector Sub-Screen (gemini-3.1-pro-preview)
@Composable
fun VisionAnalyzeSubScreen() {
    var query by remember { mutableStateOf("Diagnose battery circuit integrity, thermal dispersion, and efficiency from this sample.") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("Vision & Video Inspector", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Powered by gemini-3.1-pro-preview", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Inspection / Diagnostic Prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        isAnalyzing = true
                        // Run video analysis
                        analysisResult = GeminiStudioEngine.analyzeVideo("Internal Battery Telemetry & Power Video Clip", query)
                        isAnalyzing = false
                    }
                },
                enabled = !isAnalyzing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspecting with Gemini 3.1 Pro...")
                } else {
                    Icon(Icons.Filled.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Multimodal Video & Image Analysis")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            analysisResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Inspection Report", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(result, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

// 5. Flash Lite Fast Response Sub-Screen (gemini-3.1-flash-lite)
@Composable
fun FlashLiteSubScreen() {
    var query by remember { mutableStateOf("Quick thermal check and immediate power saving action") }
    var isQuerying by remember { mutableStateOf(false) }
    var responseText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("Flash Lite Ultra-Fast Assistant", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Powered by gemini-3.1-flash-lite (lowest latency)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Instant Query") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        isQuerying = true
                        responseText = GeminiStudioEngine.fastLiteQuery(query)
                        isQuerying = false
                    }
                },
                enabled = !isQuerying,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isQuerying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Instant Fast Lite Response")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            responseText?.let { text ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Flash Lite Output", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// 6. Deep Thinking Mode Sub-Screen (gemini-3.1-pro-preview with HIGH thinking)
@Composable
fun DeepThinkingSubScreen(batteryState: com.example.service.BatteryState) {
    var query by remember { mutableStateOf("Analyze electrochemical degradation factors and recommend a mathematically optimal charge-voltage cutoff curve for long-term health.") }
    var isThinking by remember { mutableStateOf(false) }
    var thinkingResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("High Thinking Reasoning Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Powered by gemini-3.1-pro-preview with thinkingLevel = HIGH", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Complex Reasoning / Physics Question") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        isThinking = true
                        val contextPrompt = "$query\n\nCurrent Hardware Telemetry: Level=${batteryState.percentage}%, Temp=${batteryState.temperature}°C, Voltage=${batteryState.voltage}mV, Health=${batteryState.healthPercentage}%."
                        thinkingResult = GeminiStudioEngine.deepThinkingQuery(contextPrompt)
                        isThinking = false
                    }
                },
                enabled = !isThinking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isThinking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reasoning deeply...")
                } else {
                    Icon(Icons.Filled.Psychology, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Execute Deep Thinking Reasoning")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            thinkingResult?.let { res ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("High Thinking Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(res, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// 7. Maps Grounding Sub-Screen (gemini-3.5-flash with googleMaps tool)
@Composable
fun MapsGroundingSubScreen() {
    var query by remember { mutableStateOf("EV fast charging stations and battery repair centers") }
    var location by remember { mutableStateOf("Current Area / City") }
    var isSearching by remember { mutableStateOf(false) }
    var mapsResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("Google Maps Grounded Intelligence", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Powered by gemini-3.5-flash with Google Maps tool", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("What are you looking for?") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location Context") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        isSearching = true
                        mapsResult = GeminiStudioEngine.queryMapsGroundedData(query, location)
                        isSearching = false
                    }
                },
                enabled = !isSearching,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching Google Maps...")
                } else {
                    Icon(Icons.Filled.Place, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Find Grounded Locations")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            mapsResult?.let { res ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Grounded Maps Results", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(res, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
