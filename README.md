# 💊 Caffeinate

&nbsp;&nbsp;[![GPLv3 License](https://img.shields.io/badge/License-GPL%20v3-yellow.svg)](https://img.shields.io/badge/License-GPL%20v3-yellow.svg)
&nbsp;&nbsp;[![Build App](https://github.com/abdalmoniem/Caffeinate/actions/workflows/build_app.yml/badge.svg)](https://github.com/abdalmoniem/Caffeinate/actions/workflows/build_app.yml)
&nbsp;&nbsp;[![Publish Release](https://github.com/abdalmoniem/Caffeinate/actions/workflows/publish_release.yml/badge.svg)](https://github.com/abdalmoniem/Caffeinate/actions/workflows/publish_release_on_tag.yml)
&nbsp;&nbsp;[![CodeFactor](https://www.codefactor.io/repository/github/abdalmoniem/caffeinate/badge)](https://www.codefactor.io/repository/github/abdalmoniem/caffeinate)
&nbsp;&nbsp;[![Crowdin](https://badges.crowdin.net/caffeinate/localized.svg)](https://crowdin.com/project/caffeinate)

Keeps the phone's screen awake for a configurable amount of time.

# ❓ About

Caffeinate is an App that helps android developers to keep their phone's display awake without having
to change the device settings. It can be toggled at various places:

* A `tile` in the quick settings, the place that holds the toggles for e.g. Wi-Fi and Bluetooth. Requires
  Android 7 or higher.
* A `button` in the app itself

# 💪 Features

* Uses android's `WakeLock` Class with either `SCREEN_DIM_WAKE_LOCK` or `SCREEN_BRIGHT_WAKE_LOCK` lock
  level based on user settings
* Timeout period is configurable
* Switch timeouts from the `quick settings tile` or the `button` in the app. clicking them, selects the
  next timeout and after `1 second` the timeout will start, if the `quick settings tile` or the `button`
  are clicked after starting, the timeout will stop.
* Available timeouts are `[30 seconds, 05 minutes, 10 minutes, 15 minutes, 30 minutes, 60 minutes, ∞]`
* An option to enable the screen to dim while the `WakeLock` is acquired
* An option to enable holding the `WakeLock` if the screen is locked, so that the screen will keep on
  after unlocking, default behaviour is that the `WakeLock` is released when the screen is locked
* Multiple theming options `[light, dark, system default and material you]`

# 🔽 Download

For now, you can download the most recent version of Caffeinate
from [GitHub Releases](https://github.com/abdalmoniem/Caffeinate/releases/latest).

[<img alt="Download from GitHub" height="80" src="assets/direct-apk-download.png"/>](https://github.com/abdalmoniem/Caffeinate/releases/latest)

# 🖼️ Screenshots

<table>
    <tr>
        <td>
            <img src="assets/Screenshot_2024-06-01-20-47-55-37.jpg" alt="Caffeinate Screenshot 01" width="180"/>
        </td>
        <td>
            <img src="assets/Screenshot_2024-06-01-20-48-00-67.jpg" alt="Caffeinate Screenshot 02" width="180"/>
        </td>
        <td>
            <img src="assets/Screenshot_2024-06-01-20-48-08-85.jpg" alt="Caffeinate Screenshot 03" width="180"/>
        </td>
        <td>
            <img src="assets/Screenshot_2024-06-01-20-48-16-52.jpg" alt="Caffeinate Screenshot 04" width="180"/>
        </td>
    </tr>
    <tr> 
        <td>
            <img src="assets/Screenshot_2024-06-01-20-48-34-36.jpg" alt="Caffeinate Screenshot 05" width="180"/>
        </td>
        <td>
            <img src="assets/Screenshot_2024-06-01-20-48-50-46.jpg" alt="Caffeinate Screenshot 06" width="180"/>
        </td>
        <td>
            <img src="assets/Screenshot_2024-05-30-19-44-41-86.jpg" alt="Caffeinate Screenshot 07" width="180"/>
        </td>
        <td>
            <img src="assets/Screenshot_2024-05-31-12-15-47-02.jpg" alt="Caffeinate Screenshot 08" width="180"/>
        </td>
    </tr>
</table>

## 🈵 Translations

App strings and the app store description can be translated via
Crowdin: https://crowdin.com/project/caffeinate

Translations have to be approved before being merged into the app. To become a translator with approval
rights or to request a new language, please [poke me on Crowdin](https://crowdin.com/profile/abdalmoniem)
or open an issue here on GitHub.
