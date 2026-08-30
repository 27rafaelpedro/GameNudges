# GameNudges

GameNudges is a research project developed during the "Summer of Research" program at LASIGE. The aim of this project is to encourage players to adopt healthier lifestyle habits while continuing to enjoy what they love. 

In order to achieve this goal, behavioral interventions known as "Nudges" were implemented in a non-intrusive way, ensuring there is no disruption to the core gameplay experience. By seamlessly integrating real-world physical activity tracking as daily step counts, GameNudges gently motivates players toward healthy habits, reinforcing positive habits through subtle in-game feedback and rewards.

## Requirements & Setup Guide

In order to track physical activity data and sync it with your Minecraft gameplay, follow these steps in order:

1. **Set Up Health Tracking Apps**
   * Install **Google Fit** and **Health Connect** on your smartphone.
   * **Important:** You must ensure that **Health Connect is synchronized with Google Fit** so the data can be properly accessed.
   * Log into both apps using your primary **Google Account**.
   * Grant all requested health and activity permissions for both applications.

2. **Install StepTrack**
   * Go to the **[Releases](../../releases)** tab in this repository.
   * Download the latest steptrack.apk file onto your Android device.
   * Install the application.

3. **Authenticate & Link Your Profile**
   * Open the **StepTrack** app on your phone.
   * Sign in with the **same Google Account** used in Step 1.
   * Grant all required permissions when prompted.
   * Enter your exact **Minecraft Username** to create your entry in the database.

---

## Minecraft Mod Installation

To play the mod, you must install the Fabric Loader and add the necessary .jar files to your Minecraft mods folder:

1. **Install Fabric Loader:**
   * Download and run the [Fabric Installer](https://fabricmc.net/use/installer/).
   * Select the Client tab, choose Minecraft version **26.2** and Loader version **0.19.3**, and click **Install**.

2. **Download the Mods:**
   * Go to the **[Releases](../../releases)** tab in this repository.
   * Download the 
nudgecraft-1.0.0.jar and fabric-api.jar files.

3. **Add to Mods Folder:** 
   * On Windows, press Win + R, type %appdata%\.minecraft\mods and press Enter. 
   * (On Mac, go to ~/Library/Application Support/minecraft/mods).
   * Place both .jar files inside this folder.

4. **Launch the Game:** 
   * Open the official Minecraft Launcher.
   * Select the newly created **Fabric** profile in Minecraft Java in the versions tab.
   * Click **Play** and enjoy the mod!
