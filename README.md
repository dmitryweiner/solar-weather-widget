# Kp Index Widget for Android

An Android home screen widget that displays the Planetary K-index (Kp) - a measure of geomagnetic activity - as a bar chart with real-time data from NOAA.
Data are taken from this site https://www.swpc.noaa.gov/

<img width="285" height="172" alt="image" src="https://github.com/user-attachments/assets/54fb69a3-30ad-4702-a94c-a42c49944cc7" />

### [Download widget](https://github.com/dmitryweiner/solar-weather-widget/raw/refs/heads/main/app-debug.apk)

## Features

- 📊 **Bar Chart Visualization** - Displays up to 24 Kp index values with DD.MM date labels
- 🎨 **Color-Coded Activity Levels**:
  - 🟢 Green (< 4) - Quiet conditions
  - 🟡 Yellow (4-6) - Moderate activity
  - 🟠 Orange (6-8) - Enhanced activity
  - 🔴 Red (≥ 8) - Geomagnetic storm
- 🔄 **Auto-Update** - Refreshes every 4 hours
- 👆 **Manual Refresh** - Tap the widget to update immediately
- 🌐 **Live Data** - Fetches data from NOAA Space Weather Prediction Center
- 📱 **Flexible Resizing** - Resize from 4x2 to 1x1 cells
- 📐 **Adaptive Layout** - Widget adapts to size changes:
  - Height < 1 cell: Title is hidden
  - Width adjusts number of data points shown (4-24)
- ☀️ **Sun Icon** - Beautiful sun icon for easy identification
- 🚀 **Quick Add** - Launching the app prompts to add widget to home screen
- 🌍 **Multilingual** - Supports Russian, English, Hebrew, and Ukrainian

## What is the Kp Index?

The Kp index is a global geomagnetic activity indicator ranging from 0 (very quiet) to 9 (extreme storm). It's useful for:
- Predicting auroras (Northern/Southern Lights)
- Monitoring space weather effects on satellites and communications
- Tracking solar activity impacts on Earth's magnetosphere

## Installation

### Prerequisites

- Android Studio Arctic Fox or later
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- Kotlin 1.9+

### Setup

1. **Clone the repository**
```bash
git clone https://github.com/yourusername/kp-index-widget.git
cd kp-index-widget
```

2. **Add dependencies to `build.gradle` (app level)**
```gradle
dependencies {
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

3. **Add Internet permission to `AndroidManifest.xml`**
```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

4. **Register the widget in `AndroidManifest.xml`** (inside `<application>` tag)
```xml
<receiver
    android:name=".KpIndexWidget"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
        <action android:name="com.example.kpwidget.ACTION_UPDATE"/>
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/widget_info"/>
</receiver>
```

5. **Create required resource files**:

**`res/layout/widget_layout.xml`**
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="8dp"
    android:background="@drawable/widget_background">

    <TextView
        android:id="@+id/widget_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Planetary K-Index"
        android:textSize="16sp"
        android:textColor="#FFFFFF"
        android:gravity="center"
        android:paddingBottom="4dp"/>

    <ImageView
        android:id="@+id/chart_image"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:scaleType="fitCenter"
        android:contentDescription="Kp Index Chart"/>

    <TextView
        android:id="@+id/last_update"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Updated: --:--"
        android:textSize="12sp"
        android:textColor="#AAAAAA"
        android:gravity="center"
        android:paddingTop="4dp"/>

</LinearLayout>
```

**`res/drawable/widget_background.xml`**
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC000000"/>
    <corners android:radius="16dp"/>
</shape>
```

**`res/xml/widget_info.xml`**
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="180dp"
    android:updatePeriodMillis="14400000"
    android:initialLayout="@layout/widget_layout"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description"/>
