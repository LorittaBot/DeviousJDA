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

package net.dv8tion.jda.api.interactions.components;

import net.dv8tion.jda.annotations.ForRemoval;
import net.dv8tion.jda.annotations.ReplaceWith;
import net.dv8tion.jda.api.components.ActionComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;

/**
 * Component which can be inserted into a {@link LayoutComponent}.
 *
 * @see ActionComponent
 * @see ActionRow
 */
@ForRemoval
@Deprecated
@ReplaceWith("ActionRowChildComponent")
public interface ItemComponent extends ActionRowChildComponent
{
    /**
     * How many of these components can be added to one {@link ActionRow}.
     *
     * @return The maximum amount an action row can contain
     */
    default int getMaxPerRow()
    {
        return getType().getMaxPerRow();
    }
}
