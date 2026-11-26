package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.Serializable

@Serializable
data class ProbeResult(
    val code: Int,
    val latency: Long,
    val serverIp: String? = null,
    val country: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readLong(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readValue(Double::class.java.classLoader) as? Double,
        parcel.readValue(Double::class.java.classLoader) as? Double,
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(code)
        parcel.writeLong(latency)
        parcel.writeString(serverIp)
        parcel.writeString(country)
        parcel.writeString(city)
        parcel.writeValue(latitude)
        parcel.writeValue(longitude)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ProbeResult> {
        override fun createFromParcel(parcel: Parcel): ProbeResult = ProbeResult(parcel)

        override fun newArray(size: Int): Array<ProbeResult?> = arrayOfNulls(size)
    }
}
