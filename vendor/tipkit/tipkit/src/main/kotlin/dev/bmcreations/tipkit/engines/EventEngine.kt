package dev.bmcreations.tipkit.engines

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.bmcreations.tipkit.Tip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


class EventEngine(
    context: Context,
    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val jsonDecoder by lazy {
        Json {
            ignoreUnknownKeys = true
        }
    }

    internal fun clearCompletions() {
        dataScope.launch {
            completions.edit { it.clear() }
            println("clearCompletions")
        }
    }

    internal fun clearCompletion(vararg tip: Tip) {
        dataScope.launch {
            completions.edit { prefs ->
                tip.map { longPreferencesKey("${it::class.java.simpleName}-completedAt") }
                    .onEach { key ->
                        prefs.remove(key)
                    }
            }
        }
    }

    internal fun removeAllOccurrences() {
        dataScope.launch {
            occurrenceInstances.edit { it.clear() }
            println("removeAllOccurrences")
        }
    }

    internal fun removeOccurrencesOf(vararg trigger: Trigger) {
        dataScope.launch {
            occurrenceInstances.edit { prefs ->
                trigger.map { longPreferencesKey(it.id) }.onEach { key ->
                    prefs.remove(key)
                }
            }
        }
    }

    private val completions = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("event-engine-completions") }
    )

    private val occurrenceInstances = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("event-engine-occurrences") }
    )

    internal fun recordTriggerOccurrence(occurrence: Trigger, value: Any) =
        dataScope.launch(Dispatchers.IO) {
            occurrenceInstances.edit { prefs ->
                val key = stringSetPreferencesKey(occurrence.id)
                val pref = prefs[key].orEmpty()
                val events = pref.toList()
                    .mapNotNull {
                        runCatching {
                            jsonDecoder.decodeFromString<DbTriggerOccurrenceEvent>(it)
                        }.getOrNull()
                    }
                    .sortedBy { it.timestamp }

                val event = when (value) {
                    is Boolean -> BooleanEvent(
                        id = occurrence.id,
                        timestamp = Clock.System.now(),
                        value = value
                    )

                    is Int -> IntEvent(
                        id = occurrence.id,
                        timestamp = Clock.System.now(),
                        value = value
                    )

                    is Long -> LongEvent(
                        id = occurrence.id,
                        timestamp = Clock.System.now(),
                        value = value
                    )

                    is Double -> DoubleEvent(
                        id = occurrence.id,
                        timestamp = Clock.System.now(),
                        value = value
                    )

                    is Float -> FloatEvent(
                        id = occurrence.id,
                        timestamp = Clock.System.now(),
                        value = value
                    )

                    else -> InstantEvent(
                        id = occurrence.id,
                        timestamp = Clock.System.now(),
                    )
                }


                prefs[key] =
                    (events + event).map {
                        jsonDecoder.encodeToString(DbTriggerOccurrenceEvent.serializer(), it)
                    }.toSet()
            }
        }

    internal fun recordTriggerOccurrence(occurrence: Trigger) =
        dataScope.launch(Dispatchers.IO) {
            occurrenceInstances.edit { prefs ->
                val key = stringSetPreferencesKey(occurrence.id)
                val pref = prefs[key].orEmpty()
                val events = pref.toList()
                    .mapNotNull {
                        runCatching {
                            jsonDecoder.decodeFromString<DbTriggerOccurrenceEvent>(it)
                        }.getOrNull()
                    }
                    .sortedBy { it.timestamp }

                val event = InstantEvent(
                    id = occurrence.id,
                    timestamp = Clock.System.now(),
                )


                prefs[key] =
                    (events + event).map {
                        jsonDecoder.encodeToString(DbTriggerOccurrenceEvent.serializer(), it)
                    }.toSet()
            }
        }

    fun occurrences() = occurrenceInstances.data.map {
        it.asMap().values.map { it as Set<String> }
            .map { set -> set.toList() }
            .map { list ->
                list.mapNotNull { entry ->
                    runCatching {
                        jsonDecoder.decodeFromString<DbTriggerOccurrenceEvent>(entry)
                    }.getOrNull()
                }.map { event ->
                    when (event) {
                        is BooleanEvent -> Event.TriggerOccurrence(
                            id = event.id,
                            timestamp = event.timestamp,
                            value = event.value
                        )

                        is DoubleEvent -> Event.TriggerOccurrence(
                            id = event.id,
                            timestamp = event.timestamp,
                            value = event.value
                        )

                        is FloatEvent -> Event.TriggerOccurrence(
                            id = event.id,
                            timestamp = event.timestamp,
                            value = event.value
                        )

                        is IntEvent -> Event.TriggerOccurrence(
                            id = event.id,
                            timestamp = event.timestamp,
                            value = event.value
                        )

                        is LongEvent -> Event.TriggerOccurrence(
                            id = event.id,
                            timestamp = event.timestamp,
                            value = event.value
                        )

                        else -> Event.TriggerOccurrence(
                            id = event.id,
                            timestamp = event.timestamp,
                            value = event.timestamp
                        )
                    }
                }
            }.flatten()
    }

    suspend fun complete(name: String) {
        completions.edit { prefs ->
            prefs[longPreferencesKey("$name-completedAt")] =
                Clock.System.now().toEpochMilliseconds()
        }
    }

    suspend fun isComplete(name: String): Boolean {
        println("checking $name")
        return (completions.data.map { prefs ->
            prefs[longPreferencesKey("$name-completedAt")]
        }.firstOrNull() != null).also { println("seen=$it") }
    }
}

typealias EligibilityCriteria = suspend () -> Boolean

data class Trigger(
    internal val id: String,
    internal val engine: EventEngine,
    val events: Flow<List<Event.TriggerOccurrence>> = engine
        .occurrences()
        .map { it.filter { e -> e.id == id } }
        .onEmpty { emit(emptyList()) },
) {
    fun record() {
        engine.recordTriggerOccurrence(this)
    }

    fun record(value: Any) {
        engine.recordTriggerOccurrence(this, value)
    }

    fun reset() {
        engine.removeOccurrencesOf(this)
    }
}

@Serializable
private sealed class DbTriggerOccurrenceEvent {
    abstract val id: String
    abstract val timestamp: Instant
}

@Serializable
private data class InstantEvent(
    override val id: String,
    override val timestamp: Instant
) : DbTriggerOccurrenceEvent()

@Serializable
private data class BooleanEvent(
    override val id: String,
    override val timestamp: Instant,
    val value: Boolean
) : DbTriggerOccurrenceEvent()

@Serializable
private data class IntEvent(
    override val id: String,
    override val timestamp: Instant,
    val value: Int
) : DbTriggerOccurrenceEvent()

@Serializable
private data class DoubleEvent(
    override val id: String,
    override val timestamp: Instant,
    val value: Double
) : DbTriggerOccurrenceEvent()

@Serializable
private data class LongEvent(
    override val id: String,
    override val timestamp: Instant,
    val value: Long
) : DbTriggerOccurrenceEvent()

@Serializable
private data class FloatEvent(
    override val id: String,
    override val timestamp: Instant,
    val value: Float
) : DbTriggerOccurrenceEvent()

sealed interface Event {
    @Serializable
    data class TriggerOccurrence(
        val id: String,
        val timestamp: Instant,
        @Polymorphic
        val value: Any
    ): Event

    data class Completion(
        val id: String,
        val timestamp: Instant
    ): Event
}