```

**`res/values/strings.xml`** (add this string)
```xml
<string name="widget_description">Displays planetary K-index geomagnetic activity</string>
```

6. **Build and run**
```bash
./gradlew assembleDebug
```

7. **Add widget to home screen**
   
   **Option A: Quick Add (Recommended)**
   - Launch the app from your app drawer
   - A dialog will appear to add the widget to your home screen
   - Confirm the placement
   
   **Option B: Manual Add**
   - Long-press on your Android home screen
   - Select "Widgets"
   - Find "Solar Weather" widget
   - Drag it to your home screen

## Project Structure

```
app/src/main/
├── java/com/dmitryweiner/solarweatherwidget/
│   ├── KpIndexWidget.kt          # Main widget class
│   └── MainActivity.kt           # Launcher activity for quick widget add
├── res/
│   ├── layout/
│   │   └── kp_index_widget.xml   # Widget UI layout
│   ├── drawable/
│   │   ├── app_widget_background.xml  # Widget background shape
│   │   ├── ic_widget_preview.xml      # Sun icon for widget picker
│   │   └── ic_launcher_foreground.xml # Sun icon for app launcher
│   ├── xml/
│   │   └── kp_index_widget_info.xml   # Widget metadata
│   └── values/
│       └── strings.xml            # String resources (Russian - default)
│   └── values-en/
│       └── strings.xml            # English strings
│   └── values-iw/
│       └── strings.xml            # Hebrew strings
│   └── values-uk/
│       └── strings.xml            # Ukrainian strings
└── AndroidManifest.xml
```

## How It Works

1. **Data Fetching**: The widget fetches JSON data from NOAA's API endpoint
2. **Parsing**: Extracts the last 12 Kp index values and timestamps
3. **Visualization**: Generates a bitmap with bars colored by activity level
4. **Update Cycle**: Automatically refreshes every 4 hours or on user tap

### API Endpoint

```
https://services.swpc.noaa.gov/json/planetary_k_index_1m.json
```

Returns an array of objects with:
- `time_tag` - Timestamp in UTC
- `Kp` - K-index value (0-9)

## Customization

### Change Update Frequency

Edit `widget_info.xml`:
```xml
android:updatePeriodMillis="14400000"  <!-- 4 hours in milliseconds -->
```

Note: Android enforces a minimum of 30 minutes (1800000ms) for battery optimization.

### Modify Colors

In `KpIndexWidget.kt`, adjust the color thresholds:
```kotlin
barPaint.color = when {
    kpData.kpIndex < 4 -> 0xFF4CAF50.toInt() // Green
    kpData.kpIndex < 6 -> 0xFFFFC107.toInt() // Yellow
    kpData.kpIndex < 8 -> 0xFFFF9800.toInt() // Orange
    else -> 0xFFF44336.toInt()                // Red
}
```

### Change Widget Transparency

Edit `widget_background.xml`:
```xml
<solid android:color="#CC000000"/>  <!-- CC = 80% opacity -->
```

Alpha channel values:
- `FF` = 100% opaque
- `CC` = 80% opaque (default)
- `99` = 60% opaque
- `66` = 40% opaque

## Troubleshooting

**Widget not updating**
- Check internet connection
- Verify the INTERNET permission is in AndroidManifest.xml
- Try manual refresh by tapping the widget

**Chart not displaying**
- Ensure the API endpoint is accessible
- Check logcat for error messages
- Verify coroutines dependency is added

**Widget looks stretched**
- Adjust widget size on home screen
- Modify `minWidth` and `minHeight` in `widget_info.xml`

## Supported Languages

The widget supports the following languages:

| Language | Code | Status |
|----------|------|--------|
| Russian | ru | Default |
| English | en | Full support |
| Hebrew | iw | Full support |
| Ukrainian | uk | Full support |

The language is automatically selected based on your device settings.

## Data Source

Data provided by [NOAA Space Weather Prediction Center](https://www.swpc.noaa.gov/)

## License

MIT License - Feel free to use and modify

## Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss proposed changes.

## Acknowledgments

- NOAA Space Weather Prediction Center for providing free API access
- Space weather community for Kp index standards

---

**Note**: This widget requires an active internet connection to fetch live data. The Kp index is updated approximately every 3 hours by NOAA.

