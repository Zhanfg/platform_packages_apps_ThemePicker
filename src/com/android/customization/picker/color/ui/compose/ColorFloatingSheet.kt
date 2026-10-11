/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.customization.picker.color.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation.Horizontal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.bounceable
import com.android.compose.theme.PlatformTheme
import com.android.customization.model.color.ColorOption
import com.android.customization.picker.color.shared.model.ColorType
import com.android.customization.picker.color.ui.viewmodel.ColorOptionIconViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorOptionViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.mode.ui.viewmodel.DarkModeViewModel
import com.android.systemui.monet.ColorScheme
import com.android.themepicker.R
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ColorFloatingSheet(
    darkModeViewModel: DarkModeViewModel,
    colorPickerViewModel: ColorPickerViewModel,
    modifier: Modifier = Modifier,
) {
    val previewingColorOption: ColorOption? by
        colorPickerViewModel.tempPreviewingColorOption.collectAsStateWithLifecycle(
            initialValue = null
        )
    val previewingIsDarkMode: Boolean by
        darkModeViewModel.previewingIsDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val screen: ColorPickerViewModel.Screen by
        colorPickerViewModel.currentScreen.collectAsStateWithLifecycle()
    val toggleIsDarkMode: () -> Unit by
        darkModeViewModel.toggleDarkMode.collectAsStateWithLifecycle(initialValue = {})
    val isDarkModeToggleEnabled: Boolean by
        darkModeViewModel.isEnabled.collectAsStateWithLifecycle(initialValue = false)
    val colorSeedOptions: List<Pair<ColorType, List<ColorOptionViewModel>>> by
        colorPickerViewModel.colorSeedOptions.collectAsStateWithLifecycle(
            initialValue = emptyList()
        )
    val previewingColorOptionKey: String? by
        colorPickerViewModel.previewingColorOptionKey.collectAsStateWithLifecycle(
            initialValue = null
        )
    val previewingStyle: Int? by
        colorPickerViewModel.previewingStyle.collectAsStateWithLifecycle(initialValue = null)
    val styleOptions = colorPickerViewModel.styleOptions.map { StyleBounceable(it) }
    val hueSliderPosition: Float by
        colorPickerViewModel.hueSliderPosition.collectAsStateWithLifecycle(
            initialValue = ColorPickerViewModel.HUE_INIT_VALUE
        )

    PlatformTheme {
        val scheme =
            remember(previewingColorOption, previewingIsDarkMode, previewingStyle) {
                previewingColorOption?.let {
                    ColorScheme(it.seedColor, previewingIsDarkMode, previewingStyle ?: it.style)
                        .materialScheme
                }
            }

        ColorPreviewTheme(scheme) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { value ->
                when (value) {
                    ColorPickerViewModel.Screen.LANDING ->
                        ColorFloatingSheetLanding(
                            isDarkMode = previewingIsDarkMode,
                            toggleIsDarkMode = toggleIsDarkMode,
                            isDarkModeToggleEnabled = isDarkModeToggleEnabled,
                            colorSeedOptions = colorSeedOptions,
                            selectedColorSeedKey = previewingColorOptionKey,
                            navigateToVariantPicker = {
                                colorPickerViewModel.setScreen(
                                    ColorPickerViewModel.Screen.VARIANT_PICKER
                                )
                            },
                            navigateToFreeformPicker = {
                                colorPickerViewModel.setScreen(
                                    ColorPickerViewModel.Screen.FREEFORM_PICKER
                                )
                            },
                            modifier = modifier,
                        )
                    ColorPickerViewModel.Screen.VARIANT_PICKER ->
                        ColorVariantPicker(
                            styleOptions = styleOptions,
                            selectedOption = previewingStyle,
                            previewingSeedColor = previewingColorOption?.seedColor,
                            previewingIsDarkMode = previewingIsDarkMode,
                            onClick = colorPickerViewModel::onStyleOptionClick,
                            onCancel = colorPickerViewModel::cancelStyleOptionSelection,
                            onConfirm = colorPickerViewModel::confirmStyleOptionSelection,
                            navigateToLanding = {
                                colorPickerViewModel.setScreen(ColorPickerViewModel.Screen.LANDING)
                            },
                            modifier = modifier,
                        )
                    ColorPickerViewModel.Screen.FREEFORM_PICKER ->
                        FreeformColorPicker(
                            hueSliderPosition = hueSliderPosition,
                            onHueChange = colorPickerViewModel::updateHue,
                            onCancel = colorPickerViewModel::cancelFreeformColor,
                            onConfirm = colorPickerViewModel::confirmFreeformColor,
                            navigateToLanding = {
                                colorPickerViewModel.setScreen(ColorPickerViewModel.Screen.LANDING)
                            },
                            modifier = modifier,
                        )
                }
            }
        }
    }
}

