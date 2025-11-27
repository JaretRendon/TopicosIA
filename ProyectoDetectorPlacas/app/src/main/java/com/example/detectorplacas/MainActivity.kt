package com.example.detectorplacas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.detectorplacas.ui.theme.DetectorPlacasTheme

// ML KIT
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

//  Recorte (Crop)
import java.io.File
import java.util.concurrent.Executors

// Firebase Firestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


/**
 * --- MODELOS DE BASE DE DATOS (RELACIONAL) ---
 */

// 1. Datos que vienen de la colección 'vehiculos'
data class VehiculoBD(
    val marca: String = "",
    val modelo: String = "",
    val color: String = "",
    val año: Any? = null,
    val adeudos: Any? = null,
    val propietario_id: String = ""
)

// 2. Datos que vienen de la colección 'propietarios'
data class PropietarioBD(
    val nombre: String = ""
)

// 3. Objeto final para mostrar en la UI
data class ResultadoFinal(
    val nombrePropietario: String,
    val marca: String,
    val modelo: String,
    val color: String,
    val año: String,
    val adeudos: Any?
)

class MainActivity : ComponentActivity() {

    // Los permisos necesarios, como acceso a la cámara
    private var hasCameraPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
    }

    private fun requestCameraPermission() {
        when (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        )) {
            PackageManager.PERMISSION_GRANTED -> {
                hasCameraPermission = true
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCameraPermission()
        enableEdgeToEdge()
        setContent {
            DetectorPlacasTheme {
                AppNavigation(hasCameraPermission = hasCameraPermission)
            }
        }
    }
}


