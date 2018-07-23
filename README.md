# Lembot
A bot for Discord guilds/servers based on the open source libraries [Twitch4J](https://github.com/twitch4j/twitch4j), [Discord4J](https://github.com/Discord4J/Discord4J) and [SQLite JDBC](https://github.com/xerial/sqlite-jdbc), using [SLF4J](https://github.com/qos-ch/slf4j) and [logback](https://github.com/qos-ch/logback) as logging framework, which can announce twitch streams streaming certain games if needed.

## Setup:

- Use my set up bot and let it join your server: https://discordapp.com/api/oauth2/authorize?client_id=468507312622141440&permissions=486464&scope=bot (problems with Discord's implementation of reCAPTCHA that may prevent the bot from joining your server/guild on first try are possible, you have to retry sometimes).
- Be sure to leave the permissions as they are to assure no missing permissions errors. Keep in mind that those still might occur for some reason, so guarantee that the bot can read, send and manage messages, read the message history, embed links, add reactions and external emojis in the channels you want it to announce or respond to commands. Do so if you don't see a welcome message in your default channel after inviting the bot.
- Set it up using the commands below.

If you want to wipe all the data of the bot (Twitch channels and game filters), just kick the bot and let it join again. 

Beware, this bot is currently in beta and errors may occur. Feel free to submit them here or contact me over Discord Lembox#3170.

## Commands:

all commands (except the addition or removal of maintainers) can be used by maintainers

| command        | behaviour    |     
| ------------- |:-------------|
|!init|initializes the bot, recommended after joining a new server/guild |
|!set_announce #channel|sets the announcement channel, default channel #general|
|!set_message 0/classic/1/embedded|sets the message style to classic/0 or embedded/1, which changes the appearance of the announcement messages (default: embedded)|
|!cleanup|if activated announcement messages of offline streams are deleted and if deactivated announcement messages are edited to show the streams are offline (default: deactivated)|
|!config|displays all the configuration options in your server/guild|
|!maintainer_add @user|adds a new maintainer (owner-only command) |
|!maintainer_remove @user|removes a maintainer (owner-only command)|
|!maintainers|lists maintainers |
|!game_add gameName* |adds  new game filters (same name as on Twitch) to only select streams streaming one of those games|
|!game_remove gameName*|removes game filters|
|!game_filters|lists game filters|
|!twitch_add channelName/channelID*|adds new Twitch channels to be announced|
|!twitch_remove channelName/channelID*|removes  Twitch channels|
|!twitch_channels|lists all the Twitch channels|
|!shutdown|shuts down the stream announcer in your server/guild (only recommend when problems occur)|
|!restart|restarts the stream announcer in your server/guild (only recommend when problems occur)|
|!raw_data|outputs the game filters and twitch channels as raw string to easily copy it for backup purposes or to reuse it for another bot|

Commands noted with an * can also take multiple arguments split by a "|", e.g. "!twitch_add channel1|channel2" adds channel1 and channel2.

## Planned features:

- enable to only follow games and thus announcing all streams that stream the followed games (as new mode?)
- adding Twitch channels that have been linked by Discord users in the respective guilds/servers (unfortunately the Discord API doesn't offer an access to user profiles from a bot account)
- improve performance of the raw_data command by making it access the DB directly instead of memory
- improve performance of the stream announcer which takes roughly 0.7s per channel to check its status (possible limitation of Twitch4J?)
