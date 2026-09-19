package com.flipcash.app.scanner

import androidx.activity.compose.ReportDrawn
import androidx.compose.runtime.Composable
import com.flipcash.app.scanner.internal.Scanner

@Composable
fun ScannerScreen() {
    // Only a report for the launch that lands *here* -- a deeplink into scan, or scan as the
    // restored tab. Through the reporter rather than Activity.reportFullyDrawn(), so that arriving
    // on this tab later cannot close a report another destination is still holding open.
    ReportDrawn()
    Scanner()
}
