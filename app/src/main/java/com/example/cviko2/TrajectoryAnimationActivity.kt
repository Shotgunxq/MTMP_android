package com.example.cviko2

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class TrajectoryAnimationActivity : AppCompatActivity() {

    private lateinit var animationView: TrajectoryAnimationView
    private lateinit var buttonReplay: Button
    private lateinit var textViewLiveStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_animation)

        animationView = findViewById(R.id.trajectoryAnimationView)
        buttonReplay = findViewById(R.id.buttonReplay)
        textViewLiveStats = findViewById(R.id.textViewLiveStats)

        // Prijatie bodov trajektórie z MainActivity cez Intent
        val trajectoryPoints: ArrayList<TrajectoryPointParcel>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("trajectory_data", TrajectoryPointParcel::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("trajectory_data")
            }

        if (trajectoryPoints != null && trajectoryPoints.isNotEmpty()) {
            // Callback: Každý krok animácie aktualizuje text v TextView
            animationView.onPointUpdateListener = { point ->
                textViewLiveStats.text = String.format(
                    Locale.US,
                    "Aktuálny stav: Čas t = %.2f s | Poloha x = %.1f m | Výška y = %.1f m",
                    point.time, point.x, point.y
                )
            }

            // Nastavenie bodov a spustenie animácie
            animationView.setTrajectory(trajectoryPoints)

            buttonReplay.setOnClickListener {
                animationView.setTrajectory(trajectoryPoints)
            }
        } else {
            Toast.makeText(this, "Žiadne dáta na animáciu.", Toast.LENGTH_LONG).show()
            finish()
        }

        supportActionBar?.title = "Animácia trajektórie"
    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.stopAnimation()
    }
}
