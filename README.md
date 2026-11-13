# XAVIOUR AI STUDIO

**CREATE. EXPLAIN. EXPLORE.**

A mobile-first, responsive Android application built with **Kotlin**, **Jetpack Compose**, and **Material Design 3** that transforms a single user prompt into a complete 3–15 minute documentary or explainer video using **strictly free-tier Google AI models** and on-device media processing.

---

## Key Capabilities (Strict Free-Only Mode)

- **Zero Paid Calls & Zero Billing**: All AI script, outline, fact-check, and section-regeneration calls strictly use verified free-tier models (`gemini-3.8-flash` and safe fallback `gemini-3.5-flash-lite`) via Firebase AI Logic with App Check attestation. Paid models (`gemini-4-argon`, `gemini-nano-banana-2.1`, Veo) are permanently disabled and never called.
- **Local Animated Visual-Slide Video Engine**: Combines AndroidX Media3 `Transformer` + deterministic `MediaCodec`/`MediaMuxer` Ken Burns motion slides, user-uploaded scene photos, transitions, subtitles, and optional custom background audio tracks.
- **8 Studio Sections**: Home Dashboard, One-Prompt Video Creator, AI Script Studio (outline, sources/uncertainty notes, word-count duration calibration, Expand Script, section/scene regeneration), Voice Studio (with live installed TTS voice inspection), My Projects (offline local persistence & Resume unfinished projects), Video Library (MP4 player, native FileProvider Share, MP4/WAV/SRT/PNG export), Branding & Settings (`XAVIOUR EXPLAINS` logo placement/size + live preview), and API Usage & Free Quota Status.

---

## Building Locally in Android Studio

```bash
# Build installable debug APK
gradle :app:assembleDebug

# Run unit and Robolectric tests (with local Firestore/Auth emulator)
gradle :app:testDebugUnitTest
```
