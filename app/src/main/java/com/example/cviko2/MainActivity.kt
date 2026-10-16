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
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var editTextServerUrl: EditText
    private lateinit var editTextInitialVelocity: EditText
    private lateinit var editTextAngle: EditText
    private lateinit var buttonCalculate: Button
    private lateinit var textViewStatus: TextView
    private lateinit var buttonList: Button
    private lateinit var buttonGraph: Button
    private lateinit var buttonAnimation: Button

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

        editTextServerUrl = findViewById(R.id.editTextServerUrl)
        editTextInitialVelocity = findViewById(R.id.editTextInitialVelocity)
        editTextAngle = findViewById(R.id.editTextAngle)
        buttonCalculate = findViewById(R.id.buttonCalculate)
        textViewStatus = findViewById(R.id.textViewStatus)
        buttonList = findViewById(R.id.buttonList)
        buttonGraph = findViewById(R.id.buttonGraph)
        buttonAnimation = findViewById(R.id.buttonAnimation)

        buttonCalculate.setOnClickListener {
            fetchTrajectoryFromServer()
        }

        buttonList.setOnClickListener {
            if (trajectoryPointsParcel.isNotEmpty()) {
                val intent = Intent(this, TrajectoryListActivity::class.java)
                intent.putParcelableArrayListExtra("trajectory_data", trajectoryPointsParcel)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Calculate trajectory first to see the list.", Toast.LENGTH_SHORT).show()
            }
        }

        buttonGraph.setOnClickListener {
            if (trajectoryPointsParcel.isNotEmpty()) {
                val intent = Intent(this, GraphActivity::class.java)
                intent.putParcelableArrayListExtra("trajectory_data", trajectoryPointsParcel)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Calculate trajectory first to see the graph.", Toast.LENGTH_SHORT).show()
            }
        }

        buttonAnimation.setOnClickListener {
            if (trajectoryPointsParcel.isNotEmpty()) {
                val intent = Intent(this, TrajectoryAnimationActivity::class.java)
                intent.putParcelableArrayListExtra("trajectory_data", trajectoryPointsParcel)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Calculate trajectory first to see the animation.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchTrajectoryFromServer() {
        var serverUrlStr = editTextServerUrl.text.toString().trim()
        val initialVelocityStr = editTextInitialVelocity.text.toString().trim()
        val angleStr = editTextAngle.text.toString().trim()

        if (serverUrlStr.isEmpty()) {
            serverUrlStr = "http://10.0.2.2:3000"
            editTextServerUrl.setText(serverUrlStr)
        } else if (!serverUrlStr.startsWith("http://") && !serverUrlStr.startsWith("https://")) {
            serverUrlStr = "http://$serverUrlStr"
            editTextServerUrl.setText(serverUrlStr)
        }

        if (initialVelocityStr.isEmpty() || angleStr.isEmpty()) {
            Toast.makeText(this, "Please enter both velocity and angle", Toast.LENGTH_SHORT).show()
            return
        }

        val initialVelocity = initialVelocityStr.toDoubleOrNull()
        val angleDegrees = angleStr.toDoubleOrNull()

        if (initialVelocity == null || angleDegrees == null || initialVelocity <= 0 || angleDegrees <= 0 || angleDegrees >= 90) {
            Toast.makeText(this, "Invalid input. Velocity and angle must be positive, and angle < 90", Toast.LENGTH_LONG).show()
            return
        }

        textViewStatus.text = "Sending request to server..."
        buttonCalculate.isEnabled = false

        val primaryEndpoint = if (serverUrlStr.endsWith("/trajectory")) {
            serverUrlStr
        } else {
            "${serverUrlStr.removeSuffix("/")}/trajectory"
        }

        // Build list of candidate endpoints to auto-try if local connection times out
        val candidateEndpoints = mutableListOf(primaryEndpoint)
        if (primaryEndpoint.contains("10.0.2.2")) {
            candidateEndpoints.add(primaryEndpoint.replace("10.0.2.2", "127.0.0.1"))
            candidateEndpoints.add(primaryEndpoint.replace("10.0.2.2", "147.175.109.192"))
        } else if (primaryEndpoint.contains("127.0.0.1")) {
            candidateEndpoints.add(primaryEndpoint.replace("127.0.0.1", "10.0.2.2"))
            candidateEndpoints.add(primaryEndpoint.replace("127.0.0.1", "147.175.109.192"))
        }

        thread {
            var lastException: Exception? = null
            var fetchedPoints: List<TrajectoryPoint>? = null
            var successfulEndpoint: String? = null

            for (endpoint in candidateEndpoints.distinct()) {
                try {
                    Log.d("TrajectoryClient", "Attempting connection to: $endpoint")
                    fetchedPoints = executeHttpTrajectoryRequest(endpoint, initialVelocity, angleDegrees)
                    successfulEndpoint = endpoint
                    break
                } catch (e: Exception) {
                    Log.w("TrajectoryClient", "Connection to $endpoint failed: ${e.message}")
                    lastException = e
                }
            }

            runOnUiThread {
                buttonCalculate.isEnabled = true
                if (fetchedPoints != null && successfulEndpoint != null) {
                    trajectoryPoints.clear()
                    trajectoryPoints.addAll(fetchedPoints)
                    trajectoryPointsParcel.clear()
                    trajectoryPointsParcel.addAll(fetchedPoints.map { TrajectoryPointParcel(it.time, it.x, it.y) })

                    textViewStatus.text = "Received ${fetchedPoints.size} points from $successfulEndpoint"
                    Toast.makeText(this@MainActivity, "Received ${fetchedPoints.size} points from server!", Toast.LENGTH_LONG).show()
                } else {
                    val errMsg = lastException?.message ?: "Unknown error"
                    textViewStatus.text = "Connection failed: $errMsg"
                    Toast.makeText(this@MainActivity, "Cannot connect to server. Is 'node server.js' running?", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeHttpTrajectoryRequest(endpoint: String, velocity: Double, angle: Double): List<TrajectoryPoint> {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val payload = JSONObject().apply {
            put("velocity", velocity)
            put("angle", angle)
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(responseText)
            val points = mutableListOf<TrajectoryPoint>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val time = obj.getDouble("time")
                val x = obj.getDouble("x")
                val y = obj.getDouble("y")
                points.add(TrajectoryPoint(time, x, y))
            }

            Log.d("TrajectoryClient", "Successfully fetched ${points.size} points from $endpoint")
            return points
        } else {
            val errorText = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            } catch (_: Exception) {
                "HTTP $responseCode"
            }
            throw RuntimeException("Server error ($responseCode): $errorText")
        }
    }
}
