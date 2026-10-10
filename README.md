# Peripheral

A Minecraft 1.20.1 Fabric mod about the feeling that you are not alone. Nothing attacks you. Something is just always almost there.

A hidden unease meter rises while you are alone, in the dark or underground, and falls when you sleep, stay near other players or villages, or spend time in daylight. When it is your turn, the mod picks something that a real player could have done nearby, and lets you wonder.

Nothing happens during the first two in-game days of a world.

## Effects so far

| Effect | What you notice |
| --- | --- |
| `footstep_echo` | Footsteps a few blocks behind you, keeping pace with yours. They stop when you stop. |
| `phantom_container` | A chest, barrel, door, trapdoor or gate you can't see opens and closes. |
| `distant_mining` | Underground: someone mining through the rock nearby. |
| `left_open` | A door, trapdoor or gate you left closed at home is open when you get back. Daytime only. |
| `torch_out` | A torch at the edge of your home is gone, lying on the ground below where it was. |
| `someone_elses_work` | Far from home: a lone torch, a dirt pillar, or underground a short 1x2 tunnel you didn't make. |
| `staring_animal` | A nearby animal stops and follows you with its head until you leave. |
| `the_watcher` | A figure stands at the edge of your view, 20 to 40 blocks away. Look straight at it, get close, or look away for long and it's gone. |
| `behind_you` | The figure stands right behind you for a few moments. Only a quick turn catches it. |
| `breathing` | In tight, dark places: slow breathing just behind your head. |
| `the_owner` | Near the other base, while the other you is in the game: the figure stands where its routine has it (by the door, at its trees, at its mine), at the edge of your view. |
| `torches_out` | At night or underground: the torches around you are broken one by one, furthest first, coming closer. Only ones you aren't looking at. |
| `running_feet` | In the dark or at night: someone sprints at you from behind and stops right at your back. Turning to face it stops it dead. |
| `the_other_base` | Once per world, out of sight and far from your home, on flat grass or sand that no player has built on or spent much time near, someone else starts a base: a cleared spot, a crafting table, a chest and a campfire whose smoke gives it away. It's built from what's around it: the wood of the nearest trees, sandstone instead of cobblestone on sand, and a pen of the animals grazing nearby instead of a wheat farm (always a pen in snowy places, where wheat wouldn't grow). Then it gets built, one stage per in-game day, while nobody is within 40 blocks or looking (even if you're far away): log corner posts on a course of stone, then plank walls and a bed, then a log beam along the tops of the walls, then a pitched roof of stairs hanging over the walls (in two stages, finished with a log ridge), then the door, glass windows with shutters, and torches, then a furnace with food, lanterns and a rug, then the farm or pen, then fences, torch posts and a path, then flowers and a lamp post, then a lean-to on the side, then (once it has iron tools) an unlit portal frame. It only builds while the other you is in the game (see below). If you're nearby you may hear the building. The base remembers how its surroundings looked when it was started and after each stage, so breaking a block and putting the same block back goes unnoticed; any other block you place on the surface within 20 blocks of it is taken down the next time nobody is around, and put away in its chests (with anything stored in them); when those fill up, more chests appear inside and then out the back. Anything broken at the base is put back the same way, from the ground up, but only with blocks it has in its chests (it crafts planks from logs, and stairs, slabs and trapdoors from planks, when it needs to; crops are replanted from seed, grass comes back as dirt). Nobody is ever home. |

Tier 1 (the first three) can happen any time. Tier 2 needs unease of 25 or more, tier 3 (the figure and breathing) 50 or more, and tier 4 (the last three) 75 or more. World changes only ever touch doors, torches, natural stone and open air, and never happen where you can see them.

Big moments are kept rare and quiet:

- Before `torches_out` and `running_feet`, the game goes silent. Music, ambience and weather stop about a minute before, and stay away for another minute after. Your own sounds and the world's still play.
- After any tier 3 or 4 event, nothing else happens for 10 to 20 minutes, and unease drops (by 30 after tier 3, by 45 after tier 4).
- Tier 4 happens at most once per play session.

## The other you

Once the other base is started, the other you has a life of its own:

