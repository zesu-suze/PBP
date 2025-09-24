package com.example.pokeballplus

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.bluetooth.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.exitProcess
import android.widget.Toast
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.BluetoothLeScanner


// 🟢 Clase principal de la app: pantalla principal con BLE y accesibilidad
class MainActivity : AppCompatActivity() {
    // VARIABLES

    private lateinit var openSettingsButton: Button
    private lateinit var bleStatusText: TextView
    private lateinit var scanResultText: TextView
    private lateinit var topButtonStatus: TextView
    private lateinit var batteryLevelText: TextView
    private lateinit var joystickButtonStatus: TextView
    private lateinit var joystickPosition: TextView
    private lateinit var joystickDot: View
    private lateinit var joystickMap: FrameLayout
    private lateinit var gyroData: TextView
    private lateinit var accelData: TextView
    private lateinit var orientationAngles: TextView
    private lateinit var gyroImage: ImageView

    private var rollFiltered = 0.0
    private var pitchFiltered = 0.0
    private var yawFiltered = 0.0

    private var lastAccelX = 0.0
    private var lastAccelY = 0.0
    private var lastAccelZ = 0.0
    private var lastShakeTime = 0L

    private var bluetoothGatt: BluetoothGatt? = null
    private var currentScanCallback: ScanCallback? = null


    // 🔧 Se ejecuta cuando la app se inicia. Configura la interfaz, botones, permisos y BLE.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        openSettingsButton = findViewById(R.id.openSettingsButton)

        // Ir a ajustes de accesibilidad
        openSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val btnCheckAccessibility = findViewById<Button>(R.id.btnCheckAccessibility)
        val tvAccessibilityStatus = findViewById<TextView>(R.id.tvAccessibilityStatus)

