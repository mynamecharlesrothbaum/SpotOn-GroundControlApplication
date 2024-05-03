# SpotOn ground control application

The SpotOn ground control application is an in-progress project that is built on top of chobitsfan's [MiniGroundControl](https://github.com/chobitsfan/MiniGroundControl/blob/master/README.md). It is tailored to be a UAV surveying tool which allows users to select locations on an integrated map and direct a quadcopter with a laser-gimbal attachment over those spots. 

This project uses [osmdroid](https://github.com/osmdroid/osmdroid) to integrate [Open Street Map](https://www.openstreetmap.org/about). 

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




