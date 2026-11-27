# Front buttons and playback pause

On the tested Lenovo CD-3L501F, the dedicated as9061d_key device reports Plus (24), Minus (25), and Star (295).

- Plus: next photo on initial press.
- Minus: previous displayed photo on initial press.
- Short Star press: pause or resume. A persistent Paused badge appears at the bottom right. The **Star button** setting (Everyday page, next to the hand gestures) can change this to **Favorite this photo**: a short press then stars the photo on screen, and a second press removes the star. A collage or pair cannot be starred, so on one the press pauses or resumes instead. Pausing otherwise stays in the controls overlay and on the Everyday page. See [favorites](favorites-plan.md).
- Hold Star for at least 2.5 seconds: open on-frame controls. Releasing after a hold does not toggle pause.

The overlay includes previous/pause-resume/next, album refresh, settings, Close, and a QR code plus the private web-controls address. It closes after 30 seconds without input, or on a short Star press (every key press restarts the 30 seconds, so Plus and Minus keep it open). The QR is generated entirely on the frame and contains its private management key; do not publish photos of it. HTTPS certificate pairing still applies.

Pause is also available on the Everyday webpage. It survives app restarts and does not disable sleep/wake scheduling or album checks. New additions wait while paused. Manual navigation changes the photo while staying paused. Unsupported input devices keep their usual Android behavior.

While an uncached image is fetched, Loading photo… appears and the current image stays visible. Rapid presses retain only the latest pending request; the active request may still need to finish. A slow network can still delay an uncached photo.
