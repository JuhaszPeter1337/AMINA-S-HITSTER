package com.example.aminashitster

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import androidx.core.net.toUri
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign

class MainActivity : ComponentActivity() {

    var currentScreen by mutableStateOf("menu")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (currentScreen == "menu") {
                        MainMenu(
                            onReadQrCode = {
                                startQrScanner()
                            },
                            onSettings = {
                                currentScreen = "settings"
                            }
                        )
                    } else {
                        SettingsScreen(
                            onBack = {
                                currentScreen = "menu"
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startQrScanner() {

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE
            )
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(
            this,
            options
        )

        scanner.startScan()
            .addOnSuccessListener { barcode ->

                val value = barcode.rawValue

                if (!value.isNullOrEmpty()) {
                    openUrl(value)
                }
            }
    }

    private fun openUrl(value: String) {

        if (
            value.startsWith("https://") ||
            value.startsWith("http://")
        ) {

            val intent = Intent(
                Intent.ACTION_VIEW,
                value.toUri()
            )

            startActivity(intent)
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
                .width(400.dp)
                .padding(top = 50.dp)
                .scale(1.15f)
        )

        Image(
            painter = painterResource(R.drawable.speaker),
            contentDescription = "Game",
            modifier = Modifier
                .width(400.dp)
                .padding(top = 100.dp)
                .scale(1.5f)
        )

        Button(
            onClick = onReadQrCode,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF50C6F),
                contentColor = Color.White
            ),
            modifier = Modifier
                .padding(top = 110.dp)
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
                text = "SETTINGS",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
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
                .width(400.dp)
                .padding(top = 50.dp)
                .scale(1.15f)
        )

        Text(
            text = "Join the ultimate party powered by Amina's favorite songs. Take turns placing songs in the correct order on your timeline and prove who knows the music best.",
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(top = 40.dp)
        )

        Text(
            text = "Turn any night into a party, no DJ needed. Just pick your AMINA'S HITSTER edition and press play. A game for everyone, whether you’re a music expert or just love a good tune. Scan the QR code, place the song in the timeline, and have the time of your life.",
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(top = 10.dp)
        )

        Text(
            text = "The first player to collect 10 cards earns the title of AMINA'S HITSTER.",
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(top = 10.dp)
        )

        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF50C6F),
                contentColor = Color.White
            ),
            modifier = Modifier
                .padding(top = 45.dp)
                .height(60.dp)
                .width(400.dp)
        ) {
            Text(
                text = "BACK",
                fontSize = 20.sp
            )
        }
    }
}