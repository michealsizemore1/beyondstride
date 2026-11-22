package io.github.michealsizemore1.beyondstride

import android.app.Application

/** App start: sets up the 🔒 app lock for every screen. */
class BsApp : Application() {
    override fun onCreate() { super.onCreate(); AppLock.install(this) }
}
