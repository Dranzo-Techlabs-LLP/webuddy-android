/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.presence

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.security.MessageDigest

/** "Who can see my last seen" (WhatsApp's options). */
enum class LastSeenAudience(internal val key: String) {
    EVERYONE("everyone"),
    MY_CONTACTS("contacts"),
    MY_CONTACTS_EXCEPT("contacts_except"),
    NOBODY("nobody"),
}

/** "Who can see when I'm online". */
enum class OnlineAudience(internal val key: String) {
    EVERYONE("everyone"),
    SAME_AS_LAST_SEEN("same_as_last_seen"),
}

/**
 * A user's "Last seen and online" privacy choice. It is stored in their Wallet-API `settings` so
 * chat partners' apps can honour it.
 *
 * A "contact" is someone the user has a DM with that both of them have joined (an unanswered chat
 * request doesn't count). Excluded contacts are stored as [exceptToken]s rather than user IDs, so
 * the public settings record doesn't list who was excluded.
 */
data class PresencePrivacy(
    val lastSeen: LastSeenAudience = LastSeenAudience.EVERYONE,
    val online: OnlineAudience = OnlineAudience.EVERYONE,
    val lastSeenExcept: Set<String> = emptySet(),
) {
    /** Whether [ownerId] (whose choice this is) shows their last seen to [viewerId]. */
    fun sharesLastSeenWith(ownerId: String, viewerId: String, isContact: Boolean): Boolean = when (lastSeen) {
        LastSeenAudience.EVERYONE -> true
        LastSeenAudience.MY_CONTACTS -> isContact
        LastSeenAudience.MY_CONTACTS_EXCEPT -> isContact && exceptToken(ownerId, viewerId) !in lastSeenExcept
        LastSeenAudience.NOBODY -> false
    }

    /** Whether [ownerId] shows "online" to [viewerId]. */
    fun sharesOnlineWith(ownerId: String, viewerId: String, isContact: Boolean): Boolean =
        online == OnlineAudience.EVERYONE || sharesLastSeenWith(ownerId, viewerId, isContact)

    /** False when nobody at all may see this user's presence, so it needn't be published. */
    val sharesWithAnyone: Boolean
        get() = online == OnlineAudience.EVERYONE || lastSeen != LastSeenAudience.NOBODY

    internal fun toJson(): JsonObject = buildJsonObject {
        put(KEY_LAST_SEEN, lastSeen.key)
        put(KEY_ONLINE, online.key)
        if (lastSeen == LastSeenAudience.MY_CONTACTS_EXCEPT && lastSeenExcept.isNotEmpty()) {
            putJsonArray(KEY_EXCEPT) { lastSeenExcept.sorted().forEach { add(JsonPrimitive(it)) } }
        }
    }

    companion object {
        /** Key of this object inside the Wallet-API `settings` JSON. */
        internal const val SETTINGS_KEY = "presencePrivacy"
        private const val KEY_LAST_SEEN = "lastSeen"
        private const val KEY_ONLINE = "online"
        private const val KEY_EXCEPT = "lastSeenExcept"

        /**
         * Stable, non-reversible stand-in for "[ownerId] excludes [viewerId]". Salted with the
         * owner so the same viewer gets a different token in every user's list.
         */
        fun exceptToken(ownerId: String, viewerId: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("clariva-last-seen|$ownerId|$viewerId".toByteArray())
            return digest.take(16).joinToString("") { "%02x".format(it) }
        }

        /** Parses a stored value; anything missing or unrecognised falls back to "Everyone". */
        internal fun fromJson(element: JsonElement?): PresencePrivacy {
            val obj = element as? JsonObject ?: return PresencePrivacy()
            val lastSeenKey = (obj[KEY_LAST_SEEN] as? JsonPrimitive)?.content
            val onlineKey = (obj[KEY_ONLINE] as? JsonPrimitive)?.content
            return PresencePrivacy(
                lastSeen = LastSeenAudience.entries.firstOrNull { it.key == lastSeenKey } ?: LastSeenAudience.EVERYONE,
                online = OnlineAudience.entries.firstOrNull { it.key == onlineKey } ?: OnlineAudience.EVERYONE,
                lastSeenExcept = (obj[KEY_EXCEPT] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?.toSet()
                    .orEmpty(),
            )
        }
    }
}
