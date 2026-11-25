package io.github.michealsizemore1.beyondstride

import android.app.Application

/** App start: sets up the 🔒 app lock for every screen. */
class BsApp : Application() {
    override fun onCreate() {
        super.onCreate(); AppLock.install(this)
        // One time: decaf Caramel Brulée lattes logged before they counted toward liquids.
        Thread { try { io.github.michealsizemore1.beyondstride.run.DecafCatchUp.run(this) } catch (e: Exception) { }
            // One time: natural sugar (fruit, vegetables, plain dairy) rechecked on every food already logged.
            try { io.github.michealsizemore1.beyondstride.run.SugarRecheck.run(this) } catch (e: Exception) { } }.start()
    }
}
