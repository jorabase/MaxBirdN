# Implementation Plan - PDF Slide Presentation & Download Optimization

## Overview
1. **PDF Slide Presentation Mode**: Ensure `SlideViewerDialog` in horizontal slide mode (`PDF_SLIDES`) displays **exactly one slide per screen** occupying 100% of the physical screen edge-to-edge (hiding system navigation bars completely in landscape/fullscreen) with zero cutting at top/bottom/sides.
2. **Download Speed Optimization**: Optimize `AppFileDownloadManager` with OkHttpClient tuning (larger connection pool, increased read/write timeout, buffered streams) for fast concurrent downloading.
3. **Download Quality Dialog Redesign**: Remove any dummy/placeholder text in `LessonDetailPlayerScreen`'s download dialog, replace with a clean loading state if fetching options, and redesign the UI into a modern, polished Material 3 card without unnecessary clutter.

## User Requirements Addressed
- "নেভিগেশন বার লুকানো থাকবে আর স্লাইড টা সম্পুর্ন স্কিন জুরে ফিট হবে মানে এক একটি স্কিন এ একটি একটি স্লাইড ই থাকবে সম্পুর্ন ফিট ভাবে" -> Hide system nav bar, 1 slide per screen, 100% fit edge-to-edge.
- "ডাউনলোড স্পিড এত ধিরে কেন" -> Optimize download connection and buffer size.
- "ডাউনলোড অপশন এ ক্লিক করার পর প্রথম এ ডেমো রেজুলেশন দেখায় এরপর আপডেট হয়... কোনো ডামি থাকবে না" -> Eliminate dummy placeholders, show loading state.
- "ইউ আই ডিজাইন টা উন্নত করেন। মানে সুন্দর ডিজাইন." -> Clean modern M3 UI redesign for download dialog.

## Proposed Changes
### 1. Slide Viewer Dialog (`SlideViewerDialog.kt`)
- Force full immersive window flags (`WindowCompat.setDecorFitsSystemWindows`, hide system bars) on both Dialog window and Activity window when in landscape or fullscreen.
- Ensure `HorizontalPager` takes `Modifier.fillMaxSize()` with 0dp margins, rendering each page with `ContentScale.Fit` filling 100% of width and height without clipping.
- Hide top/bottom controls automatically or ensure bottom dock does not overlap slide content.

### 2. Download Manager (`AppFileDownloadManager.kt`)
- Tune OkHttpClient with connection pooling, timeouts, and optimized chunk buffer size (`8192` bytes) for maximum network throughput.

### 3. Download Dialog (`LessonDetailPlayerScreen.kt`)
- Remove placeholder text and dummy resolutions.
- Show instant clean loading indicator if options are loading.
- Redesign the bottom sheet/dialog UI with M3 cards, clear typography, and fast action buttons.

## Verification Plan
- Compile applet with `compile_applet`.
- Verify successful build.
