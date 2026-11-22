package io.github.michealsizemore1.beyondstride

import android.app.Application

/** App start: sets up the 🔒 app lock for every screen. */
class BsApp : Application() {
    override fun onCreate() {
        super.onCreate(); AppLock.install(this)
        // One time: decaf Caramel Brulée lattes logged before they counted toward liquids.
        Thread { try { Hydration.decafCatchUp(this).forEach { (date, oz) -> io.github.michealsizemore1.beyondstride.run.HydrationSync.change(this, "other", oz, date = date) } } catch (e: Exception) { } }.start()
    }
}
