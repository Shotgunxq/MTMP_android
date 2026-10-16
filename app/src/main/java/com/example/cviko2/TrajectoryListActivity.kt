package com.example.cviko2

import android.os.Build
import android.os.Bundle
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.DecimalFormat

class TrajectoryListActivity : AppCompatActivity() {

    private lateinit var listViewTrajectory: ListView

    private val decimalFormat = DecimalFormat("#.##")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trajectory_list)

        listViewTrajectory = findViewById(R.id.listViewTrajectory)

        val trajectoryPointsParcel: ArrayList<TrajectoryPointParcel>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableArrayListExtra("trajectory_data", TrajectoryPointParcel::class.java)
            else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("trajectory_data")
            }

        if (trajectoryPointsParcel != null && trajectoryPointsParcel.isNotEmpty()) {
            val dataForListView = trajectoryPointsParcel.map {
                "T: ${decimalFormat.format(it.time)}s, x: ${decimalFormat.format(it.x)}m, Y: ${
                    decimalFormat.format(it.y)
                }m"
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, dataForListView)
            listViewTrajectory.adapter = adapter
        } else {
            Toast.makeText(this, "No trajectory data found", Toast.LENGTH_LONG).show()
        }

        supportActionBar?.title = "trajectory list"
    }
}
