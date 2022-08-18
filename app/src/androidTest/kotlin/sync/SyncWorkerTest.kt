package sync

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import app.App
import conf.ConfRepo
import org.koin.android.ext.android.get
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class SyncWorkerTest {

    private lateinit var app: App

    @BeforeTest
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        app = instrumentation.targetContext.applicationContext as App
    }

    @Test
    fun retryIfInitialSyncIncomplete() {
        val confRepo = app.get<ConfRepo>()
        confRepo.update { it.copy(initial_sync_completed = false) }
        val workerBuilder = TestWorkerBuilder.from(app, SyncWorker::class.java)
        val worker = workerBuilder.build()
        val result = worker.doWork()
        assertIs<ListenableWorker.Result.Retry>(result)
    }
}