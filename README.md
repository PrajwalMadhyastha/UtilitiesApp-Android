# Utilities App

A modern, modular Android toolkit designed to simplify your daily digital life. Built with **Jetpack Compose**, this app serves as a flat-structure hub for various independent tools.

## Available Tools

### 1. WiFi Usage Tracker
*Refactored from the original Airtel Usage Tracker.*
- **Background Sync**: Uses `WorkManager` to track broadband data cap usage automatically.
- **Smart Dashboard**: Displays remaining data, percentage used, and update timestamps.
- **Onboarding Wizard**: Guided setup for router credentials and sync intervals.
- **Battery Efficient**: Only updates when connected to WiFi and battery is healthy.

### 2. Cricket Toss
*The perfect companion for your weekend games.*
- **3D Animations**: A high-fidelity animated coin flip with realistic 3D perspective.
- **Toss History**: Tracks your last 10 flips to ensure transparency and "fairness."
- **Haptic Feedback**: Physical vibration on landing for a premium feel.
- **Lightning Fast**: Zero-lag interaction designed for immediate results.

### 3. Traffic Challans
*Track Bengaluru traffic police violations, pending fines, and ANPR camera photo proof.*
- **Multi-Vehicle Management**: Save vehicles (cars, scooters, bikes) with customized nicknames and formatted number plates.
- **On-Demand Manual Checks**: Zero background battery drain or quota wastage—check fines whenever you need.
- **Camera Evidence Proof**: View high-resolution AI/ANPR camera snapshots with tap-to-zoom modal for contactless violations.
- **Demo & Live API Modes**: Test realistic Bangalore traffic violations out-of-the-box or plug in your RapidAPI key.
- **Offline Ledger**: Room-backed persistence keeps previous lookups and violation history accessible offline.

### Coming Soon
- **Cricket Stats**: Track personal batting and bowling performance.
- **Passwords**: An offline-first, secure personal vault.
- **Workout Tracker**: Log gym sessions and exercise progress.

## Architecture & Tech Stack

The app uses a **Modular Tool Registry** pattern, allowing for the easy addition of new utilities without bloating the core codebase.

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with a centralized Tool Registry and NavGraph.
- **Persistence**: DataStore Preferences for settings and shared state.
- **Background Tasks**: WorkManager for reliable periodic fetching (WiFi tool).

## Setup & Installation

### Prerequisites
- JDK 17+
- Android Studio Ladybug or newer
- Android 8.0+ device

### Build Instructions
1. Clone the repository.
2. Open in Android Studio.
3. Build and run on your device or emulator.

## License
MIT License. Free to use, modify, and extend!
