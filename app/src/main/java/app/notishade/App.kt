package app.notishade

import android.app.Application
import android.content.Context
import app.notishade.backend.Access
import app.notishade.data.HistoryStore
import app.notishade.data.Repository
import app.notishade.data.Store
import app.notishade.engine.BulkEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var store: Store
    lateinit var history: HistoryStore
    lateinit var access: Access
    lateinit var repo: Repository
    lateinit var engine: BulkEngine

    override fun onCreate() {
        super.onCreate()
        store = Store(this, scope)
        history = HistoryStore(this, scope)
        access = Access(this)
        repo = Repository(this)
        engine = BulkEngine(this)
    }

    companion object {
        fun of(context: Context) = context.applicationContext as App
    }
}
