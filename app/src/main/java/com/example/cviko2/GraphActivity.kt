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
import java.util.Locale

/**
 * Aktivita pre zobrazenie 2D grafu trajektórie:
 * - Využíva knižnicu MPAndroidChart.
 * - Zobrazuje fyzikálnu trajektóriu v priestore: Výška y [m] vs. Horizontálna vzdialenosť x [m].
 */
class GraphActivity : AppCompatActivity() {
    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        lineChart = findViewById(R.id.lineChart)

        // Prijatie bodov z Intentu
        val trajectoryPoints: ArrayList<TrajectoryPointParcel>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("trajectory_data", TrajectoryPointParcel::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("trajectory_data")
            }

        if (trajectoryPoints != null && trajectoryPoints.isNotEmpty()) {
            setupLineChart(trajectoryPoints)
        } else {
            Toast.makeText(this, "Žiadne dáta trajektórie na zobrazenie.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Nastavenie grafu, mierok osí a vykreslenie bodov
     */
    private fun setupLineChart(points: List<TrajectoryPointParcel>) {
        val entries = ArrayList<Entry>()
        var maxY = 0.0
        var maxX = 0.0
        var totalTime = 0.0

        // Konverzia bodov na vstupné záznamy grafu: (x, y)
        for (point in points) {
            entries.add(Entry(point.x.toFloat(), point.y.toFloat()))
            if (point.y > maxY) maxY = point.y
            if (point.x > maxX) maxX = point.x
            if (point.time > totalTime) totalTime = point.time
        }

        // Nastavenie titulku lišty so súhrnom
        supportActionBar?.title = String.format(
            Locale.US,
            "Trajektória: Dolet = %.1fm | Max Výška = %.1fm",
            maxX, maxY
        )

        // Nastavenie krivky grafu (LineDataSet)
        val dataSet = LineDataSet(entries, "Trajektória letu [y(x)]")
        dataSet.color = Color.parseColor("#1E88E5")
        dataSet.lineWidth = 3f
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#90CAF9")
        dataSet.fillAlpha = 60
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // Hladká kubická krivka

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // Konfigurácia osi X (Horizontálna vzdialenosť v metroch)
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.LTGRAY
        xAxis.gridLineWidth = 1f
        xAxis.axisLineColor = Color.DKGRAY
        xAxis.axisLineWidth = 1.5f
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = (maxX * 1.05).toFloat()
        xAxis.setLabelCount(8, false)
        xAxis.textSize = 11f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format(Locale.US, "%.1fm", value)
            }
        }

        // Konfigurácia osi Y (Vertikálna výška v metroch)
        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.gridColor = Color.LTGRAY
        yAxisLeft.gridLineWidth = 1f
        yAxisLeft.axisLineColor = Color.DKGRAY
        yAxisLeft.axisLineWidth = 1.5f
        yAxisLeft.axisMinimum = 0f
        yAxisLeft.axisMaximum = (maxY * 1.15).toFloat()
        yAxisLeft.setLabelCount(8, false)
        yAxisLeft.textSize = 11f
        yAxisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format(Locale.US, "%.1fm", value)
            }
        }

        lineChart.axisRight.isEnabled = false

        lineChart.description.apply {
            text = String.format(Locale.US, "Výška y [m] vs. Vzdialenosť x [m] (Čas letu: %.2fs)", totalTime)
            textSize = 12f
            textColor = Color.DKGRAY
        }

        lineChart.legend.apply {
            textSize = 12f
            textColor = Color.BLACK
        }

        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setExtraOffsets(10f, 10f, 10f, 15f)

        lineChart.animateX(750)
        lineChart.invalidate()
    }
}
