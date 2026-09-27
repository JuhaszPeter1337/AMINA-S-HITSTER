package com.example.aminashitster

import android.content.Intent
import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    private val spotifyClientId = "bd4d4cd07ce84cba93c41309742ae27f"
    private val spotifyRedirectUri = "https://com.example.aminashitster/callback"

    private var spotifyAppRemote: SpotifyAppRemote? = null

    var currentScreen by mutableStateOf("menu") // "menu", "player", "settings"
    var isPlaying by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        "menu" -> MainMenu(
                            onReadQrCode = { startQrScanner() },
                            onSettings = { currentScreen = "settings" }
                        )
                        "player" -> PlayerScreen(
                            isPlaying = isPlaying,
                            onTogglePlayPause = { togglePlayPause() },
                            onScanNext = {
                                pausePlayback() // Stops background music when scanning the next card
                                startQrScanner()
                            },
                            onBackToMenu = { currentScreen = "menu" }
                        )
                        "settings" -> SettingsScreen(
                            onBack = { currentScreen = "menu" }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectToSpotify()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // If the app is sent to the background or being cleared, pause playback
        if (level == TRIM_MEMORY_UI_HIDDEN || level == TRIM_MEMORY_COMPLETE) {
            spotifyAppRemote?.playerApi?.pause()
            isPlaying = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Pause playback before disconnecting Spotify
        spotifyAppRemote?.let { remote ->
            remote.playerApi.pause()
            SpotifyAppRemote.disconnect(remote)
            spotifyAppRemote = null
        }
    }

    private fun connectToSpotify() {
        val connectionParams = ConnectionParams.Builder(spotifyClientId)
            .setRedirectUri(spotifyRedirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("Spotify", "Connected to Spotify")

                // Fetch actual playback state upon reconnection/resume
                appRemote.playerApi.playerState.setResultCallback { playerState ->
                    isPlaying = !playerState.isPaused
                }
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("Spotify", "Could not connect to Spotify", throwable)
            }
        })
    }

    private fun startQrScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)
        val moduleInstallClient = ModuleInstall.getClient(this)
        val moduleInstallRequest = ModuleInstallRequest.newBuilder().addApi(scanner).build()

        moduleInstallClient.areModulesAvailable(scanner)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    launchScanner(scanner)
                } else {
                    moduleInstallClient.installModules(moduleInstallRequest)
                        .addOnSuccessListener { installResponse ->
                            if (installResponse.areModulesAlreadyInstalled()) {
                                launchScanner(scanner)
                            } else {
                                Toast.makeText(this, "Downloading scanner module...", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, "Failed to download scanner module: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Module check failed: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun launchScanner(scanner: GmsBarcodeScanner) {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                if (!value.isNullOrEmpty()) {
                    openUrl(value)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Scan failed: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openUrl(value: String) {
        val uri = value.toUri()

        if (value.startsWith("spotify:track:")) {
            playTrack(value)
            return
        }

        if (uri.host == "open.spotify.com" && uri.pathSegments.size >= 2) {
            val type = uri.pathSegments[0]
            val id = uri.pathSegments[1]

            if (type == "track") {
                val spotifyUri = "spotify:track:$id"
                playTrack(spotifyUri)
            }
        }
    }

    private fun playTrack(spotifyUri: String) {
        val remote = spotifyAppRemote
        if (remote != null) {
            remote.playerApi.play(spotifyUri)
                .setResultCallback {
                    isPlaying = true
                    // Switch to the player screen once a song starts!
                    currentScreen = "player"
                }
                .setErrorCallback { throwable ->
                    Log.e("Spotify", "Playback error", throwable)
                    Toast.makeText(this, "Failed to play: ${throwable.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } else {
            Toast.makeText(this, "Spotify is not connected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        val remote = spotifyAppRemote ?: return
        if (isPlaying) {
            remote.playerApi.pause().setResultCallback {
                isPlaying = false
            }
        } else {
            remote.playerApi.resume().setResultCallback {
                isPlaying = true
            }
        }
    }

    private fun pausePlayback() {
        spotifyAppRemote?.playerApi?.pause()?.setResultCallback {
            isPlaying = false
        }
    }
}

@Composable
fun MainMenu(
    onReadQrCode: () -> Unit,
    onSettings: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.game2),
            contentDescription = "Game",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .scale(1.15f)
        )

        Image(
            painter = painterResource(R.drawable.speaker),
            contentDescription = "Game",
            modifier = Modifier
                .width(400.dp)
                .scale(0.8f)
        )

        Button(
            onClick = onReadQrCode,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF50C6F),
                contentColor = Color.White
            ),
            modifier = Modifier
                .height(60.dp)
                .width(400.dp)
        ) {
            Text(
                text = "PLAY NOW",
                fontSize = 20.sp
            )
        }

        Button(
            onClick = onSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF50C6F),
                contentColor = Color.White
            ),
            modifier = Modifier
                .padding(top = 20.dp)
                .height(60.dp)
                .width(400.dp)
        ) {
            Text(
                text = "HOW TO PLAY",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        Image(
            painter = painterResource(R.drawable.game2),
            contentDescription = "Game",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .scale(1.15f)
        )

        Text(
            text = "AMINA'S HITSTER",
            color = Color.White,
            fontSize = 26.sp,
            textAlign = TextAlign.Left,
            modifier = Modifier
                .padding(top = 20.dp)
        )


        Text(
            text = "Join the ultimate party powered by Amina's favorite songs. Take turns placing songs in the correct order on your timeline and prove who knows the music best.",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(top = 20.dp)
        )

        Text(
            text = "Turn any night into a party, no DJ needed. Just pick your AMINA'S HITSTER edition and press play. A game for everyone, whether you’re a music expert or just love a good tune. Scan the QR code, place the song in the timeline, and have the time of your life.",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(top = 10.dp)
        )

        Text(
            text = "The first player to collect 10 cards earns the title of AMINA'S HITSTER.",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(top = 10.dp)
        )

        Text(
            text = "THERE IS NO BETTER PLAN THAN A HITSTER PLAN!",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.game2),
            contentDescription = "Game",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .scale(1.15f)
        )

        Image(
            painter = painterResource(R.drawable.speaker),
            contentDescription = "Speaker",
            modifier = Modifier
                .width(400.dp)
                .scale(0.8f)
        )

        // Play / Pause Button with dynamic icon
        Button(
            onClick = onTogglePlayPause,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF50C6F),
                contentColor = Color.White
            ),
            modifier = Modifier
                .height(60.dp)
                .width(400.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) "PAUSE MUSIC" else "RESUME MUSIC",
                    fontSize = 20.sp
                )
            }
        }

        // Scan Next Song Button with QR icon
        Button(
            onClick = onScanNext,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF50C6F),
                contentColor = Color.White
            ),
            modifier = Modifier
                .padding(top = 20.dp)
                .height(60.dp)
                .width(400.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEXT CARD",
                    fontSize = 20.sp
                )
            }
        }
    }
}