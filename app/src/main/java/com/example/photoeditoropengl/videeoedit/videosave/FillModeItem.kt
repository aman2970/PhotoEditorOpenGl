package com.example.photoeditoropengl.videeoedit.videosave

import android.os.Parcel
import android.os.Parcelable

data class FillModeItem(
    val scale: Float,
    val rotate: Float,
    val translateX: Float,
    val translateY: Float,
    val videoWidth: Float,
    val videoHeight: Float
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(scale)
        parcel.writeFloat(rotate)
        parcel.writeFloat(translateX)
        parcel.writeFloat(translateY)
        parcel.writeFloat(videoWidth)
        parcel.writeFloat(videoHeight)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FillModeItem> {
        override fun createFromParcel(parcel: Parcel): FillModeItem {
            return FillModeItem(parcel)
        }

        override fun newArray(size: Int): Array<FillModeItem?> {
            return arrayOfNulls(size)
        }
    }
}