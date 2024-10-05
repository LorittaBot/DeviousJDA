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

package net.dv8tion.jda.internal.entities.channel.concrete.detached;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion;
import net.dv8tion.jda.api.managers.channel.concrete.NewsChannelManager;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.entities.channel.middleman.AbstractStandardGuildMessageChannelImpl;
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IInteractionPermissionMixin;
import net.dv8tion.jda.internal.entities.channel.mixin.concrete.NewsChannelMixin;
import net.dv8tion.jda.internal.entities.detached.DetachedGuildImpl;
import net.dv8tion.jda.internal.interactions.ChannelInteractionPermissions;

import javax.annotation.Nonnull;
import java.util.List;

public class DetachedNewsChannelImpl extends AbstractStandardGuildMessageChannelImpl<DetachedNewsChannelImpl>
    implements
        NewsChannel,
        DefaultGuildChannelUnion,
        NewsChannelMixin<DetachedNewsChannelImpl>,
        IInteractionPermissionMixin<DetachedNewsChannelImpl>
{
    private ChannelInteractionPermissions interactionPermissions;

    public DetachedNewsChannelImpl(long id, DetachedGuildImpl guild)
    {
        super(id, guild);
    }

    @Override
    public boolean isDetached()
    {
        return true;
    }

    @Nonnull
    @Override
    public ChannelType getType()
    {
        return ChannelType.NEWS;
    }

    @Nonnull
    @Override
    public List<Member> getMembers()
    {
        throw detachedException();
    }

    @Nonnull
    @Override
    public RestAction<Webhook.WebhookReference> follow(@Nonnull String targetChannelId)
    {
        throw detachedException();
    }

    @Nonnull
    @Override
    public NewsChannelManager getManager()
    {
        throw detachedException();
    }

    @Nonnull
    @Override
    public ChannelInteractionPermissions getInteractionPermissions()
    {
        return interactionPermissions;
    }

    @Nonnull
    @Override
    public DetachedNewsChannelImpl setInteractionPermissions(@Nonnull ChannelInteractionPermissions interactionPermissions)
    {
        this.interactionPermissions = interactionPermissions;
        return this;
    }
}
