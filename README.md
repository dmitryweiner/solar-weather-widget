# Solar Weather Widget for Android

An Android home screen widget that displays solar weather data as a bar chart with real-time data from NOAA Space Weather Prediction Center.

Data are taken from https://www.swpc.noaa.gov/

<img width="285" height="172" alt="image" src="https://github.com/user-attachments/assets/54fb69a3-30ad-4702-a94c-a42c49944cc7" />

### [Download widget](https://github.com/dmitryweiner/solar-weather-widget/raw/refs/heads/main/app-debug.apk)

## Features

- 📊 **Bar Chart Visualization** - Displays up to 24 data points with DD.MM date labels
- 🔄 **Multiple Data Sources**:
  - **K-index (Kp)** - Geomagnetic activity indicator (0-9)
  - **Proton Flux** - Solar energetic particle intensity
  - **X-ray Flux** - Solar X-ray emission levels
- 🎨 **Color-Coded Activity Levels** - Different color schemes for each data type
- ⚙️ **Configurable Settings**:
  - Show/hide widget title
  - Show/hide info row
  - Select data source
- 🔄 **Auto-Update** - Refreshes every 4 hours
- 👆 **Manual Refresh** - Tap the widget to update immediately
- 🌐 **Live Data** - Fetches data from NOAA Space Weather Prediction Center
- 📱 **Flexible Resizing** - Resize from 4x2 to 1x1 cells
- 📐 **Adaptive Layout** - Widget adapts to size changes:
  - Height ≤ 1 cell: Shows only chart
  - Height ≤ 2 cells: Hides title, shows chart and info row
  - Height > 2 cells: Shows all elements (based on settings)
- 💾 **Data Persistence** - Keeps showing last data on errors or resize
- ☀️ **Sun Icon** - Beautiful sun icon for easy identification
- 🚀 **Quick Add** - Launching the app prompts to add widget or opens settings
- 🌍 **Multilingual** - Supports Russian, English, Hebrew, and Ukrainian

## Data Sources

### K-index (Kp)
The Kp index is a global geomagnetic activity indicator ranging from 0 (very quiet) to 9 (extreme storm). Color coding:
- 🟢 Green (< 4) - Quiet conditions
- 🟡 Yellow (4-6) - Moderate activity
- 🟠 Orange (6-8) - Enhanced activity
- 🔴 Red (≥ 8) - Geomagnetic storm

Useful for predicting auroras and monitoring space weather effects.

### Proton Flux
Measures solar energetic particle (proton) intensity. Higher values indicate increased radiation from solar events. Color coding uses blue gradient (light blue to deep purple).

### X-ray Flux
Measures solar X-ray emission in the 0.1-0.8 nm wavelength. Used for solar flare classification (A, B, C, M, X classes). Color coding uses purple gradient.

## Widget Settings

Access settings by:
1. **When adding widget** - Configuration screen appears automatically
2. **Tapping app icon** - Opens settings for existing widget
3. **Long-press widget** (Android 12+) - Select "Reconfigure"

Settings options:
- **Show title** - Display/hide "Planetary K-Index" title
- **Show info row** - Display/hide update time/status
- **Data source** - Choose between Kp, Proton Flux, or X-ray Flux

## Installation

### Prerequisites

- Android Studio Arctic Fox or later
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
- Kotlin 1.9+

### Setup

1. **Clone the repository**
```bash
git clone https://github.com/dmitryweiner/solar-weather-widget.git
cd solar-weather-widget
```

2. **Build and run**
```bash
./gradlew assembleDebug
```

3. **Add widget to home screen**
   
   **Option A: Quick Add (Recommended)**
   - Launch the app from your app drawer
   - A dialog will appear to add the widget to your home screen
   - Configure settings and confirm
   
   **Option B: Manual Add**
   - Long-press on your Android home screen
   - Select "Widgets"
   - Find "Solar Weather" widget
   - Drag it to your home screen

## Project Structure

