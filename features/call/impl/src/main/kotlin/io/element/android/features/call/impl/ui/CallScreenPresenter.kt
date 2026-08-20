/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.call.api.CallType
import io.element.android.features.call.impl.data.WidgetMessage
import io.element.android.features.call.impl.utils.ActiveCallManager
import io.element.android.features.call.impl.utils.CallWidgetProvider
import io.element.android.features.call.impl.utils.WidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageSerializer
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.CallNotifyContent
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import io.element.android.services.appnavstate.api.ActiveRoomsHolder
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.services.analytics.api.ScreenTracker
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class CallScreenPresenter(
    @Assisted private val callType: CallType,
    @Assisted private val navigator: CallScreenNavigator,
    private val callWidgetProvider: CallWidgetProvider,
    userAgentProvider: UserAgentProvider,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val matrixClientsProvider: MatrixClientProvider,
    private val screenTracker: ScreenTracker,
    private val activeCallManager: ActiveCallManager,
    private val languageTagProvider: LanguageTagProvider,
    private val appForegroundStateService: AppForegroundStateService,
    @AppCoroutineScope
    private val appCoroutineScope: CoroutineScope,
    private val widgetMessageSerializer: WidgetMessageSerializer,
    private val activeRoomsHolder: ActiveRoomsHolder,
) : Presenter<CallScreenState> {
    @AssistedFactory
    interface Factory {
        fun create(callType: CallType, navigator: CallScreenNavigator): CallScreenPresenter
    }

    private val isInWidgetMode = callType is CallType.RoomCall
    private val userAgent = userAgentProvider.provide()

    @Composable
    override fun present(): CallScreenState {
        val coroutineScope = rememberCoroutineScope()
        val urlState = remember { mutableStateOf<AsyncData<String>>(AsyncData.Uninitialized) }
        val callWidgetDriver = remember { mutableStateOf<MatrixWidgetDriver?>(null) }
        val messageInterceptor = remember { mutableStateOf<WidgetMessageInterceptor?>(null) }
        var isWidgetLoaded by rememberSaveable { mutableStateOf(false) }
        var ignoreWebViewError by rememberSaveable { mutableStateOf(false) }
        var webViewError by remember { mutableStateOf<String?>(null) }
        // Flipped by the caller-side decline observer below when the DM peer declines the call.
        var peerDeclined by remember { mutableStateOf(false) }
        val languageTag = languageTagProvider.provideLanguageTag()
        val theme = if (ElementTheme.isLightTheme) "light" else "dark"

        // Hang up the current call: if the widget is live, ask it to hang up first (so the RTC
        // session is torn down cleanly) then close; otherwise just close. Shared by the manual
        // Hangup button and the automatic decline handling.
        fun performHangup() {
            val widgetId = callWidgetDriver.value?.id
            val interceptor = messageInterceptor.value
            if (widgetId != null && interceptor != null && isWidgetLoaded) {
                sendHangupMessage(widgetId, interceptor)
                isWidgetLoaded = false
                coroutineScope.launch {
                    delay(2.seconds)
                    close(callWidgetDriver.value, navigator)
                }
            } else {
                coroutineScope.launch {
                    close(callWidgetDriver.value, navigator)
                }
            }
        }

        DisposableEffect(Unit) {
            coroutineScope.launch {
                // Sets the call as joined
                activeCallManager.joinedCall(callType)
                fetchRoomCallUrl(
                    inputs = callType,
                    urlState = urlState,
                    callWidgetDriver = callWidgetDriver,
                    languageTag = languageTag,
                    theme = theme,
                )
            }
            onDispose {
                appCoroutineScope.launch { activeCallManager.hungUpCall(callType) }
            }
        }

        when (callType) {
            is CallType.ExternalUrl -> {
                // No analytics yet for external calls
            }
            is CallType.RoomCall -> {
                screenTracker.TrackScreen(screen = MobileScreen.ScreenName.RoomCall)
            }
        }

        HandleMatrixClientSyncState()

        callWidgetDriver.value?.let { driver ->
            LaunchedEffect(Unit) {
                driver.incomingMessages
                    .onEach {
                        // Relay message to the WebView
                        messageInterceptor.value?.sendMessage(it)
                    }
                    .launchIn(this)

                driver.run()
            }
        }

        messageInterceptor.value?.let { interceptor ->
            LaunchedEffect(Unit) {
                interceptor.interceptedMessages
                    .onEach {
                        // We are receiving messages from the WebView, consider that the application is loaded
                        ignoreWebViewError = true
                        // Relay message to Widget Driver
                        callWidgetDriver.value?.send(it)

                        val parsedMessage = parseMessage(it)
                        if (parsedMessage?.direction == WidgetMessage.Direction.FromWidget) {
                            if (parsedMessage.action == WidgetMessage.Action.Close) {
                                close(callWidgetDriver.value, navigator)
                            } else if (parsedMessage.action == WidgetMessage.Action.ContentLoaded) {
                                isWidgetLoaded = true
                            }
                        }
                    }
                    .launchIn(this)
            }

            LaunchedEffect(Unit) {
                // Wait for the call to be joined, if it takes too long, we display an error
                delay(10.seconds)

                if (!isWidgetLoaded) {
                    Timber.w("The call took too long to load. Displaying an error before exiting.")

                    // This will display a simple 'Sorry, an error occurred' dialog and force the user to exit the call
                    webViewError = ""
                }
            }
        }

        // Caller-side auto-hangup when the DM peer DECLINES. Element Call leaves the caller sitting
        // in the RTC session ("Waiting for media…") after a decline, which also keeps the room's
        // call "ongoing" so BOTH sides keep showing a Join button in chat. Detect the decline via
        // the SDK and hang up — this tears down the RTC session and clears Join on both devices.
        val roomCallType = callType as? CallType.RoomCall
        if (roomCallType != null) {
            LaunchedEffect(Unit) {
                val client = matrixClientsProvider.getOrRestore(roomCallType.sessionId).getOrNull()
                    ?: return@LaunchedEffect
                // Reuse the already-open room when possible; only a room WE create must be destroyed.
                val activeRoom = activeRoomsHolder.getActiveRoomMatching(roomCallType.sessionId, roomCallType.roomId)
                val room = activeRoom ?: client.getJoinedRoom(roomCallType.roomId) ?: return@LaunchedEffect
                val ownRoom = activeRoom == null
                try {
                    // Only 1:1 DM calls end on a single decline; a group call must survive it.
                    if (!room.isDm()) return@LaunchedEffect
                    // The caller isn't handed the ring (m.rtc.notification) event id — Element Call
                    // emits it over the widget API — so discover it from our own live timeline. It
                    // is CRITICAL to pick the ring THIS call authors and NOT one left over from a
                    // previous call in the same room: an old ring can carry a stale decline and hang
                    // up the wrong (fresh) call. Timestamp windows are unreliable here (redials
                    // within the window; device-vs-server clock skew). Instead, snapshot the rings
                    // already present on the FIRST emission as a baseline and then wait for the
                    // first NEW own ring to appear — that is unambiguously the ring for the call we
                    // just started. (A callback's receiver authors no new ring, so this correctly
                    // never fires for them, and even a missed ring degrades to a manual hang-up
                    // rather than hanging up the wrong call.)
                    val baseline = HashSet<EventId>()
                    var baselineCaptured = false
                    val ringEventId = room.liveTimeline.timelineItems
                        .mapNotNull { items ->
                            val ownRings = items.asSequence()
                                .filterIsInstance<MatrixTimelineItem.Event>()
                                .filter { it.event.isOwn && it.event.content is CallNotifyContent }
                                .mapNotNull { it.event.eventId }
                                .toList()
                            if (!baselineCaptured) {
                                baseline.addAll(ownRings)
                                baselineCaptured = true
                                null
                            } else {
                                ownRings.firstOrNull { it !in baseline }
                            }
                        }
                        .first()
                    Timber.d("Observing declines for DM call ring $ringEventId")
                    room.subscribeToCallDecline(ringEventId).collect { decliner ->
                        // Ignore our own decline echoes (e.g. from another of our sessions); react
                        // only when the OTHER DM member declines.
                        if (decliner != client.sessionId) {
                            Timber.d("DM call declined by $decliner; hanging up the caller")
                            peerDeclined = true
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Caller-side decline observer failed")
                } finally {
                    if (ownRoom) room.destroy()
                }
            }
            LaunchedEffect(peerDeclined) {
                if (peerDeclined) performHangup()
            }
        }

        fun handleEvent(event: CallScreenEvents) {
            when (event) {
                is CallScreenEvents.Hangup -> {
                    // If the call was joined, performHangup sends the hangup first and the UI is
                    // dismissed automatically; otherwise it closes the screen directly.
                    performHangup()
                }
                is CallScreenEvents.SetupMessageChannels -> {
                    messageInterceptor.value = event.widgetMessageInterceptor
                }
                is CallScreenEvents.OnWebViewError -> {
                    if (!ignoreWebViewError) {
                        webViewError = event.description.orEmpty()
                    }
                    // Else ignore the error, give a chance the Element Call to recover by itself.
                }
            }
        }

        return CallScreenState(
            urlState = urlState.value,
            webViewError = webViewError,
            userAgent = userAgent,
            isCallActive = isWidgetLoaded,
            isInWidgetMode = isInWidgetMode,
            // A voice call (startWithVideoMuted) defaults the audio route to the earpiece; video
            // calls (and external-url calls, which have no media hint) default to the loudspeaker.
            isVideoCall = (callType as? CallType.RoomCall)?.startWithVideoMuted?.not() ?: true,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun fetchRoomCallUrl(
        inputs: CallType,
        urlState: MutableState<AsyncData<String>>,
        callWidgetDriver: MutableState<MatrixWidgetDriver?>,
        languageTag: String?,
        theme: String?,
    ) {
        urlState.runCatchingUpdatingState {
            when (inputs) {
                is CallType.ExternalUrl -> {
                    inputs.url
                }
                is CallType.RoomCall -> {
                    val result = callWidgetProvider.getWidget(
                        sessionId = inputs.sessionId,
                        roomId = inputs.roomId,
                        clientId = UUID.randomUUID().toString(),
                        languageTag = languageTag,
                        theme = theme,
                        startWithVideoMuted = inputs.startWithVideoMuted,
                    ).getOrThrow()
                    callWidgetDriver.value = result.driver
                    Timber.d("Call widget driver initialized for sessionId: ${inputs.sessionId}, roomId: ${inputs.roomId}")
                    result.url
                }
            }
        }
    }

    @Composable
    private fun HandleMatrixClientSyncState() {
        val coroutineScope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            val roomCallType = callType as? CallType.RoomCall ?: return@DisposableEffect onDispose {}
            val client = matrixClientsProvider.getOrNull(roomCallType.sessionId) ?: return@DisposableEffect onDispose {
                Timber.w("No MatrixClient found for sessionId, can't send call notification: ${roomCallType.sessionId}")
            }
            coroutineScope.launch {
                Timber.d("Observing sync state in-call for sessionId: ${roomCallType.sessionId}")
                client.syncService.syncState
                    .collect { state ->
                        if (state != SyncState.Running) {
                            appForegroundStateService.updateIsInCallState(true)
                        }
                    }
            }
            onDispose {
                Timber.d("Stopped observing sync state in-call for sessionId: ${roomCallType.sessionId}")
                // Make sure we mark the call as ended in the app state
                appForegroundStateService.updateIsInCallState(false)
            }
        }
    }

    private fun parseMessage(message: String): WidgetMessage? {
        return widgetMessageSerializer.deserialize(message).getOrNull()
    }

    private fun sendHangupMessage(widgetId: String, messageInterceptor: WidgetMessageInterceptor) {
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.HangUp,
            data = null,
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    private fun CoroutineScope.close(widgetDriver: MatrixWidgetDriver?, navigator: CallScreenNavigator) = launch(dispatchers.io) {
        navigator.close()
        widgetDriver?.close()
    }
}
