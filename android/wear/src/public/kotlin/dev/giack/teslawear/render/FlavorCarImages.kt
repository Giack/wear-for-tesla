package dev.giack.teslawear.render

import android.content.Context
import dev.giack.teslawear.core.setup.CarProfile
import okhttp3.OkHttpClient

/** The Public build draws the Car illustration for the car profile. */
@Suppress("UNUSED_PARAMETER")
fun carImages(context: Context, http: OkHttpClient, profile: () -> CarProfile?): CarImages =
    IllustratedCarImages(context, profile)
