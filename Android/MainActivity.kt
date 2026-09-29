package com.example.aminashitster

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlin.random.Random
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star

enum class GameMode {
    CLASSIC,
    SNEAK_PEEK,
    AMINAS_SPECIAL
}


class MainActivity : ComponentActivity() {

    private val spotifyClientId = "99c3de6d966545fd9ce599147c2833f4"
    private val spotifyRedirectUri = "https://com.example.aminashitster/callback"

    private var spotifyAppRemote: SpotifyAppRemote? = null

    var currentScreen by mutableStateOf("menu")
    var isPlaying by mutableStateOf(false)

    private var gameMode = GameMode.CLASSIC

    private val handler = Handler(Looper.getMainLooper())
    private var findOutPauseRunnable: Runnable? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {

                    when (currentScreen) {

                        // -------------------------------------------------
                        // MAIN MENU
                        // -------------------------------------------------

                        "menu" -> MainMenu(
                            onPlayNow = {
                                currentScreen = "gameMode"
                            },
                            onGameModes = {
                                currentScreen = "gameModesInfo"
                            },
                            onSettings = {
                                currentScreen = "settings"
                            }
                        )

                        "gameModesInfo" -> GameModesInfoScreen(
                            onBack = {
                                currentScreen = "menu"
                            }
                        )


                        // -------------------------------------------------
                        // GAME MODE SELECTION
                        // -------------------------------------------------

                        "gameMode" -> GameModeScreen(
                            onClassic = {
                                gameMode = GameMode.CLASSIC
                                startQrScanner()
                            },
                            onSneakPeek = {
                                gameMode = GameMode.SNEAK_PEEK
                                startQrScanner()
                            },
                            onAminasSpecial = {
                                gameMode = GameMode.AMINAS_SPECIAL
                                startQrScanner()
                            },
                            onBack = {
                                currentScreen = "menu"
                            }
                        )


                        // -------------------------------------------------
                        // PLAYER
                        // -------------------------------------------------

                        "player" -> PlayerScreen(
                            gameMode = gameMode,
                            isPlaying = isPlaying,
                            onTogglePlayPause = {
                                when (gameMode) {
                                    GameMode.CLASSIC -> togglePlayPause()
                                    GameMode.SNEAK_PEEK -> replaySnippet(3000L)
                                    GameMode.AMINAS_SPECIAL -> replaySnippet(1000L)
                                }
                            },
                            onScanNext = {
                                pausePlayback()
                                startQrScanner()
                            },
                            onBackToMenu = {
                                pausePlayback()
                                currentScreen = "menu"
                            }
                        )


                        // -------------------------------------------------
                        // HOW TO PLAY
                        // -------------------------------------------------

                        "settings" -> SettingsScreen(
                            onBack = {
                                currentScreen = "menu"
                            }
                        )
                    }
                }
            }
        }
    }


    // ============================================================
    // SPOTIFY CONNECTION
    // ============================================================

    override fun onStart() {
        super.onStart()

        connectToSpotify()
    }


    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // If the app is sent to the background or being cleared,
        // pause playback
        if (
            level == TRIM_MEMORY_UI_HIDDEN ||
            level == TRIM_MEMORY_COMPLETE
        ) {

            spotifyAppRemote?.playerApi?.pause()

            isPlaying = false
        }
    }


    override fun onDestroy() {
        super.onDestroy()

        // Remove FIND OUT timer
        findOutPauseRunnable?.let {
            handler.removeCallbacks(it)
        }

        handler.removeCallbacksAndMessages(null)

        // Pause playback before disconnecting Spotify
        spotifyAppRemote?.let { remote ->

            remote.playerApi.pause()

            SpotifyAppRemote.disconnect(remote)

            spotifyAppRemote = null
        }
    }


    private fun connectToSpotify() {

        val connectionParams =
            ConnectionParams.Builder(spotifyClientId)
                .setRedirectUri(spotifyRedirectUri)
                .showAuthView(true)
                .build()


        SpotifyAppRemote.connect(
            this,
            connectionParams,
            object : Connector.ConnectionListener {

                override fun onConnected(
                    appRemote: SpotifyAppRemote
                ) {

                    spotifyAppRemote = appRemote

                    Log.d(
                        "Spotify",
                        "Connected to Spotify"
                    )


                    // Fetch actual playback state
                    appRemote.playerApi.playerState
                        .setResultCallback { playerState ->

                            isPlaying =
                                !playerState.isPaused
                        }
                }


                override fun onFailure(
                    throwable: Throwable
                ) {

                    Log.e(
                        "Spotify",
                        "Could not connect to Spotify",
                        throwable
                    )
                }
            }
        )
    }


    // ============================================================
    // QR SCANNER
    // ============================================================

    private fun startQrScanner() {

        val options =
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE
                )
                .enableAutoZoom()
                .build()


        val scanner =
            GmsBarcodeScanning.getClient(
                this,
                options
            )


        val moduleInstallClient =
            ModuleInstall.getClient(this)


        val moduleInstallRequest =
            ModuleInstallRequest
                .newBuilder()
                .addApi(scanner)
                .build()


        moduleInstallClient
            .areModulesAvailable(scanner)
            .addOnSuccessListener { response ->

                if (
                    response.areModulesAvailable()
                ) {

                    launchScanner(scanner)

                } else {

                    moduleInstallClient
                        .installModules(
                            moduleInstallRequest
                        )
                        .addOnSuccessListener { installResponse ->

                            if (
                                installResponse
                                    .areModulesAlreadyInstalled()
                            ) {

                                launchScanner(scanner)

                            } else {

                                Toast.makeText(
                                    this,
                                    "Downloading scanner module...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .addOnFailureListener { exception ->

                            Toast.makeText(
                                this,
                                "Failed to download scanner module: ${exception.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Module check failed: ${exception.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    private fun launchScanner(
        scanner: GmsBarcodeScanner
    ) {

        scanner.startScan()
            .addOnSuccessListener { barcode ->

                val value =
                    barcode.rawValue

                if (
                    !value.isNullOrEmpty()
                ) {

                    openUrl(value)
                }
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Scan failed: ${exception.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    // ============================================================
    // SPOTIFY URL HANDLING
    // ============================================================

    private fun openUrl(
        value: String
    ) {

        val uri =
            value.toUri()


        // Spotify URI directly
        if (
            value.startsWith(
                "spotify:track:"
            )
        ) {

            playTrack(value)

            return
        }


        // Normal Spotify URL
        if (
            uri.host == "open.spotify.com" &&
            uri.pathSegments.size >= 2
        ) {

            val type =
                uri.pathSegments[0]

            val id =
                uri.pathSegments[1]


            if (
                type == "track"
            ) {

                val spotifyUri =
                    "spotify:track:$id"

                playTrack(
                    spotifyUri
                )
            }
        }
    }


    // ============================================================
    // CHOOSE PLAYBACK MODE
    // ============================================================

    private fun playTrack(spotifyUri: String) {
        when (gameMode) {
            GameMode.CLASSIC -> playOrderMode(spotifyUri)
            GameMode.SNEAK_PEEK -> playSnippetMode(spotifyUri, 3000L)
            GameMode.AMINAS_SPECIAL -> playSnippetMode(spotifyUri, 1000L)
        }
    }


    // ============================================================
    // ORDER MODE
    //
    // Plays the song normally from the beginning.
    // ============================================================

    private fun playOrderMode(
        spotifyUri: String
    ) {

        val remote =
            spotifyAppRemote


        if (
            remote != null
        ) {

            // Remove possible FIND OUT timer
            findOutPauseRunnable?.let {
                handler.removeCallbacks(it)
            }

            findOutPauseRunnable = null


            remote.playerApi
                .play(spotifyUri)
                .setResultCallback {

                    isPlaying = true

                    currentScreen =
                        "player"
                }
                .setErrorCallback { throwable ->

                    Log.e(
                        "Spotify",
                        "Playback error",
                        throwable
                    )


                    Toast.makeText(
                        this,
                        "Failed to play: ${throwable.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }

        } else {

            Toast.makeText(
                this,
                "Spotify is not connected",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
// INITIAL SNIPPET PLAYBACK (ON QR SCAN)
// ============================================================
    private fun playSnippetMode(
        spotifyUri: String,
        sampleDurationMs: Long
    ) {
        val remote = spotifyAppRemote ?: run {
            Toast.makeText(this, "Spotify is not connected", Toast.LENGTH_SHORT).show()
            return
        }

        // Clear previous timer
        findOutPauseRunnable?.let { handler.removeCallbacks(it) }
        findOutPauseRunnable = null

        var hasSeeked = false

        val stateSubscription = remote.playerApi.subscribeToPlayerState()

        stateSubscription.setEventCallback { playerState ->
            val track = playerState.track

            if (track != null && !hasSeeked && !playerState.isPaused) {
                hasSeeked = true

                val duration = track.duration

                // Pick random start position and SAVE it for replay
                currentSnippetPositionMs = if (duration <= sampleDurationMs) {
                    0L
                } else {
                    val maxStartPosition = duration - sampleDurationMs
                    Random.nextLong(0, maxStartPosition + 1)
                }

                Log.d("Spotify", "Saved snippet position: $currentSnippetPositionMs ms")

                // Seek to position
                remote.playerApi.seekTo(currentSnippetPositionMs).setResultCallback {
                    isPlaying = true
                    currentScreen = "player"

                    // Schedule pause after duration
                    findOutPauseRunnable = Runnable {
                        remote.playerApi.pause().setResultCallback {
                            isPlaying = false
                            Log.d("Spotify", "Snippet playback auto-paused")
                        }
                    }

                    handler.postDelayed(findOutPauseRunnable!!, sampleDurationMs)
                    stateSubscription.cancel()
                }.setErrorCallback { throwable ->
                    Log.e("Spotify", "Seek error", throwable)
                    stateSubscription.cancel()
                }
            }
        }

        // Start playback to trigger event callback
        remote.playerApi.play(spotifyUri).setErrorCallback { throwable ->
            Log.e("Spotify", "Playback error", throwable)
            stateSubscription.cancel()
        }
    }


    // ============================================================
    // PLAY / PAUSE
    // ============================================================

    private fun togglePlayPause() {

        val remote =
            spotifyAppRemote
                ?: return


        if (
            isPlaying
        ) {

            remote.playerApi
                .pause()
                .setResultCallback {

                    isPlaying =
                        false
                }

        } else {

            remote.playerApi
                .resume()
                .setResultCallback {

                    isPlaying =
                        true
                }
        }
    }

    // Track the active snippet start position
    private var currentSnippetPositionMs: Long = 0L

    // ============================================================
    // REPLAY SAVED SNIPPET (ON BUTTON PRESS)
    // ============================================================
    private fun replaySnippet(sampleDurationMs: Long) {
        val remote = spotifyAppRemote ?: return

        // Clear any active timer before starting a new snippet
        findOutPauseRunnable?.let { handler.removeCallbacks(it) }

        // 1. Seek to saved timestamp first
        remote.playerApi.seekTo(currentSnippetPositionMs).setResultCallback {
            // 2. Resume playback
            remote.playerApi.resume().setResultCallback {
                isPlaying = true

                // 3. Schedule auto-pause
                findOutPauseRunnable = Runnable {
                    remote.playerApi.pause().setResultCallback {
                        isPlaying = false
                    }
                }

                handler.postDelayed(findOutPauseRunnable!!, sampleDurationMs)
            }.setErrorCallback { throwable ->
                Log.e("Spotify", "Resume error during replay", throwable)
                isPlaying = false
            }
        }.setErrorCallback { throwable ->
            Log.e("Spotify", "Seek error during replay", throwable)
            isPlaying = false
        }
    }


    // ============================================================
    // PAUSE PLAYBACK
    // ============================================================

    private fun pausePlayback() {

        // Cancel FIND OUT timer
        findOutPauseRunnable?.let {

            handler.removeCallbacks(
                it
            )
        }

        findOutPauseRunnable =
            null


        spotifyAppRemote
            ?.playerApi
            ?.pause()
            ?.setResultCallback {

                isPlaying =
                    false
            }
    }
}


// ================================================================
// MAIN MENU
// ================================================================

@Composable
fun MainMenu(
    onPlayNow: () -> Unit,
    onSettings: () -> Unit,
    onGameModes: () -> Unit = {}
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
                .padding(
                    24.dp
                )
    ) {

        Image(
            painter =
                painterResource(
                    R.drawable.game2
                ),
            contentDescription =
                "Game",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .scale(
                        1.15f
                    )
        )


        Image(
            painter =
                painterResource(
                    R.drawable.speaker
                ),
            contentDescription =
                "Game",
            modifier =
                Modifier
                    .width(
                        400.dp
                    )
                    .padding(
                        top = 18.dp
                    )
                    .scale(
                        1.3f
                    )
        )


        // PLAY NOW
        Button(
            onClick =
                onPlayNow,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 23.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.PlayArrow,
                    contentDescription =
                        "Play Now",
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        "PLAY NOW",
                    fontSize =
                        20.sp
                )
            }
        }


        // GAME MODES
        Button(
            onClick =
                onGameModes,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 20.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.VideogameAsset,
                    contentDescription =
                        "Game Modes",
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        "GAME MODES",
                    fontSize =
                        20.sp
                )
            }
        }

        // DESCRIPTION / HOW TO PLAY
        Button(
            onClick =
                onSettings,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 20.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.HelpOutline,
                    contentDescription =
                        "DESCRIPTION",
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        "DESCRIPTION",
                    fontSize =
                        20.sp
                )
            }
        }

        // COPYRIGHT NOTICE
        Text(
            text =
                "Copyright © XAERON",
            color =
                Color.Gray,
            fontSize =
                14.sp,
            textAlign =
                TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
        )
    }
}


// ================================================================
// GAME MODE SELECTION SCREEN
// ================================================================

@Composable
fun GameModeScreen(
    onClassic: () -> Unit,
    onSneakPeek: () -> Unit,
    onAminasSpecial: () -> Unit,
    onBack: () -> Unit
) {

    BackHandler {

        onBack()
    }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
                .padding(
                    24.dp
                )
    ) {

        Image(
            painter =
                painterResource(
                    R.drawable.game2
                ),
            contentDescription =
                "Game",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .scale(
                        1.15f
                    )
        )


        Image(
            painter =
                painterResource(
                    R.drawable.speaker
                ),
            contentDescription =
                "Game",
            modifier =
                Modifier
                    .width(
                        400.dp
                    )
                    .padding(
                        top = 18.dp
                    )
                    .scale(
                        1.3f
                    )
        )


        // CLASSIC MODE
        Button(
            onClick =
                onClassic,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 23.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.MusicNote,
                    contentDescription =
                        "Classic Mode",
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        "CLASSIC MODE",
                    fontSize =
                        20.sp
                )
            }
        }


        // SNEAK PEEK MODE
        Button(
            onClick =
                onSneakPeek,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 20.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Psychology,
                    contentDescription =
                        "Sneak Peek Mode",
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        "SNEAK PEEK",
                    fontSize =
                        20.sp
                )
            }
        }


        // AMINA'S SPECIAL MODE
        Button(
            onClick =
                onAminasSpecial,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 20.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Star,
                    contentDescription =
                        "Amina's Special Mode",
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        "AMINA'S SPECIAL",
                    fontSize =
                        20.sp
                )
            }
        }

        // COPYRIGHT NOTICE
        Text(
            text =
                "Copyright © XAERON",
            color =
                Color.Gray,
            fontSize =
                14.sp,
            textAlign =
                TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
        )
    }
}


