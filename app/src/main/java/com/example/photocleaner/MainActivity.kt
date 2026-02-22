package com.example.photocleaner

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoItem(val id: Long, val uri: Uri)

enum class SwipeDecision {
    KEEP,
    TRASH
}

data class UndoEntry(val item: PhotoItem, val decision: SwipeDecision, val index: Int)

data class UiState(
    val photos: List<PhotoItem> = emptyList(),
    val currentIndex: Int = 0,
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val permissionMessage: String = "사진 접근 권한이 필요합니다.",
    val statusMessage: String? = null,
    val pendingDeleteIntent: IntentSenderRequest? = null,
    val pendingTrashIntent: IntentSenderRequest? = null,
    val undoEntry: UndoEntry? = null
) {
    val currentPhoto: PhotoItem?
        get() = photos.getOrNull(currentIndex)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean, partial: Boolean = false) {
        _uiState.update {
            it.copy(
                permissionGranted = granted,
                permissionMessage = if (granted) {
                    if (partial) "일부 사진 접근이 허용되었습니다. 보이는 항목만 정리할 수 있어요."
                    else ""
                } else {
                    "권한이 거부되었습니다. 설정에서 사진 접근 권한을 허용해 주세요."
                }
            )
        }
        if (granted) loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, statusMessage = null) }
            val photos = withContext(Dispatchers.IO) {
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val result = mutableListOf<PhotoItem>()
                getApplication<Application>().contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val uri = ContentUris.withAppendedId(collection, id)
                        result += PhotoItem(id, uri)
                    }
                }
                result
            }
            _uiState.update {
                it.copy(
                    photos = photos,
                    currentIndex = 0,
                    loading = false,
                    statusMessage = if (photos.isEmpty()) "정리할 사진이 없습니다." else null
                )
            }
        }
    }

    fun onSwipe(decision: SwipeDecision) {
        val state = _uiState.value
        val photo = state.currentPhoto ?: return
        when (decision) {
            SwipeDecision.KEEP -> {
                _uiState.update {
                    it.copy(
                        undoEntry = UndoEntry(photo, SwipeDecision.KEEP, it.currentIndex),
                        currentIndex = (it.currentIndex + 1).coerceAtMost(it.photos.size)
                    )
                }
            }

            SwipeDecision.TRASH -> requestTrash(photo)
        }
    }

    private fun requestTrash(photo: PhotoItem) {
        val resolver = getApplication<Application>().contentResolver
        val uriList = listOf(photo.uri)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sender = MediaStore.createTrashRequest(resolver, uriList, true).intentSender
                _uiState.update { it.copy(pendingTrashIntent = IntentSenderRequest.Builder(sender).build()) }
            } else {
                // Android 10 이하 fallback: 시스템 삭제 확인을 유도하거나 권한 예외 처리.
                resolver.delete(photo.uri, null, null)
                moveToNextWithUndo(photo, SwipeDecision.TRASH)
            }
        } catch (security: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && security is RecoverableSecurityException) {
                val sender = security.userAction.actionIntent.intentSender
                _uiState.update { it.copy(pendingDeleteIntent = IntentSenderRequest.Builder(sender).build()) }
            } else {
                _uiState.update {
                    it.copy(statusMessage = "이 사진은 OS 제약으로 바로 삭제할 수 없습니다. Keep으로 넘겨주세요.")
                }
            }
        }
    }

    fun onTrashResult(resultCode: Int) {
        val photo = _uiState.value.currentPhoto ?: return
        if (resultCode == Activity.RESULT_OK) {
            moveToNextWithUndo(photo, SwipeDecision.TRASH)
        } else {
            _uiState.update { it.copy(statusMessage = "삭제가 취소되었습니다.") }
        }
        _uiState.update { it.copy(pendingTrashIntent = null, pendingDeleteIntent = null) }
    }

    private fun moveToNextWithUndo(photo: PhotoItem, decision: SwipeDecision) {
        _uiState.update {
            it.copy(
                undoEntry = UndoEntry(photo, decision, it.currentIndex),
                currentIndex = (it.currentIndex + 1).coerceAtMost(it.photos.size),
                statusMessage = if (decision == SwipeDecision.TRASH) "최근 삭제됨(또는 삭제 요청) 처리됨" else null
            )
        }
    }

    fun undo() {
        val undo = _uiState.value.undoEntry ?: return
        if (undo.decision == SwipeDecision.TRASH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val resolver = getApplication<Application>().contentResolver
            val sender = MediaStore.createTrashRequest(resolver, listOf(undo.item.uri), false).intentSender
            _uiState.update { it.copy(pendingTrashIntent = IntentSenderRequest.Builder(sender).build()) }
        }

        _uiState.update {
            it.copy(
                currentIndex = undo.index,
                undoEntry = null,
                statusMessage = "최근 동작을 되돌렸습니다."
            )
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        viewModel.onPermissionResult(granted = granted, partial = granted)
    }

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onTrashResult(result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsState()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val context = LocalContext.current

                    LaunchedEffect(state.statusMessage) {
                        state.statusMessage?.let {
                            snackbarHostState.showSnackbar(it)
                            viewModel.clearStatus()
                        }
                    }

                    LaunchedEffect(state.pendingTrashIntent, state.pendingDeleteIntent) {
                        state.pendingTrashIntent?.let(intentSenderLauncher::launch)
                        state.pendingDeleteIntent?.let(intentSenderLauncher::launch)
                    }

                    LaunchedEffect(Unit) {
                        requestPhotoPermissionIfNeeded()
                    }

                    AppScreen(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        onSwipe = viewModel::onSwipe,
                        onUndo = viewModel::undo,
                        onRequestPermission = { requestPhotoPermissionIfNeeded() },
                        onOpenSettings = { openAppSettings(context) }
                    )
                }
            }
        }
    }

    private fun requestPhotoPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(arrayOf(permission))
    }

    private fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        startActivity(intent)
    }
}

@Composable
private fun AppScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onSwipe: (SwipeDecision) -> Unit,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Photo Cleaner MVP",
            style = MaterialTheme.typography.headlineSmall
        )

        when {
            !state.permissionGranted -> PermissionView(
                message = state.permissionMessage,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings
            )

            state.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("사진을 불러오는 중...")
            }

            state.currentPhoto == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("모든 사진을 확인했습니다.")
            }

            else -> {
                val currentPhoto = state.currentPhoto
                var dragOffset by remember { mutableFloatStateOf(0f) }
                val swipeThreshold = 180f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .pointerInput(currentPhoto?.id) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount },
                                onDragEnd = {
                                    when {
                                        dragOffset > swipeThreshold -> onSwipe(SwipeDecision.KEEP)
                                        dragOffset < -swipeThreshold -> onSwipe(SwipeDecision.TRASH)
                                    }
                                    dragOffset = 0f
                                }
                            )
                        }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    currentPhoto?.let { PhotoCard(uri = it.uri) }
                }

                Text(
                    "왼쪽 스와이프: 휴지통(시스템 확인), 오른쪽 스와이프: 남김",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(onClick = onUndo, enabled = state.undoEntry != null) {
                    Text("Undo (최근 1개)")
                }

                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }
}

@Composable
private fun PermissionView(
    message: String,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 12.dp)) {
            Text("권한 다시 요청")
        }
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
            Text("앱 설정 열기")
        }
    }
}

@Composable
private fun PhotoCard(uri: Uri) {
    val context = LocalContext.current
    val bitmapState = remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmapState.value = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("미리보기 로딩 중")
            Box(Modifier.size(8.dp))
        }
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.setTargetSampleSize(2)
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
