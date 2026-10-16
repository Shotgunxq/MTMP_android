package com.example.cviko2

import android.os.Parcelable
import android.os.Parcel

data class TrajectoryPointParcel(
    val time: Double, val x: Double, val y: Double
) : Parcelable {
    companion object{
        @JvmField
        val CREATOR = object : Parcelable.Creator<TrajectoryPointParcel>{
            override fun createFromParcel(parcel : Parcel) = TrajectoryPointParcel(parcel)
            override fun newArray(size: Int) = arrayOfNulls<TrajectoryPointParcel>(size)
        }
    }


    private constructor(parcel: Parcel) : this(
    time = parcel.readDouble(),
    x = parcel.readDouble(),
    y = parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(time)
        parcel.writeDouble(x)
        parcel.writeDouble(y)
    }
    override fun describeContents() = 0
}