@Composable
fun ColorFloatingSheetLanding(
    isDarkMode: Boolean,
    toggleIsDarkMode: () -> Unit,
    isDarkModeToggleEnabled: Boolean,
    colorSeedOptions: List<Pair<ColorType, List<ColorOptionViewModel>>>,
    selectedColorSeedKey: String?,
    navigateToVariantPicker: () -> Unit,
    navigateToFreeformPicker: () -> Unit,
    modifier: Modifier,
) {
    val colorScheme: CustomColorScheme = LocalAnimatedColorScheme.current
    val lazyListState: LazyListState = rememberLazyListState()
    val textResId: Int by remember {
        derivedStateOf {
            val firstVisibleIndex = lazyListState.firstVisibleItemIndex
            var startIdx = 0
            var endIdx = 0
            for (colorTypeToOptions in colorSeedOptions) {
                endIdx += colorTypeToOptions.second.size
                if (firstVisibleIndex in startIdx..<endIdx) {
                    return@derivedStateOf when (colorTypeToOptions.first) {
                        ColorType.WALLPAPER_COLOR -> R.string.wallpaper_color_title_2
                        ColorType.PRESET_COLOR -> R.string.preset_color_tab_2
                        ColorType.FREEFORM_COLOR -> R.string.freeform_color_title
                    }
                } else {
                    startIdx = endIdx
                    // Account for the divider added between color types, and count it as the next
                    // group
                    endIdx += 1
                }
            }
            return@derivedStateOf R.string.wallpaper_color_tab
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(shape = RoundedCornerShape(28.dp))
                .drawBehind { drawRect(colorScheme.surfaceBright) }
    ) {
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = textResId,
                    transitionSpec = {
                        fadeIn(
                            animationSpec = tween(durationMillis = 200, delayMillis = 200)
                        ) togetherWith fadeOut(animationSpec = tween(durationMillis = 200))
                    },
                ) { text ->
                    Text(
                        text = stringResource(text),
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                IconButton(
                    modifier = Modifier.size(32.dp),
                    onClick = navigateToFreeformPicker,
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = colorScheme.surfaceDim,
                            contentColor = colorScheme.onSurface,
                        ),
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(R.drawable.ic_palette_filled_24px),
                        // The palette icon opens custom color selection.
                        // Use the existing localized TalkBack description.
                        contentDescription =
                            stringResource(R.string.accessibility_custom_color_title),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TODO (b/441279631): implement scroll to selected color option
            LazyRow(
                state = lazyListState,
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier.semantics { testTagsAsResourceId = true }
                        .testTag("com.google.android.apps.wallpaper:id/colors_horizontal_list"),
            ) {
                colorSeedOptions.forEachIndexed { colorTypeIdx, colorTypeToOptions ->
                    val colorList = colorTypeToOptions.second
                    if (colorTypeIdx != 0 && colorList.isNotEmpty()) {
                        item { OptionListGroupDivider() }
                    }
                    itemsIndexed(colorList) { idx, option ->
                        ColorSeedOption(
                            isDarkMode = isDarkMode,
                            optionItem = option,
                            isSelected = option.key == selectedColorSeedKey,
                            navigateToVariantPicker = navigateToVariantPicker,
                            modifier =
                                Modifier.size(
                                        dimensionResource(R.dimen.floating_sheet_color_option_size)
                                    )
                                    .bounceable(
                                        bounceable = colorList[idx],
                                        previousBounceable =
                                            if (idx > 0) colorList[idx - 1] else null,
                                        nextBounceable =
                                            if (idx < colorList.lastIndex) colorList[idx + 1]
                                            else null,
                                        orientation = Horizontal,
                                    ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.mode_title),
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )

                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { toggleIsDarkMode() },
                    enabled = isDarkModeToggleEnabled,
                    thumbContent =
                        if (isDarkMode) {
                            {
                                Icon(
                                    painter =
                                        painterResource(
                                            com.android.wallpaper.R.drawable.ic_check_wallpaper
                                        ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
fun OptionListGroupDivider(modifier: Modifier = Modifier) {
    val colorScheme: CustomColorScheme = LocalAnimatedColorScheme.current
    Box(
        modifier =
            modifier
                .width(12.dp)
                .height(28.dp)
                .padding(horizontal = 5.dp)
                .drawBehind {
                    drawRoundRect(
                        color = colorScheme.onSurfaceVariant,
                        cornerRadius = CornerRadius(x = 1.dp.toPx(), y = 1.dp.toPx()),
                    )
                }
                .semantics { hideFromAccessibility() }
    )
}

@Composable
fun ColorSeedOption(
    isDarkMode: Boolean,
    optionItem: ColorOptionViewModel,
    isSelected: Boolean,
    navigateToVariantPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme: CustomColorScheme = LocalAnimatedColorScheme.current
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val colorIcon: ColorOptionIconViewModel = optionItem.icon
    val enableVariantPicker: Boolean = isSelected && optionItem.enableDrillDown

    Box(modifier = modifier) {
        ColorOption(
            isSelected = isSelected,
            onClick = {
                if (enableVariantPicker) {
                    navigateToVariantPicker()
                } else if (!isSelected) {
                    optionItem.onClick?.invoke()
                    coroutineScope.launch { optionItem.clickBounceAnimate() }
                }
            },
        ) {
            // Round up width to prevent empty pixels between quadrants in bounce
            // animation.
            val quadrantSize = Size(ceil(size.width / 2f), size.height / 2f)
            onDrawBehind {
                colorIcon?.let {
                    drawRect(
                        color =
                            if (isDarkMode) {
                                Color(it.darkThemeColor0)
                            } else {
                                Color(it.lightThemeColor0)
                            },
                        size = quadrantSize,
                    )
                    drawRect(
                        color =
                            if (isDarkMode) {
                                Color(it.darkThemeColor1)
                            } else {
                                Color(it.lightThemeColor1)
                            },
                        topLeft = Offset(x = size.width / 2f, y = 0f),
                        size = quadrantSize,
                    )
                    drawRect(
                        color =
                            if (isDarkMode) {
                                Color(it.darkThemeColor2)
                            } else {
                                Color(it.lightThemeColor2)
                            },
                        topLeft = Offset(x = 0f, y = size.height / 2f),
                        size = quadrantSize,
                    )
                    drawRect(
                        color =
                            if (isDarkMode) {
                                Color(it.darkThemeColor3)
                            } else {
                                Color(it.lightThemeColor3)
                            },
                        topLeft = Offset(x = size.width / 2f, y = size.height / 2f),
                        size = quadrantSize,
                    )
                }
            }
        }

        if (enableVariantPicker) {
            // Edit icon
            Box(
                modifier =
                    Modifier.size(dimensionResource(R.dimen.floating_sheet_clock_edit_icon_size))
                        .align(Alignment.TopEnd)
                        .offset(x = 15.dp, y = (-15).dp)
                        .drawBehind {
                            drawCircle(color = colorScheme.surfaceBright, radius = 18.dp.toPx())
                            drawCircle(
                                color = colorScheme.onPrimaryFixedVariant,
                                radius = 14.dp.toPx(),
                            )
                        }
            ) {
                Icon(
                    modifier = Modifier.fillMaxSize(),
                    painter = painterResource(R.drawable.edit_icon_foreground),
                    tint = Color.White,
                    contentDescription = null,
                )
            }
        }
    }
}
