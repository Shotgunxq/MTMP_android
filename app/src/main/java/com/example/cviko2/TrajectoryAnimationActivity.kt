package com.example.cviko2

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TrajectoryAnimationActivity : AppCompatActivity() {

    private lateinit var animationView: TrajectoryAnimationView
    private lateinit var buttonReplay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_animation)

        animationView = findViewById(R.id.trajectoryAnimationView)
        buttonReplay = findViewById(R.id.buttonReplay)

        val trajectoryPoints: ArrayList<TrajectoryPointParcel>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("trajectory_data",
                    TrajectoryPointParcel::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("trajectory_data")
            }

        if (trajectoryPoints != null && trajectoryPoints.isNotEmpty()) {
            animationView.setTrajectory(trajectoryPoints)
            buttonReplay.setOnClickListener {
                animationView.setTrajectory(trajectoryPoints)
            }
        } else {
            Toast.makeText(this, "No trajectory data to animate.", Toast.LENGTH_LONG).show()
            finish()
        }

        supportActionBar?.title = "Trajectory Animation"
    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.stopAnimation()
    }
}
