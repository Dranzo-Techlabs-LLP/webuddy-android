/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Green pill with a shield and a consultant's designation ("Consultant" by default), shown next to
 * their name in the chat list and the chat header. Long designations ellipsize at [maxWidth] so the
 * name keeps its room.
 */
@Composable
fun DesignationBadge(
    text: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 140.dp,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(ElementTheme.colors.bgSuccessSubtle)
            .border(1.dp, ElementTheme.colors.borderSuccessSubtle, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.Shield(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSuccessPrimary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyXsMedium,
            color = ElementTheme.colors.textSuccessPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun DesignationBadgePreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DesignationBadge(text = "Consultant")
        DesignationBadge(text = "Senior Interventional Cardiologist and Researcher")
    }
}
