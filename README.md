# Nortix Client

A custom Minecraft client mod built on Fabric that enhances the vanilla Minecraft experience with cosmetics, authentication, and social features.

## 🎮 Features

### Cosmetics System
- **Custom Capes**: Dynamic cape rendering with texture caching and download management
- **Nametag Icons**: Custom icons displayed alongside player nametags
- **Hat Models**: GeckoLib-powered 3D hat models for player customization

### Authentication & Session Management
- Secure authentication system with device-based identification
- Session persistence and automatic token refresh
- Heartbeat system for maintaining active sessions
- Multi-account support with account overlay UI

### Social Features
- **Discord Rich Presence**: Display your current game status on Discord
- **Server Tracking**: Automatic detection and display of current server
- **Idle Detection**: Smart idle state tracking

### User Interface
- Custom title screen modifications
- Account management overlay
- Discovery screen for exploring features
- Settings screen for configuration management

## 🛠️ Technical Stack

- **Minecraft Version**: 1.21.11
- **Fabric Loader**: 0.18.4
- **Fabric API**: 0.141.1+1.21.11
- **GeckoLib**: 5.4.2 (for 3D model rendering)
- **Mod Menu**: 17.0.0-beta.2 (for in-game configuration)

## 📦 Building

This project uses Gradle with Fabric Loom for building. To compile the mod:

```bash
./gradlew build
```

The compiled JAR will be located in `build/libs/` as `nortix-client-<version>.jar`.

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Download the latest release of Nortix Client
3. Place the JAR file in your `.minecraft/mods` folder
4. Launch Minecraft with the Fabric profile

## 📁 Project Structure

```
nortixclient/
├── src/
│   ├── main/
│   │   ├── java/me/orbitium/nortix/          # Core mod initialization
│   │   └── resources/                         # Mod metadata and configs
│   └── client/
│       ├── java/me/orbitium/nortix/client/   # Client-side code
│       │   ├── api/                           # API clients
│       │   ├── gui/                           # UI screens and overlays
│       │   ├── model/                         # 3D models
│       │   ├── session/                       # Session management
│       │   └── util/                          # Utility classes
│       └── resources/                         # Client resources
├── build.gradle                               # Build configuration
├── gradle.properties                          # Project properties
└── LICENSE                                    # License file
```

## 🔒 License

**Copyright (c) 2026 NortixClient**

This software is licensed under a **Proprietary License**. All rights reserved.

### Permissions
You are granted a limited license to:
- Compile the source code for personal testing and verification
- Run the compiled binary on your local machine for personal use

### Restrictions
You are **NOT** permitted to:
- Modify, alter, or create derivative works
- Redistribute or share the source code or binaries
- Copy, clone, or mirror the repository
- Use the software for commercial purposes
- Reverse engineer or decompile the software

### Revocation
The copyright holders reserve the right to revoke this license at any time without prior notice or warning.

For the complete license terms, see the [LICENSE](LICENSE) file.

## ⚠️ Disclaimer

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## 🔧 Development

### Prerequisites
- Java 21 or higher
- Gradle (wrapper included)
- Minecraft 1.21.11

### Package Structure
The project uses the base package `me.orbitium.nortix` with the following organization:
- Core mod classes in `me.orbitium.nortix`
- Client-side code in `me.orbitium.nortix.client`
- Mixins in `me.orbitium.nortix.mixin.client`

### Configuration Files
- `fabric.mod.json`: Mod metadata and entrypoints
- `nortix-cosmetics.mixins.json`: Mixin configurations
- `gradle.properties`: Build properties and versions

## 📞 Contact

For questions or support regarding authorized use of this software, please contact the copyright holders.

---

**Note**: This is proprietary software. Unauthorized use, modification, or distribution is strictly prohibited.
