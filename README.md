# Auto Trade Reroller

Client-side villager trade rerolling for Fabric. One command cycles a villager's job site
block and prints the trades it rolled, either pausing after every roll or running until it
finds what you asked for.

## Usage

```
/atr start <block>
/atr start <block> want <enchantment|item> [level] [max <emeralds>]
/atr stop
```

`<block>` tab-completes to the villager job site blocks (`lectern`, `cartography_table`,
`grindstone`, ...); any other block id is accepted too, but only job sites actually reroll
anything.

Setup before starting:

1. Put the job site block down **one block in front of you** (or simply look right at it),
   close enough that you can pick the drop back up.
2. Stand so the villager you want is **in front of you** - the crosshair picks it, so it
   stays unambiguous even in a crowd.
3. Keep a spare copy of the block **in your inventory**; it is what gets placed back.

### Manual mode

`/atr start lectern` breaks the lectern, puts it back on the exact same position,
right-clicks the villager, prints its first two trades, and stops there:

```
[ATR] Reroll #7 - Librarian (level 1)
  1) 24x Paper → 1x Emerald
  2) 20x Emerald + 1x Book → 1x Enchanted Book (Mending)
[ATR] Press F4 to roll again, or /atr stop to keep these.
```

**F4** rolls again. **`/atr stop`** ends the loop and keeps whatever is on the villager
right now. Nothing happens on its own, so leaving it sitting at the prompt is safe. F4 is
rebindable under Options - Controls - Gameplay.

### Wish mode

Name what you are after and the loop runs on its own until it shows up, then stops:

```
/atr start lectern want efficiency 5
/atr start lectern want efficiency 5 max 20
/atr start lectern want mending
/atr start lectern want diamond_pickaxe max 30
```

The wish argument takes an enchantment id or an item id, both tab-completed. A level means
"that level or better" and only applies to enchantments. `max <emeralds>` caps what the
trade may cost in emeralds; leave it out and any price counts.

While hunting, each roll is one short line and F4 is not involved. On a hit you get the
full trade list and the loop stops:

```
[ATR] Reroll #22 - no match.
[ATR] Match after 23 rerolls! Librarian (level 1), stopped here.
  1) 19x Emerald + 1x Book → 1x Enchanted Book (Efficiency V)
  2) 24x Paper → 1x Emerald
```

The wish is checked against every trade the villager offers, not just the two that manual
mode prints.

## Picking the block back up

The dropped block is looked for in your whole inventory, and pulled back into the hotbar if
it landed in the backpack.

Minecraft only hands you an item that touches your hitbox plus one block, so a drop can
easily land just out of reach. When that happens the loop walks your character over to the
item, picks it up, and walks back to the exact spot you were standing on before carrying
on. It says so once, the first time it has to do it.

The walk is bounded: it gives up if the item ended up more than three blocks above or below
you, or if it would have to leave a ten block radius around the job site block. `/atr start`
also warns up front when you are standing far enough away that drops will regularly miss
you - standing closer avoids the detour entirely.

## Tools

Before each break the mod switches to the fastest tool for that block anywhere in your
inventory - Efficiency included - and pulls it into the hotbar if it was in the backpack.
Tools that would break the block without dropping it are never chosen. Your original
hotbar slot is restored when the loop ends.

## Notes

- The trade screen flashes open for a moment each cycle. That is how the client learns the
  offers at all; they are not sent until the merchant screen opens.
- Rerolling only works while the villager has never been traded with. Once you trade, its
  offers are locked and breaking the job site block no longer changes them.
- Placing aims at the block below the job site position first. If that neighbour is
  something right-clickable (a chest, a door), the placement click will open it instead -
  put the job site block on plain ground.
- The loop stops on its own, with the reason in chat, when the villager wanders off, when
  you walk away from the job site block, or when a step takes too long. Those checks are
  paused while it waits for an F4.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
