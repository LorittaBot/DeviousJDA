/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.dv8tion.jda.api.entities.channel.concrete;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the connection used for direct messaging.
 *
 * <p>This channel may communicate with different users in interactions triggered by user-installed apps:
 * <ul>
 *     <li>In bot DMs, this channel will send messages to {@link net.dv8tion.jda.api.JDA#getSelfUser() this bot}.</li>
 *     <li>In friend DMs, this channel will send messages to that friend,
 *         from the bot itself, this is different from where the interaction was executed.
 *         <br>Note: As friend DMs are detached channels, you will need to {@linkplain #retrieveOpenPrivateChannel() retrieve an open channel first}.
 *     </li>
 * </ul>
 *
 * @see User#openPrivateChannel()
 * @see #retrieveOpenPrivateChannel()
 */
public interface PrivateChannel extends MessageChannel
{
    /**
     * The {@link net.dv8tion.jda.api.entities.User User} that this {@link PrivateChannel PrivateChannel} communicates with.
     *
     * <p>This user is only null if this channel is currently uncached, and one the following occur:
     * <ul>
     *     <li>A reaction is removed</li>
     *     <li>A reaction is added</li>
     *     <li>A message is deleted</li>
     *     <li>This account sends a message to a user from another shard (not shard 0)</li>
     *     <li>This account receives an interaction response, happens when using an user-installed interaction</li>
     * </ul>
     * The consequence of this is that for any message this bot receives from a guild or from other users, the user will not be null.
     *
     * <br>In order to retrieve a user that is null, use {@link #retrieveUser()}
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.User User}.
     *
     * @see #retrieveUser()
     */
    @Nullable
    User getUser();

    /**
     * Retrieves the {@link User User} that this {@link PrivateChannel PrivateChannel} communicates with.
     *
     * <br>This method fetches the channel from the API and retrieves the User from that.
     *
     * @return A {@link RestAction RestAction} to retrieve the {@link User User} that this {@link PrivateChannel PrivateChannel} communicates with.
     */
    @Nonnull
    @CheckReturnValue
    RestAction<User> retrieveUser();

    /**
     * Retrieves a {@link PrivateChannel} that is guaranteed to be open.
     * <br>For detached {@link PrivateChannel PrivateChannels},
     * it essentially transforms this into an attached {@link PrivateChannel}.
     *
     * <p>This is only useful for interactions started in Friend DMs (with user-installed apps),
     * as this will return a valid channel to communicate between your bot and the friend,
     * not between the interaction's caller and the friend.
     *
     * <p><b>Note:</b> Open private channels does not imply you can successfully send messages to the recipient.
     *
     * @throws net.dv8tion.jda.api.exceptions.DetachedEntityException
     *         If this channel is detached and {@link #getUser()} returns {@code null},
     *         this only happens if Discord does not send us the recipient, which should not happen.
     *
     * @return A {@link RestAction} to retrieve an open {@link PrivateChannel}.
     */
    @Nonnull
    @CheckReturnValue
    RestAction<PrivateChannel> retrieveOpenPrivateChannel();

    /**
     * The human-readable name of this channel.
     *
     * <p>If getUser returns null, this method will return an empty String.
     * This happens when JDA does not have enough information to populate the channel name.
     *
     * <p>This will occur only when {@link #getUser()} is null, and the reasons are given in {@link #getUser()}
     *
     * <p>If the channel name is important, {@link #retrieveUser()} should be used, instead.
     *
     * @return The name of this channel
     *
     * @see #retrieveUser()
     * @see #getUser()
     */
    @Nonnull
    @Override
    String getName();
}