// ================================================================
// DESCRIPTION SCREEN
// ================================================================

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {

    val scrollState =
        rememberScrollState()


    BackHandler {

        onBack()
    }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
                .padding(
                    24.dp
                )
                .verticalScroll(
                    scrollState
                )
    ) {

        Image(
            painter =
                painterResource(
                    R.drawable.game2
                ),
            contentDescription =
                "Game",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .scale(
                        1.15f
                    )
        )


        Text(
            text =
                "AMINA'S HITSTER",
            color =
                Color.White,
            fontSize =
                30.sp,
            textAlign =
                TextAlign.Left,
            modifier =
                Modifier.padding(
                    top = 20.dp
                )
        )

        Text(
            text =
                "DESCRIPTION",
            color =
                Color(0xFFF50C6F),
            fontSize =
                22.sp,
            textAlign =
                TextAlign.Left,
            modifier =
                Modifier.padding(
                    top = 20.dp
                )
        )


        Text(
            text =
                "Join the ultimate party powered by Amina's favorite songs. Take turns placing songs in the correct order on your timeline and prove who knows the music best.",
            color =
                Color.White,
            fontSize =
                20.sp,
            textAlign =
                TextAlign.Justify,
            modifier =
                Modifier.padding(
                    top = 20.dp
                )
        )


        Text(
            text =
                "Turn any night into a party, no DJ needed. Just pick your AMINA'S HITSTER edition and press play. A game for everyone, whether you’re a music expert or just love a good tune. Scan the QR code, place the song in the timeline, and have the time of your life.",
            color =
                Color.White,
            fontSize =
                20.sp,
            textAlign =
                TextAlign.Justify,
            modifier =
                Modifier.padding(
                    top = 10.dp
                )
        )

        Text(
            text =
                "THERE IS NO BETTER PLAN THAN A HITSTER PLAN!",
            color =
                Color.White,
            fontSize =
                20.sp,
            textAlign =
                TextAlign.Justify,
            modifier =
                Modifier.padding(
                    top = 10.dp
                )
        )

        Spacer(
            modifier =
                Modifier.weight(
                    1f
                )
        )


        // COPYRIGHT NOTICE
        Text(
            text =
                "Copyright © XAERON",
            color =
                Color.Gray,
            fontSize =
                14.sp,
            textAlign =
                TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 7.dp,
                    )
        )
    }
}