- **Sessions.** It's in the game for 60 to 90 minutes at a time, then away for 20 to 60. It does nothing while it's away: no building, tidying, repairs or routine. Now and then (25 to 50 percent of the time) the chat shows "You joined the game", and "You left the game" when that session ends.
- **Routine.** Mornings it gathers wood, afternoons it mines, at dusk it cooks, at night it sleeps. Its door stands open in the morning and its furnace is lit at dusk.
- **Traces.** A dirt path worn from its door out to where it cuts wood. Trees near there cut down to stumps, one a morning, the logs in its chests. A staircase mine beside the cabin, a step deeper every so often in the afternoon, with torches, its ore and stone in its chests; it stops before water or lava. Every few days, a lone pillar it climbed back down from. You can hear the chopping and digging if you're close.
- **Progress.** It smelts what it mines, makes stone tools and then an iron pickaxe, and dresses an armor stand in leather and later an iron chestplate. The base gets a lean-to, and once it has iron tools, a portal frame it never lights.
- **Mirroring you.** Plant crops at home and it plants a wheat field of its own. Die and a copy of something you carried turns up in its chest. Its journal knows when you slept.
- **Your things.** Once in a while, while you're away from home, part of a stack goes missing from a chest there and shows up in its chests, and something of its own (bread, a torch) is left in its place.
- **The journal.** A book and quill in its chest gets a page every day it's in the game, about what *you* did: what you mined, what you killed, how far you walked, whether you died or slept. Take it and a new copy appears.

## The figure

The figure is another version of you, overlapping into your world for a moment. It wears your own skin on your own model (classic or slim), never has a name tag, and is only drawn on your screen; it doesn't exist on the server and nobody else can see it. In multiplayer, each player sees their own double.

## Custom sounds

Custom sounds live in `src/main/resources/assets/peripheral/sounds/`:

| File | Used by |
| --- | --- |
| `breathing.ogg` | `breathing` (mono, so it plays from a position) |

## Playtesting

Operator commands:

- `/peripheral debug` toggles an on-screen readout of your unease, the countdown to the next event, and how far and in which direction the last event was. While it's on, every event also posts a chat line with its coordinates (click to teleport) and marks the spot with a column of particles for 30 seconds. Footstep echoes mark each step with smoke.
- `/peripheral unease [player]` and `/peripheral unease set <0-100> [player]`
- `/peripheral trigger <effect> [player]` fires an effect immediately, ignoring grace period, tier and cooldown, and always reports where it happened. Effects that go quiet first wait 5 seconds instead of a minute.
- `/peripheral otherbase` shows where the other base is (click to teleport) and how far along it is. `/peripheral otherbase grow` builds its next stage right away, even while you watch. `/peripheral otherbase reset` forgets it (the blocks stay) so a new one can be started.
- `/peripheral otherbase` also says whether the other you is in the game, what it's doing, and how far its mine, path, tools and journal have got. `/peripheral otherbase join` and `/peripheral otherbase leave` bring it in or out of the game right away.
- `/peripheral reload` reloads `config/peripheral.json`.

Config (`config/peripheral.json`):

| Key | Default | Meaning |
| --- | --- | --- |
| `preset` | `"default"` | `"subtle"` (tier 1 and 2 only, slower), `"default"`, or `"relentless"` (faster) |
| `allowWorldChanges` | `true` | Whether effects may open doors, take torches, build or dig |
| `allowFigure` | `true` | Whether the figure can appear |
| `gracePeriodDays` | `2.0` | In-game days of world age before anything happens |
| `uneaseRiseMultiplier` | `1.0` | How fast unease rises, on top of the preset |
| `eventIntervalMultiplier` | `1.0` | Time between events, on top of the preset; lower is more often |
| `otherBaseDaysPerStage` | `1.0` | In-game days of play between each stage of the other base |
| `otherPlayerJoinMessages` | `true` | Whether the other you sometimes shows "You joined the game" and "You left the game" |
| `affectCreativePlayers` | `false` | Whether creative players are haunted |
| `effects` | `{}` | Per-effect switches, e.g. `{"distant_mining": false}` |

The mod must be installed on both the server and the client.

## License

CC0. See [LICENSE](LICENSE).