```
app/src/main/
├── java/com/dmitryweiner/solarweatherwidget/
│   ├── KpIndexWidget.kt           # Main widget class
│   ├── MainActivity.kt            # Launcher activity
│   ├── WidgetConfigureActivity.kt # Settings activity
│   ├── data/
│   │   ├── DataError.kt           # Error types for localization
│   │   ├── KpData.kt              # Kp data model (legacy)
│   │   ├── SolarData.kt           # Unified data model
│   │   ├── SolarDataRepository.kt # Data fetching for all sources
│   │   ├── KpDataRepository.kt    # Kp-specific repository (legacy)
│   │   └── WidgetSettings.kt      # Settings storage
│   └── ui/
│       └── ChartRenderer.kt       # Chart bitmap generation
├── res/
│   ├── layout/
│   │   ├── kp_index_widget.xml        # Widget UI layout
│   │   └── activity_widget_configure.xml # Settings layout
│   ├── drawable/
│   │   ├── app_widget_background.xml  # Widget background
│   │   ├── ic_widget_preview.xml      # Widget picker icon
│   │   └── ic_launcher_foreground.xml # App launcher icon
│   ├── xml/
│   │   └── kp_index_widget_info.xml   # Widget metadata
│   └── values/
│       └── strings.xml                # String resources (Russian)
│   └── values-en/
│       └── strings.xml                # English strings
│   └── values-iw/
│       └── strings.xml                # Hebrew strings
│   └── values-uk/
│       └── strings.xml                # Ukrainian strings
└── AndroidManifest.xml
```

## How It Works

1. **Configuration**: User selects data source and display options
2. **Data Fetching**: Widget fetches JSON data from NOAA's API endpoints
3. **Caching**: Successfully fetched data is cached for resilience
4. **Visualization**: Generates a bitmap with bars colored by activity level
5. **Error Handling**: On errors, shows cached data with localized error message
6. **Update Cycle**: Automatically refreshes every 4 hours or on user tap

### API Endpoints

| Data Source | URL |
|-------------|-----|
| K-index | `https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json` |
| Proton Flux | `https://services.swpc.noaa.gov/json/goes/primary/integral-protons-plot-3-day.json` |
| X-ray Flux | `https://services.swpc.noaa.gov/json/goes/primary/xrays-3-day.json` |

## Customization

### Change Update Frequency

Edit `kp_index_widget_info.xml`:
```xml
android:updatePeriodMillis="14400000"  <!-- 4 hours in milliseconds -->
```

Note: Android enforces a minimum of 30 minutes (1800000ms) for battery optimization.

### Modify Colors

In `ChartRenderer.kt`, adjust the color thresholds for each data source:
```kotlin
private fun getKpColor(kpIndex: Double): Int = when {
    kpIndex < 4 -> 0xFF4CAF50.toInt() // Green
    kpIndex < 6 -> 0xFFFFC107.toInt() // Yellow
    kpIndex < 8 -> 0xFFFF9800.toInt() // Orange
    else -> 0xFFF44336.toInt()         // Red
}
```

### Change Widget Transparency

Edit `app_widget_background.xml`:
```xml
<solid android:color="#CC000000"/>  <!-- CC = 80% opacity -->
```

## Troubleshooting

**Widget not updating**
- Check internet connection
- Verify the INTERNET permission is in AndroidManifest.xml
- Try manual refresh by tapping the widget

**Chart not displaying**
- Ensure the API endpoint is accessible
- Check logcat for error messages
- Widget shows cached data on errors - wait for next update

**Widget looks stretched**
- Adjust widget size on home screen
- Widget adapts automatically to different sizes

**Settings not appearing**
- Make sure WidgetConfigureActivity is registered in AndroidManifest.xml
- Try removing and re-adding the widget

## Supported Languages

| Language | Code | Status |
|----------|------|--------|
| Russian | ru | Default |
| English | en | Full support |
| Hebrew | iw | Full support |
| Ukrainian | uk | Full support |

The language is automatically selected based on your device settings. All error messages and UI elements are localized.

## Data Source

Data provided by [NOAA Space Weather Prediction Center](https://www.swpc.noaa.gov/)

## License

MIT License - Feel free to use and modify

## Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss proposed changes.

## Acknowledgments

- NOAA Space Weather Prediction Center for providing free API access
- Space weather community for Kp index and solar monitoring standards

---

**Note**: This widget requires an active internet connection to fetch live data. Data is cached locally for resilience during network issues.