// ================================================================
// GAME MODES INFO SCREEN
// ================================================================

@Composable
fun GameModesInfoScreen(
    onBack: () -> Unit
) {

    val scrollState =
        rememberScrollState()


    BackHandler {

        onBack()
    }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
                .padding(
                    24.dp
                )
                .verticalScroll(
                    scrollState
                )
    ) {

        Image(
            painter =
                painterResource(
                    R.drawable.game2
                ),
            contentDescription =
                "Game",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .scale(
                        1.15f
                    )
        )


        Text(
            text =
                "GAME MODES",
            color =
                Color.White,
            fontSize =
                26.sp,
            textAlign =
                TextAlign.Left,
            modifier =
                Modifier.padding(
                    top = 20.dp
                )
        )


        // CLASSIC MODE SECTION
        Text(
            text =
                "1. CLASSIC MODE",
            color =
                Color(0xFFF50C6F),
            fontSize =
                22.sp,
            textAlign =
                TextAlign.Left,
            modifier =
                Modifier.padding(
                    top = 20.dp
                )
        )


        Text(
            text =
                "In Classic mode, scan a card's QR code to play the track normally from the beginning. Listen to the music, guess when it was released, and place it in the correct chronological position on your timeline. The first player to collect 10 cards earns the title of AMINA'S HITSTER.",
            color =
                Color.White,
            fontSize =
                20.sp,
            textAlign =
                TextAlign.Justify,
            modifier =
                Modifier.padding(
                    top = 10.dp
                )
        )


        // SNEAK PEEK MODE SECTION
        Text(
            text =
                "2. SNEAK PEEK MODE",
            color =
                Color(0xFFF50C6F),
            fontSize =
                22.sp,
            textAlign =
                TextAlign.Left,
            modifier =
                Modifier.padding(
                    top = 20.dp
                )
        )


        Text(
            text =
                "In Sneak Peek mode, test your ultimate music knowledge! Scanning a card jumps to a random position in the song and plays only a 3-second sample before automatically pausing. Name or place the song using just that short snippet!",
            color =
                Color.White,
            fontSize =
                20.sp,
            textAlign =
                TextAlign.Justify,
            modifier =
                Modifier.padding(
                    top = 10.dp
                )
        )


        // AMINA'S SPECIAL MODE SECTION
        Text(
            text =
                "3. AMINA'S SPECIAL MODE",
            color =
                Color(0xFFF50C6F),
            fontSize =
                22.sp,
            textAlign =
                TextAlign.Left,
            modifier =
                Modifier.padding(
                    top = 20.dp
                )
        )


        Text(
            text =
                "Amina's Special mode is the ultimate master level challenge with Amina's favorite songs! Just like Sneak Peek mode, scanning a card seeks to a completely random spot in the track except you only get a lightning fast 1 second snippet before it pauses. Don't be a brat and try it!",
            color =
                Color.White,
            fontSize =
                20.sp,
            textAlign =
                TextAlign.Justify,
            modifier =
                Modifier.padding(
                    top = 10.dp
                )
        )

        Spacer(
            modifier =
                Modifier.weight(
                    1f
                )
        )


        // COPYRIGHT NOTICE
        Text(
            text =
                "Copyright © XAERON",
            color =
                Color.Gray,
            fontSize =
                14.sp,
            textAlign =
                TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 7.dp
                    )
        )
    }
}

