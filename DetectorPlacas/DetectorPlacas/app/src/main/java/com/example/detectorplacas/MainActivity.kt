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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * Define la estructura de datos que esperamos recibir desde Cloud Firestore.
 * Mapea un documento de la colección 'vehiculos' a un objeto Kotlin.
 */
data class Propietario(
    val nombre: String = "",
    val modelo: String = "",
    val adeudos: Any? = null // 'Any?' permite que 'adeudos' sea String ("Ninguno") o Número (15000)
)

/**
 * Actividad principal de la aplicación.
 * Es el punto de entrada y se encarga de:
 * 1. Solicitar el permiso de la cámara.
 * 2. Configurar la interfaz de usuario principal con Jetpack Compose.
 */
class MainActivity : ComponentActivity() {

    // --- MANEJO DE PERMISOS ---

    // Variable de estado para saber si el permiso de cámara fue concedido
    private var hasCameraPermission by mutableStateOf(false)

    /**
     * Registra un "launcher" para el contrato de solicitud de permisos.
     * Este objeto maneja la respuesta del usuario (si concedió o denegó el permiso).
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted // Actualiza el estado basado en la respuesta
    }

    /**
     * Verifica si el permiso de cámara ya está concedido.
     * Si está concedido, actualiza `hasCameraPermission` a true.
     * Si no está concedido, lanza el diálogo de solicitud de permiso.
     */
    private fun requestCameraPermission() {
        when (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        )) {
            PackageManager.PERMISSION_GRANTED -> {
                hasCameraPermission = true // El permiso ya estaba concedido
            }
            else -> {
                // El permiso no está concedido, solicitarlo
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // --- CICLO DE VIDA ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission() // Pedir permiso de cámara tan pronto como se crea la app

        enableEdgeToEdge() // Habilita la UI a pantalla completa (edge-to-edge)
        setContent {
            DetectorPlacasTheme {
                // Inicia el sistema de navegación de Compose
                AppNavigation(hasCameraPermission = hasCameraPermission)
            }
        }
    }
}


/**
 * Controlador de Navegación principal de la app (Router).
 * Define las rutas (pantallas) y pasa los argumentos necesarios.
 * @param hasCameraPermission Estado que se pasa a la PantallaScanner para saber si mostrar la cámara o un mensaje de error.
 */
@Composable
fun AppNavigation(hasCameraPermission: Boolean) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "scanner") {
        // Ruta 1: Pantalla de Escaneo (la pantalla principal)
        composable("scanner") {
            PantallaScanner(
                navController = navController,
                hasPermission = hasCameraPermission
            )
        }
        // Ruta 2: Pantalla de Resultados
        // Recibe un argumento "placaId" en la ruta
        composable(
            route = "resultados/{placaId}",
            arguments = listOf(navArgument("placaId") { type = NavType.StringType })
        ) { backStackEntry ->
            // Extrae el argumento "placaId" de la ruta
            val placaId = backStackEntry.arguments?.getString("placaId") ?: "N/A"
            PantallaResultados(
                placaId = placaId,
                navController = navController
            )
        }
    }
}

