Tebex supports seamless integration with the official Hytale server via a dedicated plugin. These integrations allow automated command execution, player-specific package fulfillment, and robust monetization for server operators.

#Prerequisites

- You must have a Hytale server running
- Your Tebex store must be configured for Hytale
- Access to your server's console and FTP / File system
- You must authenticate your server with a secret key. Find your Tebex secret key from: Control Panel > Integrations > Game Servers > Edit.

# Installation Instructions
- Download the plugin from Tebex > Plugins > Hytale.
- Place the .jar file into /mods in your server directory.
- Start your server. If it is already running, you can run:
```
plugin load Tebex:Tebex-Hytale
```
In console, run:
```
tebex secret YOUR_SECRET_KEY
```

In-game, run:
```
tebex info
```

Confirm your store's details are displayed.

You're all set! Your Hytale server is now fully integrated with Tebex. Ready to start earning? Create packages and publish your store today!

# Commands and Permissions

To view a list of commands available, from the console, run:
```
tebex
```
Below is a breakdown of the available commands and their permissions:

# Configuration
There are more in-depth configuration options available in the plugin's configuration file at `mods/Tebex_Tebex-Hytale/config.json`
```json
{
    "SecretKey": "qwertyuiopasdfghjklzxcvbnm9876543210",
    "BuyCommandName": "buy",
    "BuyCommandEnabled": true,
    "BuyCommandMessage": "Buy packages at our store: {url}",
    "DebugMode": false
}
```

Below is a breakdown of each configuration parameter and its function:

After making changes to your configuration file, you can apply them with:
```
plugin reload Tebex:Tebex-Hytale
```

# Support
If you are having any issues with the Tebex Hytale plugin, please make sure to toggle debug mode on using the command tebex debug true . This shows more in-depth log messages that will help diagnose the problem.
When reaching to our support team, please include these debug logs with your ticket.

# Feedback
The plugin is in active development. Please do not hesitate to contact us with any questions or feedback which you may have.
We have a dedicated Feedback Form if you have suggestions for new features with the plugin.