// ================================================================
// PLAYER SCREEN
// ================================================================

@Composable
fun PlayerScreen(
    gameMode: GameMode,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onScanNext: () -> Unit,
    onBackToMenu: () -> Unit
) {

    // Dynamic button label based on mode and playback state
    val playButtonText = when {
        gameMode == GameMode.CLASSIC && isPlaying -> "PAUSE MUSIC"
        gameMode == GameMode.CLASSIC && !isPlaying -> "RESUME MUSIC"
        else -> "REPLAY SNIPPET" // Sneak Peek & Amina's Special
    }

    BackHandler {
        onBackToMenu()
    }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
                .padding(
                    24.dp
                )
    ) {

        Image(
            painter =
                painterResource(
                    R.drawable.game2
                ),
            contentDescription =
                "Game",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .scale(
                        1.15f
                    )
        )


        Image(
            painter =
                painterResource(
                    R.drawable.speaker
                ),
            contentDescription =
                "Game",
            modifier =
                Modifier
                    .width(
                        400.dp
                    )
                    .padding(
                        top = 18.dp
                    )
                    .scale(
                        1.3f
                    )
        )


        // PLAY / PAUSE / REPLAY SNIPPET
        Button(
            onClick =
                onTogglePlayPause,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 23.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        if (gameMode == GameMode.CLASSIC && isPlaying)
                            Icons.Default.Pause
                        else
                            Icons.Default.PlayArrow,
                    contentDescription =
                        playButtonText,
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        playButtonText,
                    fontSize =
                        20.sp
                )
            }
        }


        // NEXT CARD
        Button(
            onClick =
                onScanNext,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 20.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.QrCodeScanner,
                    contentDescription =
                        "Scan QR",
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        "NEXT CARD",
                    fontSize =
                        20.sp
                )
            }
        }


        // BACK TO MENU
        Button(
            onClick =
                onBackToMenu,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Color(
                                0xFFF50C6F
                            ),
                        contentColor =
                            Color.White
                    ),
            modifier =
                Modifier
                    .padding(
                        top = 20.dp
                    )
                    .height(
                        60.dp
                    )
                    .width(
                        400.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Menu,
                    contentDescription =
                        "Menu",
                    tint =
                        Color.White
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )


                Text(
                    text =
                        "BACK TO MENU",
                    fontSize =
                        20.sp
                )
            }
        }

        // COPYRIGHT NOTICE
        Text(
            text =
                "Copyright © XAERON",
            color =
                Color.Gray,
            fontSize =
                14.sp,
            textAlign =
                TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
        )
    }
}