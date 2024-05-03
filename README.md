# SpotOn ground control application

The SpotOn ground control application is an in-progress project that is built on top of chobitsfan's [MiniGroundControl](https://github.com/chobitsfan/MiniGroundControl/blob/master/README.md). It is tailored to be a UAV surveying tool which allows users to select locations on an integrated map and direct a quadcopter with a laser-gimbal attachment over those spots. 

This project also uses [osmdroid](https://github.com/osmdroid/osmdroid) to integrate [Open Street Map](https://www.openstreetmap.org/about). 

### Features
* Added buttons and functionality for some important controls:
    1. Set UAV to STABALIZE, GUIDED, or LAND mode.
    2. Arm/Disarm UAV throttle.
    3. Takeoff.
    4. Send a global waypoint.
*   Integrated a osmdroid map view
    1. Display current location of UAV on map as an icon.
    2. Extract the 2D global coordinates of a point that the user taps within the map view.
    3. Save the map tiles for a specified area to local memory so that they can be accessed offline.

<img width="547" alt="Screen Shot 2024-05-03 at 2 19 56 PM" src="https://github.com/mynamecharlesrothbaum/SpotOnMiniGroundControl/assets/33434729/3a6aaaf8-9eaf-4f5c-b284-548b3b5a49b1">

### Usage

#### Equipment Needed
- Quadcopter equipped with ArduPilot flight controller, GPS antenna, and telemetry radio.
- Serial USB telemetry radio
- Android device with the SpotOnMiniGroundControl app installed
#### Setup and Connection
1. Connect the serial USB antenna to your Android device. This antenna will facilitate communication between your mobile device and the UAV.
2. Open the SpotOnMiniGroundControl application on your Android device. The app should automatically detect the serial USB antenna and ask for permission to use the device.
3. Turn on the UAV. The app should automatically establish a connection and display the UAV's information within the app.

#### Takeoff and land
1. When the UAV is connected and has completed pre-flight calibration, press "set mode GUIDED".
2. In guided mode press "force arm" to arm the throttle.
3. When the motors begin spinning, press "takeoff" and the UAV will take off to 3 meters.
4. If you press land, the UAV will descend and land from wherever it is currently flying. 

### Structure

* com.chobitsfan.minigcs
    * ArduCopter: Handles specific functionalities related to the ArduCopter, an implementation detail for ArduPilot based drones.
    * MainActivity: The main entry point of the application where the UI is initialized and user interactions are managed.
    * MyAppConfig: Configuration class for managing global application settings.
    * MyMavlinkWork: Manages MAVLink communications, essential for commands and controls exchanged with the UAV.
    * MyUSBSerialListener: Handles USB serial communication for real-time data exchange with the UAV.
