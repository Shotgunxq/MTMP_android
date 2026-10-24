package com.example.cviko2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Hlavná aktivita (Klient):
 * - Získava od používateľa počiatočnú rýchlosť (v0) a elevačný uhol (alpha).
 * - Odosiela tieto parametre na Node.js server cez HTTP POST v JSON formáte.
 * - Prijíma vypočítané body trajektórie zo servera a odovzdáva ich aktivitám pre zoznam, graf a animáciu.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var editTextServerUrl: EditText
    private lateinit var editTextInitialVelocity: EditText
    private lateinit var editTextAngle: EditText
    private lateinit var buttonCalculate: Button
    private lateinit var textViewStatus: TextView
    private lateinit var buttonList: Button
    private lateinit var buttonGraph: Button
    private lateinit var buttonAnimation: Button

    // Zoznam prijatých bodov
    private val trajectoryPoints = mutableListOf<TrajectoryPoint>()
    private val trajectoryPointsParcel = ArrayList<TrajectoryPointParcel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializácia UI prvkov
        editTextServerUrl = findViewById(R.id.editTextServerUrl)
        editTextInitialVelocity = findViewById(R.id.editTextInitialVelocity)
        editTextAngle = findViewById(R.id.editTextAngle)
        buttonCalculate = findViewById(R.id.buttonCalculate)
        textViewStatus = findViewById(R.id.textViewStatus)
        buttonList = findViewById(R.id.buttonList)
        buttonGraph = findViewById(R.id.buttonGraph)
        buttonAnimation = findViewById(R.id.buttonAnimation)

        // Odoslanie požiadavky na server
        buttonCalculate.setOnClickListener {
            fetchTrajectoryFromServer()
        }

        // Zobrazenie tabuľkového zoznamu bodov
        buttonList.setOnClickListener {
            if (trajectoryPointsParcel.isNotEmpty()) {
                val intent = Intent(this, TrajectoryListActivity::class.java)
                intent.putParcelableArrayListExtra("trajectory_data", trajectoryPointsParcel)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Najprv vypočítajte trajektóriu cez server.", Toast.LENGTH_SHORT).show()
            }
        }

        // Zobrazenie 2D grafu trajektórie
        buttonGraph.setOnClickListener {
            if (trajectoryPointsParcel.isNotEmpty()) {
                val intent = Intent(this, GraphActivity::class.java)
                intent.putParcelableArrayListExtra("trajectory_data", trajectoryPointsParcel)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Najprv vypočítajte trajektóriu cez server.", Toast.LENGTH_SHORT).show()
            }
        }

        // Zobrazenie fyzikálnej animácie letu
        buttonAnimation.setOnClickListener {
            if (trajectoryPointsParcel.isNotEmpty()) {
                val intent = Intent(this, TrajectoryAnimationActivity::class.java)
                intent.putParcelableArrayListExtra("trajectory_data", trajectoryPointsParcel)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Najprv vypočítajte trajektóriu cez server.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Zabezpečuje sieťovú komunikáciu so serverom na pozadí (v samostatnom vlákne).
     * Využíva paralelný Smart-Connect pre okamžité nájdenie spojenia bez zamŕzania.
     */
    private fun fetchTrajectoryFromServer() {
        var serverUrlStr = editTextServerUrl.text.toString().trim()
        val initialVelocityStr = editTextInitialVelocity.text.toString().trim()
        val angleStr = editTextAngle.text.toString().trim()

        if (serverUrlStr.isEmpty()) {
            serverUrlStr = "http://127.0.0.1:3000"
            editTextServerUrl.setText(serverUrlStr)
        } else if (!serverUrlStr.startsWith("http://") && !serverUrlStr.startsWith("https://")) {
            serverUrlStr = "http://$serverUrlStr"
            editTextServerUrl.setText(serverUrlStr)
        }

        // Validácia vstupov
        if (initialVelocityStr.isEmpty() || angleStr.isEmpty()) {
            Toast.makeText(this, "Zadajte počiatočnú rýchlosť aj uhol.", Toast.LENGTH_SHORT).show()
            return
        }

        val initialVelocity = initialVelocityStr.toDoubleOrNull()
        val angleDegrees = angleStr.toDoubleOrNull()

        if (initialVelocity == null || angleDegrees == null || initialVelocity <= 0 || angleDegrees <= 0 || angleDegrees >= 90) {
            Toast.makeText(this, "Neplatný vstup: Rýchlosť a uhol musia byť kladné, uhol < 90°.", Toast.LENGTH_LONG).show()
            return
        }

        textViewStatus.text = "Pripájam sa k serveru..."
        buttonCalculate.isEnabled = false

        val primaryEndpoint = if (serverUrlStr.endsWith("/trajectory")) {
            serverUrlStr
        } else {
            "${serverUrlStr.removeSuffix("/")}/trajectory"
        }

        // Zoznam potenciálnych lokálnych adries na otestovanie (Smart-Fallback)
        val candidateEndpoints = linkedSetOf<String>()
        candidateEndpoints.add(primaryEndpoint)
        candidateEndpoints.add("http://127.0.0.1:3000/trajectory")
        candidateEndpoints.add("http://10.0.2.2:3000/trajectory")
        candidateEndpoints.add("http://147.175.109.192:3000/trajectory")

        // Sieťová operácia musí bežať na pozadí mimo hlavného UI vlákna
        thread {
            val hasCompleted = AtomicBoolean(false)
            val latch = CountDownLatch(1)
            var winningPoints: List<TrajectoryPoint>? = null
            var winningEndpoint: String? = null
            var lastErrorMessage = "Nepodarilo sa pripojiť k serveru"

            val executor = Executors.newFixedThreadPool(candidateEndpoints.size)

            // Paralelný test všetkých adries súčasne
            for (endpoint in candidateEndpoints) {
                executor.submit {
                    try {
                        val result = executeHttpTrajectoryRequest(endpoint, initialVelocity, angleDegrees)
                        if (hasCompleted.compareAndSet(false, true)) {
                            winningPoints = result
                            winningEndpoint = endpoint
                            latch.countDown()
                        }
                    } catch (e: Exception) {
                        Log.w("TrajectoryClient", "Candidate $endpoint zlyhal: ${e.message}")
                        lastErrorMessage = e.message ?: "Chyba spojenia"
                    }
                }
            }

            // Počkáme max 2.5s na prvú úspešnú odpoveď
            latch.await(2500, TimeUnit.MILLISECONDS)
            executor.shutdownNow()

            val points = winningPoints
            val endpoint = winningEndpoint

            // Návrat na UI vlákno pre aktualizáciu rozhrania
            runOnUiThread {
                buttonCalculate.isEnabled = true
                if (points != null && endpoint != null) {
                    trajectoryPoints.clear()
                    trajectoryPoints.addAll(points)
                    trajectoryPointsParcel.clear()
                    trajectoryPointsParcel.addAll(points.map { TrajectoryPointParcel(it.time, it.x, it.y) })

                    editTextServerUrl.setText(endpoint.removeSuffix("/trajectory"))
                    textViewStatus.text = "Úspech: Prijatých ${points.size} bodov zo servera ($endpoint)"
                    Toast.makeText(this@MainActivity, "Prijatých ${points.size} bodov zo servera!", Toast.LENGTH_LONG).show()
                } else {
                    textViewStatus.text = "Chyba pripojenia: $lastErrorMessage"
                    Toast.makeText(this@MainActivity, "Nedá sa pripojiť k serveru. Beží 'node server.js'?", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Vykoná HTTP POST požiadavku s JSON telom: { "velocity": ..., "angle": ... }
     * a vráti naparsovaný zoznam bodov [TrajectoryPoint].
     */
    private fun executeHttpTrajectoryRequest(endpoint: String, velocity: Double, angle: Double): List<TrajectoryPoint> {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 1800
        conn.readTimeout = 1800

        // Vytvorenie JSON požiadavky
        val payload = JSONObject().apply {
            put("velocity", velocity)
            put("angle", angle)
        }

        // Odoslanie dát serveru
        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        // Spracovanie odpovede
        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(responseText)
            val points = mutableListOf<TrajectoryPoint>()

            // Parsovanie prijatého poľa JSON objektov: [{ time, x, y }, ...]
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val time = obj.getDouble("time")
                val x = obj.getDouble("x")
                val y = obj.getDouble("y")
                points.add(TrajectoryPoint(time, x, y))
            }

            Log.d("TrajectoryClient", "Úspešne prijatých ${points.size} bodov z $endpoint")
            return points
        } else {
            val errorText = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            } catch (_: Exception) {
                "HTTP $responseCode"
            }
            throw RuntimeException("Chyba servera ($responseCode): $errorText")
        }
    }
}
