package org.jellyfin.androidtv

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.acra.ACRA
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.jellyfin.androidtv.util.AutoBitrate
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

@Suppress("unused")
class JellyfinApplication : Application() {
	override fun onCreate() {
		super.onCreate()

		// Don't run in ACRA service
		if (ACRA.isACRASenderServiceProcess()) return

		val notificationsRepository by inject<NotificationsRepository>()
		notificationsRepository.addDefaultNotifications()
	}

	/**
	 * Called from the StartupActivity when the user session is started.
	 */
	suspend fun onSessionStart() = withContext(Dispatchers.IO) {
		val workManager by inject<WorkManager>()
		val autoBitrate by inject<AutoBitrate>()
		val socketListener by inject<SocketHandler>()

		// Update background worker
		launch {
			// Cancel all current workers
			workManager.cancelAllWork().await()

			// Recreate periodic workers
			workManager.enqueueUniquePeriodicWork(
				LeanbackChannelWorker.PERIODIC_UPDATE_REQUEST_NAME,
				ExistingPeriodicWorkPolicy.REPLACE,
				PeriodicWorkRequestBuilder<LeanbackChannelWorker>(1, TimeUnit.HOURS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
					.build()
			).await()
		}

		// Update WebSockets
		launch { socketListener.updateSession() }

		// Detect auto bitrate
		// running in a different scope to prevent slow startups
		ProcessLifecycleOwner.get().lifecycleScope.launch { autoBitrate.detect() }
	}

	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)

		TelemetryService.init(this)
	}
}
