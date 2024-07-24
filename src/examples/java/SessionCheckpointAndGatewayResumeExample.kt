import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelFlag
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.*
import net.dv8tion.jda.api.entities.channel.concrete.*
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.PreProcessedRawGatewayEvent
import net.dv8tion.jda.api.events.StatusChangeEvent
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.SelfUserImpl
import net.dv8tion.jda.internal.requests.WebSocketCode
import org.slf4j.LoggerFactory
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Bot example with session checkpoint and gateway resumes after a bot restart.
 *
 * **How this works?**
 * On startup, we check if we have the shards' session data stored on `cache/shardId/`, if yes, we use the session data on startup, if not, we do a full identify.
 *
 * Fake `READY` events are dispatched to JDA when the `CONNECTING_TO_WEBSOCKET` status is triggered.
 * This event causes JDA to know that we already *have* a created session, and causes it to `RESUME` instead of `IDENTIFY`ing.
 *
 * After doing this, we wait until the `RESUMED` event has been received. Because the `RESUMED` event is sent AFTER all events have been replayed, we need to store the received
 * events BEFORE the `RESUMED` events in a replay queue.
 *
 * When the `RESUMED` event has been received, the WebSocket reading thread will be blocked while we read the data from the disk and, after reading it, we submit the data from disk
 * to JDA as if they were `GUILD_CREATE` events, so JDA handles it as if it was being received from Discord! Just like Yoru's fakeout ability, huh?
 * (I may or may have not been playing too much VALORANT)
 *
 * When all fake `GUILD_CREATE` events have been sent, we replay the received events, and that's it! :3
 *
 * On shutdown, we write all the guild and session data to disk.
 *
 * While this example stores the data on disk, you could be more creative and do stuff like:
 * * Storing the state to Redis, so you don't need to have a persistent storage
 * * Making a "hand off" process, by booting another instance of your bot, handshaking with the previous instance, and then streaming the cache from the old instance to the new instance
 *
 * This implementation could be less "hacky", but I didn't want to change JDA *too* much.
 *
 * If you have noticed, this is similar to how [Discord Gateway Proxies](https://github.com/Gelbpunkt/gateway-proxy) works behind the scenes! However, in this case we don't need to keep a
 * persistent gateway connection open, nor do we need to cache the data on a different process (which would effectively 2x your memory usage).
 *
 * You can also make your own custom SessionControllerAdapter that prioritizes shards that are resuming, this way the chances of the session being invalidated are lower!
 */
object SessionCheckpointAndGatewayResumeExample {
    private val logger = LoggerFactory.getLogger(SessionCheckpointAndGatewayResumeExample::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val shards = 0..0
        val cacheFolder = File("cache")
        cacheFolder.mkdirs()

        val initialSessions = mutableMapOf<Int, GatewaySessionData>()
        val previousVersionKeyFile = File(cacheFolder, "version")
        if (previousVersionKeyFile.exists()) {
            val previousVersion = UUID.fromString(previousVersionKeyFile.readText())
            for (shard in shards) {
                try {
                    val shardCacheFolder = File(cacheFolder, shard.toString())
                    val sessionFile = File(shardCacheFolder, "session.json")
                    val cacheVersionKeyFile = File(shardCacheFolder, "version")
                    // Does not exist, so bail out
                    if (!cacheVersionKeyFile.exists()) {
                        logger.warn("Couldn't load shard $shard cached data because the version file does not exist!")
                        continue
                    }

                    val cacheVersion = UUID.fromString(cacheVersionKeyFile.readText())
                    // Only load the data if the version matches
                    if (cacheVersion == previousVersion) {
                        if (sessionFile.exists()) {
                            val sessionData = if (sessionFile.exists()) Json.decodeFromString<GatewaySessionData>(sessionFile.readText()) else null
                            if (sessionData != null)
                                initialSessions[shard] = sessionData
                        }
                    } else {
                        logger.warn("Couldn't load shard $shard cached data because the cache version does not match!")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to load shard $shard cached data!")
                }
            }
        }

        val shardManager = DefaultShardManagerBuilder.createDefault(File("token.txt").readText().lines()[0])
                .setShardsTotal(shards.last + 1)
                .setShards(shards.toList())
                // Required for PreProcessedRawGatewayEvent
                .setRawEventsEnabled(true)
                .addEventListenerProvider { shardId ->
                    PreStartGatewayEventReplayListener(initialSessions[shardId], cacheFolder)
                }
                // We want to override JDA's shutdown hook to store the cache on disk when shutting down
                .setEnableShutdownHook(false)
                .build()

        Runtime.getRuntime().addShutdownHook(
                thread(false) {
                    // This is used to validate if our cache was successfully written or not
                    val connectionVersion = UUID.randomUUID()

                    File(cacheFolder, "version").writeText(connectionVersion.toString())

                    val jobs = shardManager.shards.map { shard ->
                        GlobalScope.async(Dispatchers.IO) {
                            val jdaImpl = shard as JDAImpl
                            val sessionId = jdaImpl.client.sessionId
                            val resumeUrl = jdaImpl.client.resumeUrl

                            // Only get connected shards, invalidate everything else
                            if (shard.status != JDA.Status.CONNECTED || sessionId == null || resumeUrl == null) {
                                // Not connected, shut down and invalidate our cached data
                                shard.shutdownNow(1000) // We don't care about persisting our gateway session
                                File(cacheFolder, shard.shardInfo.shardId.toString()).deleteRecursively()
                            } else {
                                // Indicate on our presence that we are restarting
                                jdaImpl.presence.setPresence(OnlineStatus.IDLE, Activity.playing("\uD83D\uDE34 Loritta is restarting..."))

                                // We need to wait until JDA *really* sends the presence
                                Thread.sleep(1_000)

                                // Connected, store to the cache
                                // Using close code 1012 does not invalidate your gateway session!
                                shard.shutdownNow(1012)

                                val shardCacheFolder = File(cacheFolder, shard.shardInfo.shardId.toString())

                                // Delete the current cached data for this shard
                                shardCacheFolder.deleteRecursively()

                                // Create the shard cache folder
                                shardCacheFolder.mkdirs()

                                val guildsCacheFile = File(shardCacheFolder, "guilds.json")
                                val sessionCacheFile = File(shardCacheFolder, "session.json")
                                val versionFile = File(shardCacheFolder, "version")

                                val guildIdsForReadyEvent = jdaImpl.guildsView.map { it.idLong } + jdaImpl.unavailableGuilds.map { it.toLong() }

                                guildsCacheFile.bufferedWriter().use { bufWriter ->
                                    for (guild in jdaImpl.guildsView) {
                                        bufWriter.appendLine(DeviousConverter.toJson(guild).toString())
                                        // Remove the guild from memory, which avoids the bot crashing due to Out Of Memory
                                        jdaImpl.guildsView.remove(guild.idLong)
                                    }
                                }

                                sessionCacheFile
                                        .writeText(
                                                Json.encodeToString(
                                                        GatewaySessionData(
                                                                sessionId,
                                                                resumeUrl,
                                                                jdaImpl.responseTotal,
                                                                guildIdsForReadyEvent
                                                        )
                                                )
                                        )

                                // Only write after everything has been successfully written
                                versionFile.writeText(connectionVersion.toString())
                            }
                        }
                    }

                    runBlocking {
                        jobs.awaitAll()
                    }
                }
        )
    }

    /**
     * Replays gateway events when the session is resumed, this should only be triggered on the first resume event received!
     */
    class PreStartGatewayEventReplayListener(private val initialSession: GatewaySessionData?, private val cacheFolder: File) : ListenerAdapter() {
        companion object {
            private const val FAKE_EVENT_FIELD = "fakeout"
            private val logger = LoggerFactory.getLogger(SessionCheckpointAndGatewayResumeExample::class.java)
        }

        private val replayCache = LinkedBlockingQueue<DataObject>()
        private var state = ProcessorState.WAITING_FOR_WEBSOCKET_CONNECTION

        override fun onPreProcessedRawGateway(event: PreProcessedRawGatewayEvent) {
            if (event.`package`.getBoolean(FAKE_EVENT_FIELD)) {
                // Events that has the "fakeout" field shouldn't be processed by this listener
                logger.info("Received faked out event! We won't process it on the PreStartGatewayEventReplayListener listener...")
                return
            }

            if (state == ProcessorState.FINISHED)
                return

            logger.info("Current event sequence ${event.jda.responseTotal}, received event ${event.`package`.getString("t", "UNKNOWN")}")

            if (state == ProcessorState.WAITING_FOR_RESUME) {
                // This is the first boot of this JDA instance that we sent a faked ready event
                // What we need to do about it:
                // * Have we successfully resumed?
                // * If we have successfully resumed, we need to create an event replay cache that should be replayed after we loaded all the cached data
                when (event.`package`.getInt("op")) {
                    // Only cancel dispatch events, we don't want the gateway connection to timeout due to not sending heartbeats
                    WebSocketCode.DISPATCH -> {
                        if (event.type == "RESUMED") {
                            logger.info("Successfully resumed the gateway connection! Loading cached data...")

                            // No need to send the resumed event to JDA because we have sent our own faked READY event
                            event.isCancelled = true

                            val jdaImpl = event.jda as JDAImpl

                            // Indicate on our presence that we are loading the cached data
                            jdaImpl.presence.setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.playing("\uD83C\uDF6E Loritta is loading... Hang tight!"))

                            // Yoru's Fakeout: Discord Edition
                            // (I may or may have not been playing too much VALORANT)
                            // handleEvent is actually protected, which is why we need to use DeviousJDA for this!
                            // We run the events this way to make it easier for us, since JDA will handle the event correctly after relaying it to JDA (yay!)
                            //
                            // Keep in mind that this will block the reading thread, so you need to be FAST to avoid the gateway connection being invalidated!
                            // Technically you need to load your data in less than ~41.5s (which is the current "heartbeat_interval"), but you can take longer
                            // without the connection dropping out (I tested with 60s, and it also worked, but ymmv)
                            // You could be fancier and make the cache stuff happen on a separate thread while keeping heartbeats through,
                            // but that makes the code harder and confusing.
                            jdaImpl.guildsView.writeLock().use {
                                File(cacheFolder, "${jdaImpl.shardInfo.shardId}/guilds.json").forEachLine {
                                    // Fill the cache out
                                    jdaImpl.client.handleEvent(
                                            DataObject.fromJson(
                                                    """{"op":0,"d":$it,"t":"GUILD_CREATE","$FAKE_EVENT_FIELD":true}"""
                                            )
                                    )
                                }
                            }

                            // Now replay the events!
                            logger.info("Successfully sent faked guild create events!")
                            logger.info("Replaying ${replayCache.size} events...")
                            while (replayCache.isNotEmpty()) {
                                val cachedEvent = replayCache.poll()

                                // Remove sequence from our cached event because we have already manually updated the sequence before
                                val cachedEventWithoutSequence = cachedEvent.remove("s")
                                // Put our fakeout field to avoid triggering our listener when replaying the event, which causes an infinite loop (whoops)
                                cachedEventWithoutSequence.put(FAKE_EVENT_FIELD, true)

                                logger.info("Cached Event Type: ${cachedEvent.getString("t")}")
                                (event.jda as JDAImpl).client.handleEvent(cachedEventWithoutSequence)
                            }
                            state = ProcessorState.FINISHED
                            logger.info("Done!")
                            jdaImpl.presence.setPresence(OnlineStatus.ONLINE, Activity.playing("lori is cute! ʕ•ᴥ•ʔ"))
                            return
                        }

                        if (event.`package`.hasKey("s")) {
                            // Manually update the sequence since using "isCancelled" stops updating the response total, and the current sequence is used for heartbeating
                            val sequence = event.`package`.getInt("s")
                            (event.jda as JDAImpl).setResponseTotal(sequence)
                        }

                        event.isCancelled = true

                        // Add the event to our replay cache, we need to do this because a resume event is AFTER all events were replayed to the client, but we want to apply our faked events BEFORE the replayed events,
                        // to maintain a consistent cache.
                        replayCache.add(event.`package`)
                        return
                    }

                    WebSocketCode.INVALIDATE_SESSION -> {
                        // Session has been invalidated, clear out the replay cache
                        logger.info("Session has been invalidated, clearing out ${replayCache.size} events...")
                        state = ProcessorState.FINISHED
                        replayCache.clear()
                    }
                }
            }
        }

        override fun onStatusChange(event: StatusChangeEvent) {
            if (state == ProcessorState.FINISHED)
                return

            if (event.newStatus == JDA.Status.CONNECTING_TO_WEBSOCKET) {
                if (initialSession != null) {
                    logger.info("Connecting to WebSocket, sending faked READY event...")

                    val jdaImpl = event.jda as JDAImpl

                    // Update the current event sequence for resume
                    jdaImpl.setResponseTotal(initialSession.sequence.toInt())

                    // Send a fake READY event
                    jdaImpl.client.handleEvent(
                            DataObject.fromJson(
                                    buildJsonObject {
                                        this.put("op", 0)
                                        this.putJsonObject("d") {
                                            this.putJsonArray("guilds") {
                                                for (guildId in initialSession.guilds) {
                                                    addJsonObject {
                                                        this.put("id", guildId)
                                                        this.put("unavailable", true)
                                                    }
                                                }
                                            }
                                            this.putJsonObject("user") {
                                                put("id", event.jda.selfUser.idLong)
                                                put("username", event.jda.selfUser.name)
                                                put("global_name", event.jda.selfUser.globalName)
                                                put("discriminator", event.jda.selfUser.discriminator)
                                                put("avatar", event.jda.selfUser.avatarId)
                                                put("public_flags", event.jda.selfUser.flagsRaw)
                                                put("bot", event.jda.selfUser.isBot)
                                                put("system", event.jda.selfUser.isSystem)
                                            }
                                            this.putJsonObject("application") {
                                                // This requires the verifyToken to be enabled since we need JDA to query the self user before proceeding
                                                // If you aren't using it, store the bot's app ID somewhere and pass it here instead!
                                                put("id", (event.jda.selfUser as SelfUserImpl).applicationId)
                                            }
                                            this.put("session_id", initialSession.sessionId)
                                            this.put("resume_gateway_url", initialSession.resumeGatewayUrl)
                                            // This is always empty
                                            this.putJsonArray("private_channels") {}
                                        }
                                        this.put("t", "READY")
                                        this.put(FAKE_EVENT_FIELD, true)
                                    }.toString()
                            )
                    )
                    state = ProcessorState.WAITING_FOR_RESUME

                    // When JDA connects, it will see that it has a non-null session ID and resume gateway URL, which will trigger a resume state instead of a identify... sweet!
                } else {
                    // We don't have a gateway session, so just skip the gateway event processing shenanigans
                    state = ProcessorState.FINISHED
                }
            }
        }

        enum class ProcessorState {
            WAITING_FOR_WEBSOCKET_CONNECTION,
            WAITING_FOR_RESUME,
            FINISHED
        }
    }

    @Serializable
    data class GatewaySessionData(
            val sessionId: String,
            val resumeGatewayUrl: String,
            val sequence: Long,
            val guilds: List<Long>
    )

    object DeviousConverter {
        /**
         * Converts a [guild] to a Guild Create event
         */
        // TIP: I recommend looking at Loritta's DeviousConverter class, because sometimes I may add new fields there that I forgot to add to this example code
        fun toJson(guild: Guild) = buildJsonObject {
            this.put("id", guild.idLong)
            this.put("name", guild.name)
            this.put("icon", guild.iconId)
            this.put("splash", guild.splashId)
            this.put("owner_id", guild.ownerIdLong)
            this.put("afk_channel_id", guild.afkChannel?.idLong)
            this.put("afk_timeout", guild.afkTimeout.seconds)
            // Unused by JDA: widget_enabled
            // Unused by JDA: widget_channel_id
            this.put("verification_level", guild.verificationLevel.key)
            this.put("default_message_notifications", guild.defaultNotificationLevel.key)
            this.put("explicit_content_filter", guild.explicitContentLevel.key)
            this.put("mfa_level", guild.requiredMFALevel.key)
            // Unused by JDA: application_id
            this.put("system_channel_id", guild.systemChannel?.idLong)
            // Unused by JDA: system_channel_flags
            this.put("rules_channel_id", guild.rulesChannel?.idLong)
            this.put("max_presences", guild.maxPresences)
            this.put("max_members", guild.maxMembers)
            this.put("description", guild.description)
            this.put("banner", guild.bannerId)
            this.put("premium_tier", guild.boostTier.key)
            this.put("premium_subscription_count", guild.boostCount)
            this.put("preferred_locale", guild.locale.locale)
            this.put("public_updates_channel_id", guild.communityUpdatesChannel?.idLong)
            // Unused by JDA: max_video_channel_users
            this.put("nsfw_level", guild.nsfwLevel.key)
            this.put("premium_progress_bar_enabled", guild.isBoostProgressBarEnabled)

            this.putJsonArray("features") {
                for (feature in guild.features) {
                    add(feature)
                }
            }

            // GuildCreate exclusive fields
            // joined_at, doesn't seem to be provided by JDA
            // Unused by JDA: large
            this.put("member_count", guild.memberCount)
            this.putJsonArray("members") {
                for (member in guild.members) {
                    addJsonObject {
                        putJsonObject("user") {
                            val user = member.user

                            put("id", user.idLong)
                            put("username", user.name)
                            put("global_name", user.globalName)
                            put("discriminator", user.discriminator)
                            put("avatar", user.avatarId)
                            put("public_flags", user.flagsRaw)
                            put("bot", user.isBot)
                            put("system", user.isSystem)
                        }
                        put("nick", member.nickname)
                        put("avatar", member.avatarId)
                        if (member.hasTimeJoined())
                            put("joined_at", formatIso(member.timeJoined))
                        if (member.isBoosting)
                            put("premium_since", formatIso(member.timeBoosted))
                        // TODO: deaf
                        // TODO: mute
                        put("pending", member.isPending)
                        put("communication_disabled_until", formatIso(member.timeOutEnd))
                        putJsonArray("roles") {
                            for (role in member.roles) {
                                add(role.idLong)
                            }
                        }
                    }
                }
            }
            this.putJsonArray("roles") {
                for (role in guild.roles) {
                    addJsonObject {
                        put("id", role.idLong)
                        put("name", role.name)
                        put("color", role.colorRaw)
                        put("hoist", role.isHoisted)
                        put("icon", role.icon?.iconId)
                        put("unicode_emoji", role.icon?.emoji)
                        put("position", role.positionRaw)
                        put("permissions", role.permissionsRaw)
                        put("managed", role.isManaged)
                        put("mentionable", role.isMentionable)
                        putJsonObject("tags") {
                            put("bot_id", role.tags.botIdLong)
                            put("integration_id", role.tags.integrationIdLong)
                            put("premium_subscriber", role.tags.isBoost)
                        }
                    }
                }
            }
            this.putJsonArray("channels") {
                for (channel in guild.channels) {
                    addJsonObject {
                        put("id", channel.idLong)
                        put("type", channel.type.id)
                        put("guild_id", channel.guild.idLong)
                        put("name", channel.name)
                        put("flags", ChannelFlag.getRaw(channel.flags))
                        if (channel is IPositionableChannel) {
                            put("position", channel.positionRaw)
                        }
                        if (channel is ICategorizableChannel) {
                            put("parent_id", channel.parentCategoryIdLong)
                        }
                        if (channel is IAgeRestrictedChannel) {
                            put("nsfw", channel.isNSFW)
                        }
                        if (channel is StandardGuildMessageChannel) {
                            put("topic", channel.topic)
                        }
                        if (channel is MessageChannel) {
                            put("last_message_id", channel.latestMessageIdLong)
                        }
                        if (channel is AudioChannel) {
                            put("user_limit", channel.userLimit)
                            put("bitrate", channel.bitrate)
                            put("rtc_region", channel.regionRaw)
                        }
                        if (channel is ISlowmodeChannel) {
                            put("rate_limit_per_user", channel.slowmode)
                        }
                        if (channel is IThreadContainer) {
                            put("default_thread_rate_limit_per_user", channel.defaultThreadSlowmode)
                        }
                        if (channel is ForumChannel) {
                            put("default_forum_layout", channel.defaultLayout.key)
                        }
                        if (channel is IPostContainer) {
                            put("default_sort_order", channel.defaultSortOrder.key)
                            val emoji = channel.defaultReaction
                            if (emoji != null) {
                                putJsonObject("default_reaction_emoji") {
                                    put("emoji_id", if (emoji.type == Emoji.Type.CUSTOM) emoji.asCustom().idLong else null)
                                    put("emoji_name", if (emoji.type == Emoji.Type.UNICODE) emoji.asUnicode().name else null)
                                }
                            }

                            putJsonArray("available_tags") {
                                for (tag in channel.availableTags) {
                                    addJsonObject {
                                        put("id", tag.idLong)
                                        put("name", tag.name)
                                        put("moderated", tag.isModerated)
                                        put("emoji_id", if (emoji?.type == Emoji.Type.CUSTOM) emoji.asCustom().idLong else null)
                                        put("emoji_name", if (emoji?.type == Emoji.Type.UNICODE) emoji.asUnicode().name else null)
                                    }
                                }
                            }
                        }

                        if (channel is IPermissionContainer) {
                            putJsonArray("permission_overwrites") {
                                for (permissionOverwrite in channel.permissionOverrides) {
                                    addJsonObject {
                                        put("id", permissionOverwrite.idLong)
                                        put("type", if (permissionOverwrite.isRoleOverride) 0 else 1)
                                        put("allow", permissionOverwrite.allowedRaw)
                                        put("deny", permissionOverwrite.deniedRaw)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            this.putJsonArray("threads") {
                for (channel in guild.threadChannels) {
                    addJsonObject {
                        put("id", channel.idLong)
                        put("parent_id", channel.parentChannel.idLong)
                        put("type", channel.type.id)
                        put("guild_id", channel.guild.idLong)
                        put("name", channel.name)
                        put("owner_id", channel.ownerIdLong)
                        put("member_count", channel.memberCount)
                        put("message_count", channel.messageCount)
                        put("total_message_sent", channel.totalMessageCount)
                        put("last_message_id", channel.latestMessageIdLong)
                        put("rate_limit_per_user", channel.slowmode)
                        putJsonObject("thread_metadata") {
                            put("locked", channel.isLocked)
                            put("archived", channel.isArchived)
                            if (channel.type == ChannelType.GUILD_PRIVATE_THREAD)
                                put("invitable", channel.isInvitable)
                            put("archive_timestamp", formatIso(channel.timeArchiveInfoLastModified))
                            put("create_timestamp", formatIso(channel.timeCreated))
                            put("auto_archive_duration", channel.autoArchiveDuration.minutes)
                        }
                    }
                }
            }
            // we do not care i repeat we do not care
            this.putJsonArray("guild_scheduled_events") {}
            this.putJsonArray("emojis") {
                for (emoji in guild.emojis) {
                    addJsonObject {
                        put("id", emoji.idLong)
                        put("name", emoji.name)
                        val roles = emoji.roles
                        if (roles.isNotEmpty()) {
                            putJsonArray("roles") {
                                emoji.roles.forEach {
                                    add(it.idLong)
                                }
                            }
                        }
                        // TODO: User (but does it reaaaally matter?)
                        put("user", null)
                        put("animated", emoji.isAnimated)
                        put("managed", emoji.isManaged)
                        put("available", emoji.isAvailable)
                    }
                }
            }
            this.putJsonArray("stickers") {
                for (sticker in guild.stickers) {
                    addJsonObject {
                        put("id", sticker.idLong)
                        put("name", sticker.name)
                        put("format_type", sticker.formatType.id)
                        put("type", sticker.type.id)
                        put("description", sticker.description)
                        putJsonArray("tags") {
                            sticker.tags.forEach {
                                add(it)
                            }
                        }
                        put("available", sticker.isAvailable)
                        put("guild_id", sticker.guildIdLong)
                        // TODO: User (but does it reaaaally matter?)
                        put("user", null)
                    }
                }
            }
            this.putJsonArray("voice_states") {
                for (voiceState in guild.voiceStates) {
                    // Only add users to the voice state list if they are connected in a channel
                    val channelId = voiceState.channel?.idLong ?: continue

                    addJsonObject {
                        put("user_id", voiceState.member.idLong)
                        put("channel_id", channelId)
                        put("request_to_speak_timestamp", formatIso(voiceState.requestToSpeakTimestamp))
                        put("self_mute", voiceState.isSelfMuted)
                        put("self_deaf", voiceState.isSelfDeafened)
                        put("mute", voiceState.isMuted)
                        put("deaf", voiceState.isDeafened)
                        put("suppress", voiceState.isSuppressed)
                        put("session_id", voiceState.sessionId)
                        put("self_stream", voiceState.isStream)
                    }
                }
            }
        }

        private fun formatIso(odt: OffsetDateTime?) = odt?.let { DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(it) }
    }
}
