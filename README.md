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
| `torches_out` | At night or underground: the torches around you are broken one by one, furthest first, coming closer. Only ones you aren't looking at. |
| `running_feet` | In the dark or at night: someone sprints at you from behind and stops right at your back. Turning to face it stops it dead. |
| `the_other_base` | Once per world, out of sight and far from your home, on flat grass or sand that no player has built on or spent much time near, someone else starts a base: a cleared spot, a crafting table, a chest and a campfire whose smoke gives it away. It's built from what's around it: the wood of the nearest trees, sandstone instead of cobblestone on sand, and a pen of the animals grazing nearby instead of a wheat farm. Then it gets built, one stage per in-game day, while nobody is within 40 blocks or looking (even if you're far away): walls and a bed, then a roof, door and torches, then a furnace with food and the farm or pen, then fences, torch posts and a path. If you're nearby you may hear the building. The base remembers how its surroundings looked when it was started and after each stage, so breaking a block and putting the same block back goes unnoticed; any other block you place on the surface within 20 blocks of it is taken down the next time nobody is around, and put away in its chests (with anything stored in them); when those fill up, more chests appear inside and then out the back. Nobody is ever home. |

Tier 1 (the first three) can happen any time. Tier 2 needs unease of 25 or more, tier 3 (the figure and breathing) 50 or more, and tier 4 (the last three) 75 or more. World changes only ever touch doors, torches, natural stone and open air, and never happen where you can see them.

Big moments are kept rare and quiet:

- Before `torches_out` and `running_feet`, the game goes silent. Music, ambience and weather stop about a minute before, and stay away for another minute after. Your own sounds and the world's still play.
- After any tier 3 or 4 event, nothing else happens for 10 to 20 minutes, and unease drops (by 30 after tier 3, by 45 after tier 4).
- Tier 4 happens at most once per play session.

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
| `affectCreativePlayers` | `false` | Whether creative players are haunted |
| `effects` | `{}` | Per-effect switches, e.g. `{"distant_mining": false}` |

The mod must be installed on both the server and the client.

## License

CC0. See [LICENSE](LICENSE).