/**
 * Pantalla principal que muestra la vista de la cámara, un recuadro (retícula)
 * y un botón para escanear.
 * @param navController Controlador para navegar a la pantalla de resultados.
 * @param hasPermission Estado que indica si el permiso de cámara fue concedido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaScanner(navController: NavController, hasPermission: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Controlador de CameraX que gestiona el ciclo de vida de la cámara
    val cameraController = remember { LifecycleCameraController(context) }
    // Executor para correr las tareas de la cámara (como tomar fotos) en un hilo separado
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    // Estado para mostrar/ocultar el indicador de carga
    var estaAnalizando by remember { mutableStateOf(false) }

    // --- Definición de la retícula (recuadro) ---
    // Define el tamaño del recuadro como un porcentaje del total de la imagen
    val reticleWidthFraction = 0.9f  // El recuadro ocupará el 90% del ancho
    val reticleHeightFraction = 0.25f // El recuadro ocupará el 25% del alto

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // Evitar múltiples clics si ya se está analizando
                if (estaAnalizando) return@FloatingActionButton

                estaAnalizando = true // Mostrar indicador de carga

                // --- INICIO DE LÓGICA DE CAPTURA Y ANÁLISIS ---
                // Esta lógica es la más importante del proyecto.

                // 1. Crear un archivo temporal donde se guardará la foto
                val photoFile = File.createTempFile("placa_crop_", ".jpg", context.cacheDir)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                // 2. Tomar la foto y guardarla en el archivo temporal
                cameraController.takePicture(
                    outputOptions,
                    cameraExecutor, // Ejecutar en el hilo de la cámara
                    object : ImageCapture.OnImageSavedCallback {
                        // 3. Callback: Se llama cuando la foto se guardó exitosamente
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val savedUri = outputFileResults.savedUri ?: return

                            try {
                                // 4. Cargar la imagen completa (Bitmap) desde el archivo guardado
                                val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, savedUri))
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, savedUri)
                                }

                                // 5. Calcular las dimensiones del RECORTE
                                // Se usa el mismo porcentaje (fracción) de la retícula de la UI
                                val cropWidth = (originalBitmap.width * reticleWidthFraction).toInt()
                                val cropHeight = (originalBitmap.height * reticleHeightFraction).toInt()
                                val x = (originalBitmap.width - cropWidth) / 2 // Centrar horizontalmente
                                val y = (originalBitmap.height - cropHeight) / 2 // Centrar verticalmente

                                // 6. Crear un NUEVO Bitmap, recortado a la zona de la retícula
                                val croppedBitmap = Bitmap.createBitmap(
                                    originalBitmap,
                                    x,
                                    y,
                                    cropWidth,
                                    cropHeight
                                )

                                // 7. Preparar la imagen para ML Kit
                                // Se usa el bitmap *recortado* para mejorar la precisión y velocidad
                                val image = InputImage.fromBitmap(croppedBitmap, 0)
                                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                                // 8. Procesar la imagen con el reconocedor de texto (IA)
                                recognizer.process(image)
                                    .addOnSuccessListener { visionText ->
                                        // 9. La IA funcionó. Ahora buscamos un texto que parezca placa
                                        val placaEncontrada = buscarPlaca(visionText)

                                        // Volver al hilo principal para actualizar la UI
                                        context.mainExecutor.execute {
                                            if (placaEncontrada != null) {
                                                // ¡Éxito! Navegar a la pantalla de resultados
                                                navController.navigate("resultados/$placaEncontrada")
                                            } else {
                                                Toast.makeText(context, "No se encontró placa en el recuadro", Toast.LENGTH_SHORT).show()
                                            }
                                            estaAnalizando = false // Ocultar indicador de carga
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        // 10. Fallo en la IA
                                        Log.e("PantallaScanner", "Error al procesar texto: ${e.message}", e)
                                        context.mainExecutor.execute {
                                            Toast.makeText(context, "Error al analizar", Toast.LENGTH_SHORT).show()
                                            estaAnalizando = false
                                        }
                                    }
                                    .addOnCompleteListener {
                                        // 11. Limpieza: Borrar archivo y liberar memoria de los Bitmaps
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

                        // Callback: Se llama si falla al *guardar* la foto
                        override fun onError(exception: ImageCaptureException) {
                            Log.e("PantallaScanner", "Error al guardar foto: ${exception.message}", exception)
                            context.mainExecutor.execute {
                                Toast.makeText(context, "Error al tomar la foto", Toast.LENGTH_SHORT).show()
                                estaAnalizando = false
                            }
                        }
                    }
                )
                // --- FIN DE LÓGICA DE CAPTURA ---
            }) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Escanear Placa")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            if (hasPermission) {
                // --- UI DE CÁMARA ---

                // 1. Vista de cámara (fondo)
                // AndroidView es un "puente" para usar Vistas de Android (no-Compose)
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            this.controller = cameraController
                        }
                        // Vincula el controlador de la cámara al ciclo de vida de la pantalla
                        cameraController.bindToLifecycle(lifecycleOwner)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 2. UI de overlay (encima de la cámara)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp), // Espacio para el botón FAB
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Texto de instrucción
                    Text(
                        text = "Alinea la placa en el recuadro",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f)) // Fondo semitransparente
                            .padding(8.dp)
                    )

                    // El recuadro (Retícula)
                    // Esta es la guía visual para el usuario
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(reticleWidthFraction) // 90% ancho
                            .height((LocalContext.current.resources.displayMetrics.heightPixels / LocalContext.current.resources.displayMetrics.density * reticleHeightFraction).dp) // 25% alto
                            .border(BorderStroke(4.dp, Color.White))
                    )
                }

            } else {
                // --- UI DE PERMISO DENEGADO ---
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Permiso de cámara denegado.")
                    Text("Por favor, habilita el permiso en la configuración de la app.")
                }
            }

            // --- UI DE CARGA ---
            // Indicador de carga (encima de todo)
            if (estaAnalizando) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Pantalla que muestra los resultados de la búsqueda en Firestore.
 * @param placaId La placa (String) que se reconoció y se usará para buscar.
 * @param navController Controlador para navegar de regreso al scanner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaResultados(placaId: String, navController: NavController) {

    // --- LÓGICA DE ESTADO ---
    // Guardamos el estado de la búsqueda en la BD
    var propietario by remember { mutableStateOf<Propietario?>(null) } // Almacena los datos del dueño
    var estadoBusqueda by remember { mutableStateOf("cargando") } // "cargando", "exito", "no_encontrado", "error"

    // --- LÓGICA DE BÚSQUEDA EN FIREBASE ---
    // LaunchedEffect se ejecuta UNA SOLA VEZ cuando la pantalla aparece (o si 'placaId' cambia)
    LaunchedEffect(placaId) {
        // 1. Obtener la instancia de la base de datos
        val db = Firebase.firestore

        // 2. Buscar en la colección "vehiculos" un documento cuyo ID sea la placa
        db.collection("vehiculos").document(placaId)
            .get() // Ejecutar la consulta
            .addOnSuccessListener { document ->
                // 3. Consulta exitosa
                if (document.exists()) {
                    // 4. ¡Éxito! El documento existe.
                    // Se convierte el documento de Firestore al objeto 'Propietario'
                    propietario = document.toObject(Propietario::class.java)
                    estadoBusqueda = "exito"
                } else {
                    // 5. No se encontró el documento (placa no registrada)
                    estadoBusqueda = "no_encontrado"
                }
            }
            .addOnFailureListener {
                // 6. Error al conectar con Firebase
                Log.e("PantallaResultados", "Error al buscar en Firestore", it)
                estadoBusqueda = "error"
            }
    }

    // --- UI DE RESULTADOS ---
    Scaffold(
        topBar = {
            // Barra superior con título y botón de "atrás"
            TopAppBar(
                title = { Text("Resultados de la Placa") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { // Regresa a la pantalla anterior
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Placa Escaneada:",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = placaId,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Divider() // Línea divisoria
            Spacer(modifier = Modifier.height(24.dp))

            // Lógica de UI que reacciona al estado de la búsqueda
            when (estadoBusqueda) {
                "cargando" -> {
                    // Muestra un indicador de carga mientras busca
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Buscando en la base de datos...", modifier = Modifier.padding(top = 16.dp))
                    }
                }
                "exito" -> {
                    // Muestra los datos encontrados
                    propietario?.let { datos ->
                        Text("Datos del Propietario:", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Nombre
                        Text("Nombre:", style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray))
                        Text(datos.nombre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))

                        // Modelo
                        Text("Modelo:", style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray))
                        Text(datos.modelo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))

                        // Adeudos
                        Text("Adeudos:", style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray))
                        // Esta lógica revisa si 'adeudos' es un String, Número, etc.
                        val adeudosTexto = when (val adeudosValor = datos.adeudos) {
                            is String -> adeudosValor // Si es "Ninguno"
                            is Long -> "%,d".format(adeudosValor) // Si es 15000, formatea a "15,000"
                            is Double -> "%,.2f".format(adeudosValor) // Si tiene decimales
                            null -> "N/A"
                            else -> adeudosValor.toString() // Otro caso
                        }
                        Text(adeudosTexto, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                "no_encontrado" -> {
                    // Mensaje de error si no se encuentra el dato en la BD
                    Text("Placa no encontrada en la base de datos.", color = Color.Red)
                }
                "error" -> {
                    // Mensaje de error si no se puede conectar con la BD
                    Text("Error al conectar con la base de datos.", color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Empuja el botón hacia abajo

            Button(
                onClick = { navController.popBackStack() }, // Regresa al scanner
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
            ) {
                Text("Escanear de Nuevo")
            }
        }
    }
}

/**
 * Función de ayuda para "limpiar" el texto reconocido por ML Kit.
 * Busca el texto que coincida con el formato de una placa.
 * @param visionText El objeto 'Text' que devuelve ML Kit.
 * @return Un String con la placa (ej. "VLA551A") o `null` si no se encontró.
 */
