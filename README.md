<h1 align="center">👾 DeviousJDA 👾</h1>

<p align="center"><i>Devious: Showing a skillful use of underhanded tactics to achieve goals.</i></p>

[JDA](https://github.com/DV8FromTheWorld/JDA) fork with devious experimental and hacky features for big bots™, created for [Loritta](https://github.com/LorittaBot/Loritta).

This fork was created because I wanted to implement [Session Checkpointing](https://github.com/DV8FromTheWorld/JDA/issues/1266) for my bot, because I *hate* restarting my bot to add new features because all gateway sessions needs to reidentify, which is expensive, takes a long time, and makes your users mad due to "your bot is down!!".

While I already have played around with other newer and experimental libs for Discord's API that are more modular in nature which, in theory, would've been easier to migrate Loritta to another lib, in reality *nothing* comes close to JDA's battle tested implementation, because JDA just works™ and it was easier to bend JDA to do what I want it to do, than to migrate to another lib. :3

Keep in mind that this repo is constantly `git reset --hard`'d, we always reset the repo to match JDA's repo, then we cherry-pick the changes on top of it!

## ✨ Features
* Added `PreProcessedRawGatewayEvent`
    * Similar to `RawGatewayEvent`, but it is triggered when any event is received on the gateway, not just dispatch events, and it is triggered before the event has been processed by JDA
    * The event is sent before the response total is updated, so you need to manually update the response total with `setResponseTotal(seq)`!
    * Requires `setRawEventsEnabled(true)`
    * Has `setCancelled(isCancelled)` call, which can be used to cancel the event processing. Events cancelled won't be processed further by JDA.
* Allow shutting down the gateway connection with a specific close code, which can be useful if you don't want to invalidate your current session.
* Added `getResumeUrl()`, `getSessionId()` to `WebSocketClient`
* Added `JDA#shutdown(closeCode)` and `JDA#shutdownNow(closeCode)`, which is useful if you want to shutdown the WebSocket but don't want to invalidate your current session.
* Added `getId()` to `StickerFormat`
* Changed `WebSocketClient#handleEvent` from `protected` to `public`
* Hacky User Installable Apps support
    * This is a prototype only for fun, a lot of checks were removed and user installable apps when used in a guild that doesn't have the bot just acts like it is a private channel. Use `setIntegrationTypes(...)` and `setInteractionContextTypes(...)` when registering a command to enable commands in DM and group DMs.

## 👀 Examples

* [Bot with Session Checkpoint and Gateway Resuming](/src/examples/java/SessionCheckpointAndGatewayResumeExample.kt)
