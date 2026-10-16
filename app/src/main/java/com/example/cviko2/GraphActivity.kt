package com.example.cviko2

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

class GraphActivity : AppCompatActivity() {
    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        lineChart = findViewById(R.id.lineChart)

        val trajectoryPoints: ArrayList<TrajectoryPointParcel>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("trajectory_data",
                    TrajectoryPointParcel::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("trajectory_data")
            }

        if (trajectoryPoints != null && trajectoryPoints.isNotEmpty()) {
            setupLineChart(trajectoryPoints)
        } else {
            Toast.makeText(this, "No trajectory data to display.", Toast.LENGTH_LONG).show()
            finish()
        }
        supportActionBar?.title = "Height vs. Time Graph"
    }

    private fun setupLineChart(points: List<TrajectoryPointParcel>) {
        val entries = ArrayList<Entry>()
        for (point in points) {
            entries.add(Entry(point.time.toFloat(), point.y.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Height (y versus time)")
        dataSet.color = Color.BLUE
        dataSet.valueTextColor = Color.BLACK
        dataSet.setDrawValues(false)
        dataSet.setDrawCircles(false)
        dataSet.lineWidth = 2f

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.labelCount = 6
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.1fs", value)
            }
        }
        xAxis.axisMinimum = 0f

        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.1fm", value)
            }
        }
        yAxisLeft.axisMinimum = 0f

        lineChart.axisRight.isEnabled = false
        lineChart.description.text = "Projectile Height Over Time"
        lineChart.description.textSize = 12f

        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)

        lineChart.invalidate()
    }
}
