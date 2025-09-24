# PBP

PBP is an experimental Android application designed to interface with a Bluetooth controller device.  
This project is open-source and developed for educational purposes.

## Features
- Bluetooth connectivity for custom controller input.
- Basic input mapping and control support.
- Developed using Android Studio with AI-assisted code generation.

## Disclaimer
This project is provided "as is" without any warranties.  
I do not take any responsibility for damages, malfunctions, or data loss caused by the use of this software.  
This project is **not affiliated, endorsed, or authorized by Nintendo**.  
"PBP" is an independent project and is not intended for commercial use.  
Any references to trademarks, such as "Pokéball Plus", are used solely for informational purposes and belong to their respective owners.  
Users are responsible for ensuring that their use of this software complies with applicable laws.

## Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/zesu-suze/PBP.git
2. Open the project in Android Studio.

3. Build and run the app on your device.

## APK Release
An experimental APK build is available for download under the "Releases" section.  
Use at your own risk.  
This project is not optimized for all devices.

## How to Configure PBP App

When you open the application for the first time, it will request **Nearby Devices permissions** (required for Bluetooth Low Energy).

### Step-by-step configuration:

1. **Permissions**  
   Grant all requested permissions so the app can function correctly.

2. **Accessibility & Location Services**  
   - There is a button in the app to check Accessibility and Location.  
   - Both services must be **enabled manually** for the app to work.  
     - **Location** is required for finding the Poké Ball Plus device.  
     - **Accessibility** allows the app to simulate screen taps for controls.

3. **Enable Accessibility**  
   ⚠ Important:  
   To activate accessibility for the app, follow these steps:  
   - Long press the app icon → Select **Settings of this app** (Storage, Battery, Clear data...).  
   - Tap the **three dots** in the top right corner → Select **Allow restrictive settings**.  
   - Once done, go back and enable Accessibility.

4. **Using the App**  
   - The app displays various information about the Poké Ball Plus and button states.  
   - To configure which part of the screen each button should tap:  
     - Go to **Configure Tap**.  
     - Tap the area of the screen you want the app to simulate.  
     - Note: This only works in landscape mode.  
     - Gyroscope controls are not implemented yet due to technical issues.

---

⚠ **Disclaimer**:  
This application is experimental and may not work perfectly on all devices.  
Use at your own risk.  
The developer is not responsible for any malfunction or damages caused by using this app.
