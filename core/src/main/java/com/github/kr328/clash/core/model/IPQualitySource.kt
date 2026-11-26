package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IPQualitySource(
    val name: String,
    @SerialName("country") val country: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("isp") val isp: String? = null,
    @SerialName("asn") val asn: String? = null,
    @SerialName("risk") val risk: String? = null,
    @SerialName("proxy") val proxy: String? = null,
    @SerialName("hosting") val hosting: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readValue(Double::class.java.classLoader) as? Double,
        parcel.readValue(Double::class.java.classLoader) as? Double,
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(country)
        parcel.writeString(region)
        parcel.writeString(city)
        parcel.writeString(isp)
        parcel.writeString(asn)
        parcel.writeString(risk)
        parcel.writeString(proxy)
        parcel.writeString(hosting)
        parcel.writeString(type)
        parcel.writeValue(latitude)
        parcel.writeValue(longitude)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<IPQualitySource> {
        override fun createFromParcel(parcel: Parcel): IPQualitySource = IPQualitySource(parcel)

        override fun newArray(size: Int): Array<IPQualitySource?> = arrayOfNulls(size)
    }
}