@Composable
fun AppNavigation(hasCameraPermission: Boolean) {
    val navController = rememberNavController()
    // Scanner es usado para leer la placa
    NavHost(navController = navController, startDestination = "scanner") {
        composable("scanner") {
            PantallaScanner(
                navController = navController,
                hasPermission = hasCameraPermission
            )
        }
        // Jala los resultados de la búsqueda en la BD
        composable(
            route = "resultados/{placaId}",
            arguments = listOf(navArgument("placaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val placaId = backStackEntry.arguments?.getString("placaId") ?: "N/A"
            PantallaResultados(
                placaId = placaId,
                navController = navController
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Pantalla donde se usa el scanner
fun PantallaScanner(navController: NavController, hasPermission: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Controladores de la cámara
    val cameraController = remember { LifecycleCameraController(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var estaAnalizando by remember { mutableStateOf(false) }

    val reticleWidthFraction = 0.9f
    val reticleHeightFraction = 0.25f

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (estaAnalizando) return@FloatingActionButton

                estaAnalizando = true

                val photoFile = File.createTempFile("placa_crop_", ".jpg", context.cacheDir)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                cameraController.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val savedUri = outputFileResults.savedUri ?: return

                            try {
                                val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, savedUri))
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, savedUri)
                                }

                                val cropWidth = (originalBitmap.width * reticleWidthFraction).toInt()
                                val cropHeight = (originalBitmap.height * reticleHeightFraction).toInt()
                                val x = (originalBitmap.width - cropWidth) / 2
                                val y = (originalBitmap.height - cropHeight) / 2

                                val croppedBitmap = Bitmap.createBitmap(
                                    originalBitmap,
                                    x,
                                    y,
                                    cropWidth,
                                    cropHeight
                                )

                                val image = InputImage.fromBitmap(croppedBitmap, 0)
                                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                                recognizer.process(image)
                                    .addOnSuccessListener { visionText ->
                                        val placaEncontrada = buscarPlaca(visionText)

                                        context.mainExecutor.execute {
                                            if (placaEncontrada != null) {
                                                navController.navigate("resultados/$placaEncontrada")
                                            } else {
                                                Toast.makeText(context, "No se encontró placa en el recuadro", Toast.LENGTH_SHORT).show()
                                            }
                                            estaAnalizando = false
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("PantallaScanner", "Error al procesar texto: ${e.message}", e)
                                        context.mainExecutor.execute {
                                            Toast.makeText(context, "Error al analizar", Toast.LENGTH_SHORT).show()
                                            estaAnalizando = false
                                        }
                                    }
                                    .addOnCompleteListener {
                                        photoFile.delete()
                                        originalBitmap.recycle()
                                        croppedBitmap.recycle()
                                    }

                            } catch (e: Exception) {
                                Log.e("PantallaScanner", "Error al recortar imagen: ${e.message}", e)
                                context.mainExecutor.execute {
                                    Toast.makeText(context, "Error al procesar la foto", Toast.LENGTH_SHORT).show()
                                    estaAnalizando = false
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("PantallaScanner", "Error al guardar foto: ${exception.message}", exception)
                            context.mainExecutor.execute {
                                Toast.makeText(context, "Error al tomar la foto", Toast.LENGTH_SHORT).show()
                                estaAnalizando = false
                            }
                        }
                    }
                )
            }) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Escanear Placa")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            if (hasPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            this.controller = cameraController
                        }
                        cameraController.bindToLifecycle(lifecycleOwner)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Lógica que pide alinear la placa en el recuadro
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Alinea la placa en el recuadro",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(reticleWidthFraction)
                            .height((LocalContext.current.resources.displayMetrics.heightPixels / LocalContext.current.resources.displayMetrics.density * reticleHeightFraction).dp)
                            .border(BorderStroke(4.dp, Color.White))
                    )
                }

            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Permiso de cámara denegado.")
                    Text("Por favor, habilita el permiso en la configuración de la app.")
                }
            }

            // Muestra un mensaje que indica que está procesando la imagen a detectar
            if (estaAnalizando) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaResultados(placaId: String, navController: NavController) {
    var resultado by remember { mutableStateOf<ResultadoFinal?>(null) }
    var estadoBusqueda by remember { mutableStateOf("cargando") }

    // --- LÓGICA DE DOBLE CONSULTA (RELACIONAL) ---
    LaunchedEffect(placaId) {
        val db = Firebase.firestore

        db.collection("vehiculos").document(placaId).get()
            .addOnSuccessListener { docVehiculo ->
                if (docVehiculo.exists()) {
                    val datosVehiculo = docVehiculo.toObject(VehiculoBD::class.java)

                    if (datosVehiculo != null && datosVehiculo.propietario_id.isNotEmpty()) {
                        db.collection("propietarios").document(datosVehiculo.propietario_id).get()
                            .addOnSuccessListener { docPropietario ->
                                if (docPropietario.exists()) {
                                    val datosPropietario = docPropietario.toObject(PropietarioBD::class.java)

                                    resultado = ResultadoFinal(
                                        nombrePropietario = datosPropietario?.nombre ?: "Desconocido",
                                        marca = datosVehiculo.marca,
                                        modelo = datosVehiculo.modelo,
                                        color = datosVehiculo.color,
                                        año = datosVehiculo.año.toString(),
                                        adeudos = datosVehiculo.adeudos
                                    )
                                    estadoBusqueda = "exito"
                                } else {
                                    estadoBusqueda = "error"
                                }
                            }
                            .addOnFailureListener { estadoBusqueda = "error" }
                    } else {
                        estadoBusqueda = "error"
                    }
                } else {
                    estadoBusqueda = "no_encontrado"
                }
            }
            .addOnFailureListener { estadoBusqueda = "error" }
    }

    // Interfaz de la pantalla de resultados
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Vehículo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEFF2F7)
                )
            )
        },
        containerColor = Color(0xFFEFF2F7)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Tarjeta de la placa
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(3.dp, Color.Black)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("MÉXICO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = placaId,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF333333),
                        letterSpacing = 4.sp
                    )
                    Text("TRANSPORTE PRIVADO", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            when (estadoBusqueda) {
                "cargando" -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Consultando bases de datos...")
                }
                "no_encontrado" -> {
                    // Mensaje en caso de no encontrar la placa en la BD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), // rojito claro
                        border = BorderStroke(2.dp, Color(0xFFE53935)), // rojito fuerte
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("MATRÍCULA NO ENCONTRADA", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                            Text("La placa no existe en la base de datos.", style = MaterialTheme.typography.bodyMedium, color = Color.Black.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                        }
                    }
                }
                // Mensajes de error si no se encuentra la placa en la BD
                "error" -> {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                    Text("Error de datos", color = Color.Red)
                    Text("No se pudo vincular el propietario.", color = Color.Gray, fontSize = 12.sp)
                }
                // Mensajes de éxito si se encuentra la placa en la BD
                "exito" -> {
                    resultado?.let { datos ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {

                                // Sección propietario
                                Text("PROPIETARIO", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                InfoRow(icon = Icons.Default.Person, label = "Nombre Completo", value = datos.nombrePropietario)

                                Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.LightGray.copy(alpha = 0.5f))

                                // Sección datos del auto
                                Text("VEHÍCULO", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        InfoRow(icon = Icons.Default.DirectionsCar, label = "Marca", value = datos.marca)
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        InfoRow(icon = Icons.Default.Label, label = "Modelo", value = datos.modelo)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        InfoRow(icon = Icons.Default.CalendarToday, label = "Año", value = datos.año)
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        InfoRow(icon = Icons.Default.Palette, label = "Color", value = datos.color)
                                    }
                                }

                                Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.LightGray.copy(alpha = 0.5f))

                                // Sección de adeudos
                                val (textoAdeudo, colorAdeudo) = when (val a = datos.adeudos) {
                                    is String -> {
                                        if (a.equals("Ninguno", ignoreCase = true) || a == "0")
                                            Pair("Sin Adeudo", Color(0xFF4CAF50)) // Verde
                                        else
                                            Pair("$ $a", Color(0xFFE53935)) // Rojo
                                    }
                                    is Number -> {
                                        if (a.toDouble() > 0) Pair("$ ${a}", Color(0xFFE53935)) // Rojo
                                        else Pair("Sin Adeudo", Color(0xFF4CAF50)) // Verde
                                    }
                                    else -> Pair("N/A", Color.Gray)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = colorAdeudo)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Estado de Adeudo", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                        Text(
                                            text = textoAdeudo,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = colorAdeudo,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Botón para volver a escanear
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Escanear Nuevo Vehículo", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Función para buscar y reconocer automáticamente la placa dentro del texto de la imagen
private fun buscarPlaca(visionText: Text): String? {
    val regexPlaca = Regex("^[A-Z0-9]{6,8}$") // Formato que debe tener la placa
    for (block in visionText.textBlocks) {
        val textoLimpio = block.text.replace(Regex("[\\s\\n-]"), "").uppercase()
        if (regexPlaca.matches(textoLimpio)) {
            return textoLimpio
        }
    }
    return null
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DetectorPlacasTheme {
        PantallaResultados(placaId = "ABC1234", navController = rememberNavController())
    }
}