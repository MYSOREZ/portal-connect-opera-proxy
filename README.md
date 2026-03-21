# PORTAL Connect: Open Source Opera Proxy Client

**PORTAL Connect** is an advanced Android client providing a secure tunnel via the Opera Proxy infrastructure. This repository provides **full open-source access** to the application's codebase, enabling the community to build, study, and enhance this powerful connectivity tool.

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" height="128" />
</p>

## 🔓 Open Access for the Community

The source code and resources in this repository were **sourced from public archives and community-shared materials found on the internet**. We have consolidated and prepared this codebase to ensure that this technology remains transparent and accessible to everyone. 

By providing these sources, we empower users to:
*   **Audit the code** for security and privacy.
*   **Customize the experience** with unique themes and routing rules.
*   **Contribute improvements** to bypass evolving network restrictions.

## 🚀 Key Features Available in this Source

*   **DPI Bypass Engine:** Integrated techniques (like "White List" mode) designed to overcome strict network filtering using Fake SNI and specialized bootstrap DNS.
*   **Intelligent Routing:** 
    *   **Whitelist Mode:** Proxy only specific apps.
    *   **Blacklist Mode:** Proxy everything *except* selected apps.
    *   **Proxy-Only Mode:** Run as a local HTTP/SOCKS5 server without a system-wide VPN.
*   **Multi-Protocol Support:** Seamless HTTP and SOCKS5 switching for compatibility with Telegram, Tor, and more.
*   **Modern UI/UX:** A customizable interface with 6 aesthetic themes (AMOLED, Hacker, Glass, etc.) and a real-time log console.
*   **Integrated Widgets:** Three widget sizes for instant control and traffic monitoring.

## 🛠 Building the Project

The codebase is ready to be compiled using standard Android development tools:
1. Clone the repository.
2. Open in **Android Studio**.
3. Ensure **NDK (Side by side)** is installed.
4. Run `./gradlew assembleRelease` to generate the production APKs.

## 📜 License & Acknowledgments

This project is shared with the community under the following terms:

- **Primary License:** [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). Free to use and modify for non-commercial purposes, provided the same freedom is preserved.
- **Core Technology:** This client utilizes the `tun2proxy` core originally authored by [Snawoot](https://github.com/snawoot) (MIT License).

---
*Open source. Community driven. Non-commercial forever.*
