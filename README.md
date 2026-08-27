# Auto Trade Reroller

Client-side villager trade rerolling for Fabric. One command cycles a villager's job site
block and prints the trades it rolled, then waits for you to decide whether to roll again.

## Usage

```
/atr start <block>
/atr stop
```

`<block>` tab-completes to the villager job site blocks (`lectern`, `cartography_table`,
`grindstone`, ...); any other block id is accepted too, but only job sites actually reroll
anything.

Setup before starting:

1. Put the job site block down **one block in front of you** (or simply look right at it).
2. Stand so the villager you want is **in front of you** - the crosshair picks it, so it
   stays unambiguous even in a crowd.
3. Keep a spare copy of the block **in your hotbar**; it is what gets placed back.

`/atr start lectern` then breaks the lectern, puts it back on the exact same position,
right-clicks the villager, prints its first two trades, and stops there:

```
[ATR] Reroll #7 - Librarian (level 1)
  1) 24x Paper → 1x Emerald
  2) 20x Emerald + 1x Book → 1x Enchanted Book (Mending)
[ATR] Press F4 to roll again, or /atr stop to keep these.
```

**F4** rolls again. **`/atr stop`** ends the loop and keeps whatever is on the villager
right now. Nothing happens on its own, so leaving it sitting at the prompt is safe.

F4 is rebindable under Options - Controls - Gameplay.

The loop also stops on its own, with the reason in chat, when the villager wanders off,
when you walk away from the job site block, when the block cannot be placed back, or when
a step takes too long. Those checks are paused while it waits for your answer, so you can
walk around and look at the trades in peace.

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

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
