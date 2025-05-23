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

package net.dv8tion.jda.api.components.filedisplay;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.ResolvedMedia;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.dv8tion.jda.internal.components.filedisplay.FileDisplayFileUpload;
import net.dv8tion.jda.internal.utils.Checks;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Component displaying a file, you can mark it as a spoiler.
 *
 * <p><b>Note:</b> Audio files and text files cannot be <i>previewed</i>,
 * use {@linkplain net.dv8tion.jda.api.components.mediagallery.MediaGallery media galleries} to display images.
 *
 * <p><b>Requirements:</b> {@linkplain MessageRequest#useComponentsV2() Components V2} needs to be enabled!
 */
public interface FileDisplay extends Component, MessageTopLevelComponent, ContainerChildComponent
{
    /**
     * Constructs a new {@link FileDisplay} from the {@link FileUpload}.
     *
     * <p>This will automatically add the file when building the message,
     * as such you do not need to add it manually (with {@link MessageCreateBuilder#addFiles(FileUpload...)} for example).
     *
     * @param  file
     *         The {@link FileUpload} to display
     *
     * @throws IllegalArgumentException
     *         If {@code null} is provided
     *
     * @return The new {@link FileDisplay}
     */
    @Nonnull
    static FileDisplay fromFile(@Nonnull FileUpload file)
    {
        Checks.notNull(file, "FileUpload");
        return new FileDisplayFileUpload(file);
    }

    @Nonnull
    @Override
    @CheckReturnValue
    FileDisplay withUniqueId(int uniqueId);

    /**
     * Creates a new {@link FileDisplay} with the provided spoiler status.
     * <br>Spoilers are hidden until the user clicks on it.
     *
     * @param  spoiler
     *         The new spoiler status
     *
     * @return The new {@link FileDisplay}
     */
    @Nonnull
    @CheckReturnValue
    FileDisplay withSpoiler(boolean spoiler);

    /**
     * The URL of this file, this is always where the file originally came from.
     * <br>This can be either {@code attachment://filename.extension} or an actual URL.
     *
     * <p>If you want to download the file, you should use {@link #getResolvedMedia()} then {@link ResolvedMedia#getProxy()},
     * to avoid connecting your bot to unknown servers.
     *
     * @return The URL of this file
     */
    @Nonnull
    String getUrl();

    /**
     * The media resolved from this file, this is only available if you receive this component from Discord.
     *
     * @return Possibly-null {@link ResolvedMedia}
     */
    @Nullable
    ResolvedMedia getResolvedMedia();

    /**
     * Whether this file is hidden until the user clicks on it.
     *
     * @return {@code true} if this is hidden by default, {@code false} otherwise
     */
    boolean isSpoiler();
}
