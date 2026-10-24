# Jolt app icon — 2a "Sharpened"

Adaptive icon on a 108 dp canvas; the mark sits inside the 66 dp safe zone.

- Mark #FFB020 on #0E0F12 (light variant: #0E0F12 on #F6F5F2)
- `android/res` — drop into `app/src/main/res`. Includes foreground, solid background, monochrome (Android 13+ themed icons) and the adaptive-icon XML.
- For the light variant, set `jolt_mark` to #0E0F12 and `jolt_background` to #F6F5F2.
- `png/` — legacy mipmaps (48–192 px) and a 512 px Play Store icon. Upload the Play icon as a full square; Play applies its own mask.
- `svg/` — source artwork.
