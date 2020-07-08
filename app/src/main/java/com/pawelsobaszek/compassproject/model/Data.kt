package com.pawelsobaszek.compassproject.model

import com.google.gson.annotations.SerializedName

data class UserCurrentPosition(
    @SerializedName("userLatitude")
    val userLatitude: Double,
    @SerializedName("userLongitude")
    val userLongitude: Double
)

data class DirectionCoordinates(
    @SerializedName("directionLatitude")
    val directionLatitude: Double,
    @SerializedName("directionLongitude")
    val directionLongitude: Double
)