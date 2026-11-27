# Hand gestures (0.5.9)

The Lenovo CD-3L501F has a dedicated gesture input device. FrameRevive can read its existing directional events without opening the camera, recording video, or running image recognition. This also works with Lenovo's launcher disabled on the tested frame.

- Wave left to advance; wave right to return to a previously displayed photo.
- The photo slides the way your hand moved, so you can see the wave was read correctly; other changes cross-fade.
- Pause about a second between waves. An 800 ms cooldown rejects accidental duplicate events.
- Direction follows the display rotation, using the same mapping as Lenovo's original app, so a wave to your left advances however the frame hangs. Upside down (rotated 180 degrees) was checked on the frame; the other rotations are unit-tested only.
- A hand moving towards the front buttons looks like a wave to the sensor. While the buttons are asleep (the lights off), the first such movement only wakes them and lights them; waves then change the photo for as long as they stay awake, 8 seconds after the last touch, key press or movement. So after a quiet spell the first wave only lights the buttons, and reaching for a button does not flip the picture.
- Vertical and forward/backward gestures have no slideshow action.
- Gestures pause during scheduled sleep and while the frame's on-screen controls are visible.
- Turn off **Hand gestures** in the webpage's Display settings or the frame's Settings if you prefer button controls.

The Previous button uses up to 60 photos actually displayed during the current app session. After going back, Next/automatic playback moves forward through that history before continuing the playlist. No previous image is available immediately after startup. Same-album refreshes retain history while replacing temporary image URLs and removing deleted photos. Switching albums or restarting clears history. Image bitmaps are not retained in history.

On the controls page, **Last hand gesture** shows the last recognized horizontal gesture and its time. If no gesture is recognized, check that gestures are enabled, the frame is awake and displaying photos, and the on-frame settings/controls are closed. Wave across the front sensor and pause; merely holding a hand nearby is not a swipe.

Validation: live USB input capture confirmed left/right events while Lenovo's app was disabled. Build, 38 unit tests and lint passed. The owner confirmed that left advances the photo and right returns to the previous displayed photo on the installed 0.5.9 build. The original playback interval and sleep/display settings were restored after testing. Other physical orientations have unit-test coverage but have not all been tested on hardware.
