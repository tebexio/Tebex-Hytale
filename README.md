# Tebex Plugin for Hytale Servers

This is the official Tebex plugin for Hytale, which enables automatic command execution, player-specific package fulfillment, and robust monetization options for Hytale server operators, complete with a in-game store browser, cart system, and QR code checkout.
<img width="1411" height="832" alt="image" src="https://github.com/user-attachments/assets/e2f9b4f8-eb32-403a-ac69-c66802d4b24f" />

# What is Tebex?
Tebex is a game monetization engine featuring over 120 payment methods, chargeback protection, fraud protection, and 3-day payouts. Tebex allows you to sell items, subscriptions, and more from an in-game customized shop.

Players browse your store, select and purchase their items, and Tebex automatically delivers the items when the player is next online. View more at https://tebex.io

See an interactive Tebex store using one of our free templates at https://example.tebex.io/

# Prerequisites
- You must have a Hytale server running
- Your Tebex store must be configured for Hytale
- Access to your server's console and FTP / File system
- You must authenticate your server with a secret key. Find your Tebex secret key from: Control Panel > Integrations > Game Servers > Edit.

# Installation Instructions
- Download the plugin from the Releases tab.
- Place the .jar file into `/mods` in your server directory.
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

You're all set! Your Hytale server is now fully integrated with Tebex. When players purchase a package, it will run your configured command to fulfill their purchase. [Start a Tebex store today](https://www.tebex.io/game-servers).

# Store Browser UI

The plugin implements a store browser UI now opens with `/buy`, showing the categories and packages available for sale.

Users can check out a basket by scanning a QR code generated on the fly, or visit a link generated in chat to complete payment and receive their items.

**Restart required after first startup:** An asset pack is built at startup containing the package thumbnails downloaded from your Tebex store. This will be saved to `mods/Tebex_Tebex-Hytale-Thumbnails`. You must restart the server for the new asset pack to be recognized, but this must only be done once.

After the first startup, you can rebuild the store's assets at anytime with `/tebex rebuild`. Assets must be rebuilt any time you make changes to your store's thumbnails.

If you do not want to use the Store Browser, set `StoreBrowserEnabled` to false in the configuration, and a link to your webstore will be shown instead when `/buy` is used.

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
    "StoreBrowserEnabled": true,
    "CartEnabled": true,
    "BuyCommandMessage": "Buy packages at our store: {url}",
    "DebugMode": false
}
```

The Headless API public token is now sourced automatically from Plugin API `/information` as `account.public_token`.

Below is a breakdown of each configuration parameter and its function:

After making changes to your configuration file, you can apply them with:
```
plugin reload Tebex:Tebex-Hytale
```

# Support
If you are having any issues with the Tebex Hytale plugin, please make sure to toggle debug mode on using the command:
```
tebex debug true
```

This shows more in-depth log messages that will help diagnose the problem.
When reaching to our support team, please include these debug logs with your ticket.

# Feedback
The plugin is in active development. Please do not hesitate to contact us with any questions or feedback which you may have.
