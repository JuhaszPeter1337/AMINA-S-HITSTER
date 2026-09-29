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

enum class GameMode {
    CLASSIC,
    SNEAK_PEEK
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
                            onBack = {
                                currentScreen = "menu"
                            }
                        )


                        // -------------------------------------------------
                        // PLAYER
                        // -------------------------------------------------

                        "player" -> PlayerScreen(
                            isPlaying = isPlaying,
                            onTogglePlayPause = {
                                togglePlayPause()
                            },
                            onScanNext = {

                                // Stop current song before scanning
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
            GameMode.CLASSIC -> {
                playOrderMode(spotifyUri)
            }
            GameMode.SNEAK_PEEK -> {
                playFindOutMode(spotifyUri)
            }
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
    // FIND OUT MODE
    //
    // 1. Start Spotify track
    // 2. Get duration
    // 3. Generate random position
    // 4. Seek to random position
    // 5. Play
    // 6. Pause after 3 seconds
    // ============================================================

    // ============================================================
    // FIND OUT MODE (FIXED)
    //
    // 1. Start Spotify track
    // 2. Subscribe to PlayerState until track is active/playing
    // 3. Extract duration & calculate random seek position
    // 4. Seek to random position
    // 5. Schedule pause after 3 seconds
    // ============================================================

    private fun playFindOutMode(
        spotifyUri: String
    ) {
        val remote = spotifyAppRemote ?: run {
            Toast.makeText(
                this,
                "Spotify is not connected",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Cancel any pending FIND OUT timer
        findOutPauseRunnable?.let {
            handler.removeCallbacks(it)
        }
        findOutPauseRunnable = null

        var hasSeeked = false

        // Subscribe to state updates to ensure song is loaded before seeking
        val stateSubscription = remote.playerApi.subscribeToPlayerState()

        stateSubscription.setEventCallback { playerState ->
            val track = playerState.track

            if (track != null && !hasSeeked && !playerState.isPaused) {
                hasSeeked = true

                val duration = track.duration
                Log.d("Spotify", "Song duration: $duration ms")

                // Choose random position leaving at least 3 seconds before the end
                val randomPosition = if (duration <= 3000) {
                    0L
                } else {
                    val maxStartPosition = duration - 3000
                    Random.nextLong(0, maxStartPosition + 1)
                }

                Log.d("Spotify", "FIND OUT random position: $randomPosition ms")

                // Seek to random position
                remote.playerApi.seekTo(randomPosition).setResultCallback {
                    isPlaying = true
                    currentScreen = "player"

                    // Schedule pause 3 seconds after seeking completes
                    findOutPauseRunnable = Runnable {
                        remote.playerApi.pause().setResultCallback {
                            isPlaying = false
                            Log.d("Spotify", "FIND OUT 3-second sample finished")
                        }
                    }

                    handler.postDelayed(findOutPauseRunnable!!, 3000)

                    // Unsubscribe from state updates once initial sample trigger is scheduled
                    stateSubscription.cancel()
                }.setErrorCallback { throwable ->
                    Log.e("Spotify", "Seek error", throwable)
                    stateSubscription.cancel()
                }
            }
        }

        // Start playback to trigger the player state event
        remote.playerApi.play(spotifyUri).setErrorCallback { throwable ->
            Log.e("Spotify", "Playback error", throwable)
            Toast.makeText(
                this,
                "Failed to play: ${throwable.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
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
                    .padding(
                        top = 5.dp
                    )
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
                        top = 30.dp
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
                        top = 40.dp
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


        // HOW TO PLAY
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
                        "How To Play",
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
    }
}


// ================================================================
// GAME MODE SELECTION SCREEN
// ================================================================

@Composable
fun GameModeScreen(
    onClassic: () -> Unit,
    onSneakPeek: () -> Unit,
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
                    .padding(
                        top = 5.dp
                    )
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
                "Game Mode",
            modifier =
                Modifier
                    .width(
                        400.dp
                    )
                    .padding(
                        top = 30.dp
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
                        top = 40.dp
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
                        "SNEAK PEEK MODE",
                    fontSize =
                        20.sp
                )
            }
        }
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
                    .padding(
                        top = 5.dp
                    )
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
                "The first player to collect 10 cards earns the title of AMINA'S HITSTER.",
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
                    .padding(
                        top = 5.dp
                    )
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
                "In Classic mode, scan a card's QR code to play the track normally from the beginning. Listen to the music, guess when it was released, and place it in the correct chronological position on your timeline.",
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
    }
}


// ================================================================
// PLAYER SCREEN
// ================================================================

@Composable
fun PlayerScreen(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onScanNext: () -> Unit,
    onBackToMenu: () -> Unit
) {

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
                    .padding(
                        top = 5.dp
                    )
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
                        top = 30.dp
                    )
                    .scale(
                        1.3f
                    )
        )


        // PLAY / PAUSE
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
                        top = 40.dp
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
                        if (isPlaying)
                            Icons.Default.Pause
                        else
                            Icons.Default.PlayArrow,
                    contentDescription =
                        if (isPlaying)
                            "Pause"
                        else
                            "Play",
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
                        if (isPlaying)
                            "PAUSE MUSIC"
                        else
                            "RESUME MUSIC",
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
    }
}