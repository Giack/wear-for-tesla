package dev.giack.teslawear.render

import android.content.Context
import dev.giack.teslawear.core.setup.CarProfile
import okhttp3.OkHttpClient

/** The Public build shows the project's car renders, and the Car illustration for models without them. */
@Suppress("UNUSED_PARAMETER")
fun carImages(context: Context, http: OkHttpClient, profile: () -> CarProfile?): CarImages =
    FallbackCarImages(RenderedCarImages(context, profile), IllustratedCarImages(context, profile))