        btnCheckAccessibility.setOnClickListener {
            val isEnabled = isAccessibilityServiceEnabled()
            val hasLocationPermission =
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED

            val accesibilidad = if (isEnabled) {
                "✅ Accessibility service active"
            } else {
                "❌ Accessibility service disabled"
            }

            val ubicacion = if (hasLocationPermission) {
                "✅ Location permission granted"
            } else {
                "⚠️ No location permission"
            }


            tvAccessibilityStatus.text = "$accesibilidad\n$ubicacion"
        }



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
                ),
                100
            )
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ),
                101
            )
        }

        bleStatusText = findViewById(R.id.bleStatusText)
        checkBlePermission()

        val connectButton = findViewById<Button>(R.id.connectButton)
        connectButton.setOnClickListener {
            connectToPokeball()
        }

        scanResultText = findViewById(R.id.scanResultText)
        batteryLevelText = findViewById(R.id.batteryLevelText)
        topButtonStatus = findViewById(R.id.topButtonStatus)
        joystickButtonStatus = findViewById(R.id.joystickButtonStatus)
        joystickPosition = findViewById(R.id.joystickPosition)
        joystickDot = findViewById(R.id.joystickDot)
        joystickMap = findViewById(R.id.joystickMap)
        gyroData = findViewById(R.id.gyroData)
        orientationAngles = findViewById(R.id.orientationAngles)
        accelData = findViewById(R.id.accelData)
        gyroImage = findViewById(R.id.gyroImage)

        val configureTapButton = findViewById<Button>(R.id.configureTapButton)
        configureTapButton.setOnClickListener {
            Log.d("MainActivity", "BOTÓN: Voy a abrir ConfigureTapActivity")
            val intent = Intent(this, ConfigureTapActivity::class.java)
            startActivity(intent)
            Log.d("MainActivity", "BOTÓN: ConfigureTapActivity abierta")

        }

        val disconnectButton = findViewById<Button>(R.id.disconnectButton)
        val exitAppButton = findViewById<Button>(R.id.exitAppButton)

        disconnectButton.setOnClickListener {
            disconnectPokeball()
        }


        exitAppButton.setOnClickListener {
            finishAffinity() // Cierra todas las actividades
            exitProcess(0)   // Termina el proceso
        }


    }

    private fun disconnectPokeball() {
        try {
            currentScanCallback?.let { cb ->
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothManager.adapter.bluetoothLeScanner?.stopScan(cb)
                currentScanCallback = null
                Log.d("BLE_DEBUG", "⏹️ Scan manually stopped")
            }
        } catch (e: Exception) {
            Log.w("BLE_DEBUG", "⚠️ Error stopping scan: ${e.message}")
        }

        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w("BLE_DEBUG", "⚠️ Error closing GATT: ${e.message}")
            }
        }

        bluetoothGatt = null

        runOnUiThread { scanResultText.text = "🔌 Poké Ball disconnected" }
    }


    // ✅ Comprueba si el servicio de accesibilidad de la app está activado por el usuario
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (service in services) {
            if (service.resolveInfo.serviceInfo.packageName == packageName &&
                service.resolveInfo.serviceInfo.name == MyAccessibilityService::class.java.name) {
                return true
            }
        }
        return false
    }

    // ✅ Verifica si los permisos necesarios para usar Bluetooth están concedidos
    private fun checkBlePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        bleStatusText.text = if (hasPermission) {
            "✅ BLE permissions granted"
        } else {
            "⚠️ BLE permissions not granted"
        }

    }



    // 🔌 Se conecta al dispositivo Poké Ball Plus mediante BLE, usando la dirección MAC FUNCIONAAAA
    // Requiere API 26+ (Android 8)
    @RequiresApi(Build.VERSION_CODES.O)
    private fun connectToPokeball() {
        // Si había una conexión previa, límpiala
        bluetoothGatt?.close()
        bluetoothGatt = null

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        //val device = bluetoothAdapter.getRemoteDevice(macAddress)

        scanResultText.text = "🔌 Connecting to Poké Ball Plus..."
        //Log.d("BLE_DEBUG", "🔍 Intentando conectar con MAC: $macAddress")

        val gattCallback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d("BLE_DEBUG", "📡 Estado de conexión cambiado: status=$status, newState=$newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread {
                        scanResultText.text = "✅ Connected. Searching sevices..."
                    }
                    Log.d("BLE_DEBUG", "✅ Conexión establecida. Iniciando discoverServices()")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread {
                        scanResultText.text = "❌ Disconnected"
                    }
                    Log.e("BLE_DEBUG", "❌ Conexión perdida con el dispositivo")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.d("BLE_DEBUG", "🔍 Servicios descubiertos con status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS) return

                for (service in gatt.services) {
                    Log.d("BLE_DEBUG", "🔧 Servicio encontrado: ${service.uuid}")
                    for (characteristic in service.characteristics) {
                        Log.d("BLE_DEBUG", "   └📌 Característica: ${characteristic.uuid}")
                    }
                }

                // UUID de batería
                val batteryService = gatt.getService(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))
                batteryService?.getCharacteristic(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"))?.let { batteryChar ->
                    gatt.readCharacteristic(batteryChar)
                    Log.d("BLE_DEBUG", "🔋 Leyendo nivel de batería...")
                }

                // UUID del servicio y característica de input
                val inputService = gatt.getService(UUID.fromString("6675e16c-f36d-4567-bb55-6b51e27a23e5"))
                val inputChar = inputService?.getCharacteristic(UUID.fromString("6675e16c-f36d-4567-bb55-6b51e27a23e6"))

                if (inputChar != null) {
                    val success = gatt.setCharacteristicNotification(inputChar, true)
                    Log.d("BLE_DEBUG", "✅ Notificaciones activadas para INPUT: $success")

                    val descriptor = inputChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        Handler(Looper.getMainLooper()).postDelayed({
                            val writeSuccess = gatt.writeDescriptor(descriptor)
                            Log.d("BLE_DEBUG", "📎 Escritura del descriptor CCCD tras delay: $writeSuccess")
                        }, 200) // Espera 200ms

                    } else {
                        Log.e("BLE_DEBUG", "❌ Descriptor CCCD no encontrado para INPUT")
                    }
                } else {
                    Log.e("BLE_DEBUG", "❌ Característica INPUT no encontrada")
                }

            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                val uuid = descriptor.characteristic.uuid
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE_DEBUG", "✅ Descriptor CCCD escrito correctamente para $uuid")
                } else {
                    Log.e("BLE_DEBUG", "❌ Error al escribir descriptor CCCD para $uuid, status=$status")
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                Log.d("BLE_DEBUG", "📥 onCharacteristicRead: ${characteristic.uuid}, status=$status")
                if (characteristic.uuid == UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")) {
                    val batteryLevel = characteristic.value[0].toInt()
                    Log.d("BLE_DEBUG", "🔋 Nivel de batería leído: $batteryLevel%")
                    runOnUiThread {
                        batteryLevelText.text = "🔋 Battery: $batteryLevel%"
                    }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val data = characteristic.value

                if (data.size < 12) {
                    Log.w("BLE_DEBUG", "⚠️ Notificación ignorada: longitud insuficiente (${data.size})")
                    return
                }

                val buttonByte = data[1].toInt()
                val topPressed = (buttonByte and 0x01) != 0
                val joystickPressed = (buttonByte and 0x02) != 0

                val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                val x = prefs.getFloat("tap_x", 500f)
                val y = prefs.getFloat("tap_y", 500f)

                /**
                fun quaternionToEuler(qX: Double, qY: Double, qZ: Double, qW: Double): Triple<Double, Double, Double> {
                    val x = qX.coerceIn(-1.0, 1.0)
                    val y = qY.coerceIn(-1.0, 1.0)
                    val z = qZ.coerceIn(-1.0, 1.0)
                    val w = qW.coerceIn(-1.0, 1.0)

                    val sinr_cosp = 2.0 * (w * x + y * z)
                    val cosr_cosp = 1.0 - 2.0 * (x * x + y * y)
                    val roll = Math.toDegrees(Math.atan2(sinr_cosp, cosr_cosp))

                    var t = 2.0 * (w * y - z * x)
                    t = t.coerceIn(-1.0, 1.0)
                    val pitch = Math.toDegrees(Math.asin(t))

                    val siny_cosp = 2.0 * (w * z + x * y)
                    val cosy_cosp = 1.0 - 2.0 * (y * y + z * z)
                    val yaw = Math.toDegrees(Math.atan2(siny_cosp, cosy_cosp))

                    return Triple(pitch, yaw, roll)
                }
                // Gyroscope
                fun bytesToShortLE(b1: Byte, b2: Byte): Int {
                    return ByteBuffer.wrap(byteArrayOf(b1, b2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                }
                // Gyroscope: leer valores sin escalar (como Int)
                val rawgX = bytesToShortLE(data[6], data[7])
                val rawgY = bytesToShortLE(data[8], data[9])
                val rawZ = bytesToShortLE(data[10], data[11])
                val rawW = bytesToShortLE(data[12], data[13])

// Escalado provisional
                val scaleFactor = 32000.0  // 🔁 prueba 4000.0, 5600.0, 8000.0, etc.

                val gyroX = rawgX / scaleFactor
                val gyroY = rawgY / scaleFactor
                val gyroZ = rawZ / scaleFactor
                val gyroW = rawW / 32767.0


// Normalización
                val norm = Math.sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ + gyroW * gyroW)
                val qXn = gyroX / norm
                val qYn = gyroY / norm
                val qZn = gyroZ / norm
                val qWn = gyroW / norm

// Conversión a ángulos de Euler
                val (pitch, yaw, roll) = quaternionToEuler(qXn, qYn, qZn, qWn)

// Mostrar en la UI
                gyroData.text = String.format("Gyro: X=%.2f, Y=%.2f, Z=%.2f, W=%.2f", qXn, qYn, qZn, qWn)
                orientationAngles.text = String.format("Pitch: %.2f°, Yaw: %.2f°, Roll: %.2f°", pitch, yaw, roll)

// Log para debug visual
                Log.d("GYRO_DEBUG", String.format(
                    "Raw gyro: X=%.4f Y=%.4f Z=%.4f W=%.4f | Euler -> Pitch=%.2f° Yaw=%.2f° Roll=%.2f°",
                    qXn, qYn, qZn, qWn, pitch, yaw, roll
                ))

// Log del valor crudo para saber entre qué valores se mueve
                Log.d("GYRO_RAW_RANGE", "X=$rawgX Y=$rawgY Z=$rawZ W=$rawW")

**/

                // Accelerometer (últimos 3 bytes)
                val accelZ = data[data.size - 3].toInt() / 127.0
                val accelY = data[data.size - 2].toInt() / 127.0
                val accelX = data[data.size - 1].toInt() / 127.0

                accelData.text = String.format("Accel: X=%.2f, Y=%.2f, Z=%.2f", accelX, accelY, accelZ)


                // Constantes reales de calibración
                val JOY_X_MIN = 7980
                val JOY_X_MAX = 49620
                val JOY_Y_MIN = 13350
                val JOY_Y_MAX = 50280

                // Leer X reordenando como hace tu código Python (data[3] + data[2])
                val xBytes = data.sliceArray(2..3)
                val hex = xBytes.joinToString("") { "%02x".format(it) } // string como "a1b2"
                val reorderedHex = "${hex[3]}${hex[0]}${hex[2]}${hex[1]}" // igual que Python
                val rawX = reorderedHex.toInt(16)
                val joyX = 2 * ((rawX - JOY_X_MIN).toFloat() / (JOY_X_MAX - JOY_X_MIN)) - 1


                // Leer Y como un short LE desde data[4..5]
                // Igual que X: reordenar Y manualmente
                val yBytes = data.sliceArray(4..5)
                val hexY = yBytes.joinToString("") { "%02x".format(it) } // string como "34ab"
                val rawY = hexY.toInt(16)
                val joyY = -2 * ((rawY - JOY_Y_MIN).toFloat() / (JOY_Y_MAX - JOY_Y_MIN)) + 1


                runOnUiThread {
                    topButtonStatus.text =
                        if (topPressed) "🎮 TOP button pressed" else "🟢 TOP button released"
                    joystickButtonStatus.text =
                        if (joystickPressed) "🔘 Joystick button: PRESSED" else "⚪ Joystick button: RELEASED"
                    joystickPosition.text = String.format("🕹️ Joystick: X=%.2f, Y=%.2f", joyX, joyY)

                    /**
                    gyroData.text = String.format(
                        "Gyro: X=%.2f, Y=%.2f, Z=%.2f, W=%.2f",
                        gyroX,
                        gyroY,
                        gyroZ,
                        gyroW
                    )
                    accelData.text =
                        String.format("Accel: X=%.2f, Y=%.2f, Z=%.2f", accelX, accelY, accelZ)

                    // 🔁 Calcular ángulos desde acelerómetro
                    val accelNorm = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
                    val ax = accelX / accelNorm
                    val ay = accelY / accelNorm
                    val az = accelZ / accelNorm

                    val rollFromAccel = Math.toDegrees(Math.atan2(ay, az))
                    val pitchFromAccel =
                        Math.toDegrees(Math.atan2(-ax, Math.sqrt(ay * ay + az * az)))

                    // 🔁 Filtro complementario
                    val alpha = 0.98
                    rollFiltered = alpha * roll + (1 - alpha) * rollFromAccel
                    pitchFiltered = alpha * pitch + (1 - alpha) * pitchFromAccel
                    yawFiltered = yaw // no podemos filtrar yaw sin magnetómetro

                    // Mostrar ángulos fusionados
                    orientationAngles.text = "Pitch: %.1f°, Yaw: %.1f°, Roll: %.1f°".format(
                        pitchFiltered,
                        yawFiltered,
                        rollFiltered
                    )

                    // 🔁 Aplicar rotación completa en los tres ejes
                    gyroImage.rotationX =
                        pitchFiltered.toFloat()   // inclinación hacia adelante/atrás
                    //gyroImage.rotationY = yawFiltered.toFloat()     // giro horizontal
                    gyroImage.rotation = rollFiltered.toFloat()     // giro axial sobre sí mismo

                    **/
                }




                // Mueve el punto visual
                val mapSize = joystickMap.width
                val dotSize = joystickDot.width
                if (mapSize > 0 && dotSize > 0) {
                    val center = (mapSize - dotSize) / 2f
                    val posX = center + center * joyX
                    val posY = center - center * joyY // Invertido porque en pantalla Y+ es hacia abajo

                    joystickDot.translationX = posX
                    joystickDot.translationY = posY
                }

                val intent = Intent("POKEBALL_INPUT")
                intent.putExtra("topPressed", topPressed)
                sendBroadcast(intent)



                if (topPressed) {
                    Log.d("MyAccessibilityService", "Top pressed, sending A button")
                    MyAccessibilityService.instance?.simulateTap(x, y)
                }

                if (joystickPressed) {
                    val joystickX = prefs.getFloat("joystick_x", 500f)
                    val joystickY = prefs.getFloat("joystick_y", 500f)
                    Log.d("MyAccessibilityService", "Joystick pressed, simulating tap at ($joystickX, $joystickY)")
                    MyAccessibilityService.instance?.simulateTap(joystickX, joystickY)
                }


                val joyThreshold = 0.2f // para ignorar movimiento mínimo
                val joyMagnitude = kotlin.math.sqrt(joyX * joyX + joyY * joyY)

                if (joyMagnitude > joyThreshold) {
                    val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                    val centerX = prefs.getFloat("joystick_center_x", 500f)
                    val centerY = prefs.getFloat("joystick_center_y", 500f)
                    val intensity = 200f  // distancia máxima del toque

                    val targetX = centerX + joyX * intensity
                    val targetY = centerY - joyY * intensity

                    Log.d("Joystick", "🕹️ Mover a ($targetX, $targetY) desde centro ($centerX, $centerY)")

                    MyAccessibilityService.instance?.simulateTap(targetX, targetY)
                }

                // 1. Calcular el cambio respecto a la lectura anterior
                val deltaX = Math.abs(accelX - lastAccelX)
                val deltaY = Math.abs(accelY - lastAccelY)
                val deltaZ = Math.abs(accelZ - lastAccelZ)

// 2. Guardar esta lectura como la última
                lastAccelX = accelX
                lastAccelY = accelY
                lastAccelZ = accelZ

// 3. Lógica de sacudida refinada
                val now = System.currentTimeMillis()
                val shakeDeltaThreshold = 0.25  // Ajustable según sensibilidad (empieza aquí)
                val countAboveThreshold = listOf(deltaX, deltaY, deltaZ).count { it > shakeDeltaThreshold }

// 4. Si hay suficientes ejes con cambio fuerte y ha pasado al menos 1s desde la última vez...
                if (countAboveThreshold >= 2 && now - lastShakeTime > 1000) {
                    lastShakeTime = now

                    Log.d("SHAKE", "💥 Sacudida REAL detectada: ΔX=$deltaX ΔY=$deltaY ΔZ=$deltaZ")

                    // 5. Recuperar coordenadas guardadas desde la pantalla de configuración
                    val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                    val shakeX = prefs.getFloat("shake_x", 500f)
                    val shakeY = prefs.getFloat("shake_y", 500f)

                    // 6. Lanzar el gesto virtual desde el servicio de accesibilidad
                    MyAccessibilityService.instance?.simulateTap(shakeX, shakeY)
                }


            }



        }

        Log.d("BLE_DEBUG", "🔍 Escaneando Poké Ball Plus en lugar de usar MAC fija...")

        val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("BLE_DEBUG", "❌ BluetoothLeScanner no disponible")
            runOnUiThread { scanResultText.text = "❌ Scanner BLE no disponible" }
            return
        }

