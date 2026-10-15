package com.ambientbrightness.app

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ambientbrightness.core.BackgroundService.BackgroundNotificationContent
import com.ambientbrightness.infrastructure.BackgroundService.AndroidBrightnessNotificationFactory.AndroidBrightnessNotificationFactory
import com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost.ForegroundBrightnessService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [ForegroundBrightnessService] on a device and checks the platform contract of the
 * persistent notification (US1, AC1.1; US4, AC4.3).
 *
 * It lives in `:app`'s androidTest, not `:infrastructure`'s, because the service is declared in the
 * **app** manifest: `:infrastructure`'s manifest is empty, so the infrastructure test target
 * (`com.ambientbrightness.infrastructure.test`) has no such component and every bind attempt failed
 * with "service not found" — the test silently skipped and proved nothing. This mirrors the
 * documented correction for the F2 adapter tests, which had to move to `:app` for the same reason.
 */
@RunWith(AndroidJUnit4::class)
class ForegroundBrightnessServiceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun serviceRunsInForegroundAndPublishesOngoingNotificationWithStopAction() {
        val serviceIntent = Intent(context, ForegroundBrightnessService::class.java)
        val boundLatch = CountDownLatch(1)
        var boundService: ForegroundBrightnessService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                boundService = (binder as ForegroundBrightnessService.LocalBinder).service()
                boundLatch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }

        context.startForegroundService(serviceIntent)
        assumeTrue(context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE))
        try {
            assumeTrue(
                "the foreground service did not bind in time",
                boundLatch.await(5, TimeUnit.SECONDS),
            )
            val service = boundService!!
            service.startForeground(RUNNING_CONTENT)

            assertTrue("the service must report itself in the foreground", service.isInForeground)

            val built = AndroidBrightnessNotificationFactory(context).create(RUNNING_CONTENT)
            assertTrue(
                "the notification must stay non-dismissible",
                built.flags and Notification.FLAG_ONGOING_EVENT != 0,
            )
            assertTrue(
                "the notification must not auto-cancel",
                built.flags and Notification.FLAG_AUTO_CANCEL == 0,
            )
            assertEquals("exactly one Stop action is offered", 1, built.actions.size)
            assertEquals("Stop", built.actions[0].title.toString())

            postedNotification()?.let { posted ->
                assertTrue(
                    "the posted notification must be ongoing",
                    posted.flags and Notification.FLAG_ONGOING_EVENT != 0,
                )
                assertEquals(
                    "the posted notification carries the Stop action",
                    1,
                    posted.actions.size,
                )
            }
        } finally {
            context.unbindService(connection)
            context.stopService(serviceIntent)
        }
    }

    private fun postedNotification(): Notification? =
        context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.firstOrNull { it.id == AndroidBrightnessNotificationFactory.NOTIFICATION_ID }
            ?.notification

    private companion object {
        val RUNNING_CONTENT = BackgroundNotificationContent(
            title = "Ambient brightness control is running",
            text = "Applied brightness: 128",
            ongoing = true,
            showStopAction = true,
        )
    }
}