private fun buscarPlaca(visionText: Text): String? {
    // Expresión Regular (Regex) para una placa:
    // ^ -> Inicio del texto
    // [A-Z0-9] -> Letras de A a la Z o números del 0 al 9
    // {6,8} -> Que tenga entre 6 y 8 caracteres de longitud
    // $ -> Fin del texto
    val regexPlaca = Regex("^[A-Z0-9]{6,8}$")

    for (block in visionText.textBlocks) {
        // Limpiamos el texto: quitamos espacios, saltos de línea, guiones y lo ponemos en MAYÚSCULAS
        val textoLimpio = block.text.replace(Regex("[\\s\\n-]"), "").uppercase()

        // Comprobamos si el texto limpio coincide con nuestro formato de placa
        if (regexPlaca.matches(textoLimpio)) {
            return textoLimpio // ¡Encontrado! Devolvemos la placa limpia
        }
    }
    return null // No se encontró ningún texto que pareciera placa
}


/**
 * Función de previsualización para Jetpack Compose.
 * Muestra cómo se ve la PantallaResultados sin tener que ejecutar la app.
 */
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DetectorPlacasTheme {
        // Muestra la pantalla de resultados con datos de ejemplo
        PantallaResultados(placaId = "ABC1234", navController = rememberNavController())
    }
}