// definimos la callback en una variable para poder pasarla a stopScan(...)
        currentScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: "Desconocido"
                Log.d("BLE_DEBUG", "📡 Dispositivo encontrado: $name [${device.address}]")

                if (name.contains("Pokeball", ignoreCase = true) ||
                    name.contains("Pokemon PBP", ignoreCase = true)) {

                    Log.d("BLE_DEBUG", "🎯 Poké Ball detectada, conectando a ${device.address}")
                    runOnUiThread { scanResultText.text = "🔌 Conectando a Poké Ball Plus..." }

                    try {
                        scanner.stopScan(this)
                    } catch (e: Exception) {
                        Log.w("BLE_DEBUG", "⚠️ stopScan fallo: ${e.message}")
                    }

                    // conectamos y guardamos el GATT global
                    bluetoothGatt = device.connectGatt(
                        this@MainActivity,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )

                }
            }

            override fun onScanFailed(errorCode: Int) {
                runOnUiThread { scanResultText.text = "❌ Error al escanear dispositivos ($errorCode)" }
                Log.e("BLE_DEBUG", "❌ Error en escaneo BLE: $errorCode")
            }
        }


// Start scan
        scanner.startScan(currentScanCallback)

        runOnUiThread { scanResultText.text = "🔍 Escaneando dispositivos..." }

// stop scan tras 10 s si no se encontró nada
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                scanner.stopScan(currentScanCallback)
            } catch (e: Exception) {
                Log.w("BLE_DEBUG", "stopScan fallo: ${e.message}")
            }
            runOnUiThread { scanResultText.text = "⏱️ Escaneo finalizado" }
        }, 10_000)

    }

}
