# Lembot
A bot for Discord guilds/servers that can announce twitch streams streaming certain games if needed.

## Setup:

- Use my set up bot and let him join your server: https://discordapp.com/api/oauth2/authorize?client_id=468507312622141440&permissions=257024&redirect_uri=http%3A%2F%2Flocalhost%2F&scope=bot
- Set it up using the commands below.

If you want to wipe all the data of the bot (Twitch channels and game filters), just kick the bot and let it join again. 

Beware, this bot is still in alpha and has to be optimized for several cases (connection loss, Discord outage, etc.). 

## Commands:

all commands are maintainer-only

| command        | behaviour    |     
| ------------- |:-------------|
| !init     | initializes the bot, recommended after joining a new server/guild |
| !set_announce channelID      | sets the announcement channel, default channel (#general) by default     |
| !maintainer_add userID   | adds a new maintainer (owner-only command)      |
|!maintainer_remove userID  | removes a maintainer (owner-only command) |
|!maintainers          |                  lists maintainers 
|!game_add gameName*    |                adds  new game filters (same name as on Twitch) to only select streams streaming one of those games
| !game_remove gameName*            |      removes game filters
|!twitch_add channelName/channelID*    |  adds new Twitch channels to be announced (during the next check cycle)
|!twitch_remove channelName/channelID*    |                      removes  Twitch channels
|!twitch_channels           |             lists all the Twitch channels

Commands noted with an * can also take multiple arguments split by a "|", e.g. "!twitch_add channel1|channel2" adds channel1 and channel2.
