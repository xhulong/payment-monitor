package com.example.paymentmonitor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paymentmonitor.UiTestHostActivity
import com.example.paymentmonitor.debug.DebugFixtureFactory
import com.example.paymentmonitor.debug.DebugFixtureType
import com.example.paymentmonitor.monitor.PaymentNotificationParser
import com.example.paymentmonitor.ui.theme.PaymentMonitorTheme
import com.example.paymentmonitor.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<UiTestHostActivity>()

    @Test
    fun floatingNavigationSwitchesAllThreeTabs() {
        composeRule.setContent {
            var selected by remember { mutableIntStateOf(0) }
            PaymentMonitorTheme(darkTheme = true) {
                GlassStyleProvider(darkTheme = true) {
                    Column {
                        FloatingGlassNavigation(
                            items = listOf(
                                GlassNavigationItem("监听", Icons.Rounded.NotificationsActive, "tab-monitor"),
                                GlassNavigationItem("同步", Icons.Rounded.Sync, "tab-sync"),
                                GlassNavigationItem("记录", Icons.Rounded.ReceiptLong, "tab-events"),
                            ),
                            selectedIndex = selected,
                            onSelected = { selected = it },
                        )
                        Text(listOf("监听页面", "同步页面", "记录页面")[selected])
                    }
                }
            }
        }

        composeRule.onNodeWithTag("tab-monitor").assertIsDisplayed()
        composeRule.onNodeWithTag("tab-sync").performClick()
        composeRule.onNodeWithText("同步页面").assertIsDisplayed()
        composeRule.onNodeWithTag("tab-events").performClick()
        composeRule.onNodeWithText("记录页面").assertIsDisplayed()
    }

    @Test
    fun themeButtonCyclesAndPrimaryButtonMeetsTouchTarget() {
        composeRule.setContent {
            var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var started by remember { mutableStateOf(false) }
            PaymentMonitorTheme(darkTheme = false) {
                GlassStyleProvider(darkTheme = false) {
                    Column {
                        ThemeModeButton(mode = mode, onClick = { mode = mode.next() })
                        GlassButton(
                            text = if (started) "停止监听" else "开始监听",
                            onClick = { started = !started },
                            modifier = Modifier.fillMaxWidth().testTag("monitor-primary-action"),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("主题：跟随系统").performClick()
        composeRule.onNodeWithContentDescription("主题：浅色").assertIsDisplayed()
        composeRule.onNodeWithTag("monitor-primary-action")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("停止监听").assertIsDisplayed()
    }

    @Test
    fun filtersAnimateSelectionAndAdvancedToolsCollapse() {
        composeRule.setContent {
            var platform by remember { mutableStateOf(PlatformFilter.ALL) }
            var expanded by remember { mutableStateOf(false) }
            PaymentMonitorTheme(darkTheme = true) {
                GlassStyleProvider(darkTheme = true) {
                    Column {
                        GlassFilterRow(
                            entries = PlatformFilter.entries,
                            selected = platform,
                            onSelect = { platform = it },
                            label = {
                                when (it) {
                                    PlatformFilter.ALL -> "全部平台"
                                    PlatformFilter.WECHAT -> "微信"
                                    PlatformFilter.ALIPAY -> "支付宝"
                                }
                            },
                        )
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().testTag("debug-tools"),
                            onClick = { expanded = !expanded },
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("开发工具")
                                AnimatedVisibility(expanded) {
                                    Text("支付测试样本", Modifier.testTag("debug-tools-content"))
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("微信").performClick().assertIsSelected()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("debug-tools").performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(
            testTag = "debug-tools-content",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun paymentEventUsesGlassDetailSheet() {
        val raw = DebugFixtureFactory.create(DebugFixtureType.WECHAT_INCOME)
        val event = requireNotNull(PaymentNotificationParser.parse(raw))
        composeRule.setContent {
            PaymentMonitorTheme(darkTheme = false) {
                GlassStyleProvider(darkTheme = false) {
                    EventDetailSheet(
                        event = event,
                        onDismiss = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("event-detail-sheet").assertIsDisplayed()
        composeRule.onNodeWithText("支付通知详情").assertIsDisplayed()
        composeRule.onNodeWithText("微信").assertIsDisplayed()
    }
}
