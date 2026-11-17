package com.flipcash.app.scanner

import androidx.activity.compose.ReportDrawn
import androidx.compose.runtime.Composable
import com.flipcash.app.scanner.internal.Scanner

/**
 * @param onClose leaves the scanner, when it is drawn without the tab bar (the Group Money shell has
 * no tab for it); it draws a leading close. Null when the scanner is a tab, as it is today.
 */
@Composable
fun ScannerScreen(onClose: (() -> Unit)? = null) {
    // Only a report for the launch that lands *here* -- a deeplink into scan, or scan as the
    // restored tab. Through the reporter rather than Activity.reportFullyDrawn(), so that arriving
    // on this tab later cannot close a report another destination is still holding open.
    ReportDrawn()
    Scanner(onClose = onClose)
}
